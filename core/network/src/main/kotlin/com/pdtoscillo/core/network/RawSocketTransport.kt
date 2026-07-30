package com.pdtoscillo.core.network

import com.pdtoscillo.core.common.PdtLog
import com.pdtoscillo.core.model.ConnectionConfig
import com.pdtoscillo.core.model.ConnectionState
import com.pdtoscillo.core.model.InstrumentTransport
import com.pdtoscillo.core.model.ScopeError
import com.pdtoscillo.core.model.TimeoutAwareTransport
import com.pdtoscillo.core.model.TransportRouteInfo
import com.pdtoscillo.core.scpi.ScpiBinaryBlockReader
import com.pdtoscillo.core.scpi.ScpiByteSource
import com.pdtoscillo.core.scpi.ScpiException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okio.BufferedSink
import okio.BufferedSource
import okio.buffer
import okio.sink
import okio.source
import java.io.EOFException
import java.io.IOException
import java.net.ConnectException
import java.net.InetSocketAddress
import java.net.NoRouteToHostException
import java.net.PortUnreachableException
import java.net.Socket
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/**
 * Tektronix Socket Server（既定ポート 4000 / Protocol None）へ TCP で接続する Transport。
 *
 * 実装上の要点:
 * - ソケットは [SocketProvider] 経由で作る。Ethernet へのバインドはそこに閉じ込める。
 * - 接続後に [RouteVerifier] で経路を検証し、モバイル回線への誤接続を検出する。
 * - **1 回の read で応答が揃うと仮定しない。** バイナリは [ScpiBinaryBlockReader] が読み切る。
 * - ブロッキング読み取りはコルーチンのキャンセルでは止まらないため、
 *   [applyOperationTimeout] でソケット自身に締め切りを与える。
 * - ソケットへの同時アクセスを内部の [Mutex] でも防ぐ。上位のキューと二重の保護になる。
 */
class RawSocketTransport(private val socketProvider: SocketProvider, private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO) :
    InstrumentTransport,
    TimeoutAwareTransport {

    private val _state = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    override val state: StateFlow<ConnectionState> = _state.asStateFlow()

    private val _routeInfo = MutableStateFlow<TransportRouteInfo?>(null)
    override val routeInfo: StateFlow<TransportRouteInfo?> = _routeInfo.asStateFlow()

    private val ioMutex = Mutex()

    private var socket: Socket? = null
    private var source: BufferedSource? = null
    private var sink: BufferedSink? = null
    private var config: ConnectionConfig? = null

    /** 押し戻された 1 バイト。ブロック末尾の終端判定で使う。 */
    private val pushback = ArrayDeque<Int>()

    override fun applyOperationTimeout(millis: Long) {
        val clamped = millis.coerceIn(MIN_SOCKET_TIMEOUT_MILLIS, Int.MAX_VALUE.toLong()).toInt()
        runCatching { socket?.soTimeout = clamped }
    }

    override suspend fun connect(config: ConnectionConfig) {
        ioMutex.withLock {
            closeQuietly()
            this.config = config
            _state.value = ConnectionState.Connecting(config)

            withContext(ioDispatcher) {
                val provided = try {
                    socketProvider.createSocket(config.bindStrategy)
                } catch (exception: ScpiException) {
                    _state.value = ConnectionState.Failed(config, exception.error)
                    throw exception
                }

                val target = provided.socket
                try {
                    target.keepAlive = config.keepAlive
                    target.tcpNoDelay = config.tcpNoDelay
                    target.connect(
                        InetSocketAddress(config.host, config.port),
                        config.connectTimeoutMillis.toInt(),
                    )
                    target.soTimeout = config.readTimeoutMillis.toInt()
                } catch (error: Exception) {
                    runCatching { target.close() }
                    val mapped = mapConnectError(error, config)
                    _state.value = ConnectionState.Failed(config, mapped)
                    throw ScpiException(mapped)
                }

                socket = target
                source = target.source().buffer()
                sink = target.sink().buffer()
                pushback.clear()

                val route = RouteVerifier.verify(
                    socket = target,
                    requestedStrategy = config.bindStrategy,
                    ethernetLink = provided.ethernetLink ?: socketProvider.ethernetLink(),
                )
                _routeInfo.value = route
                route.warning?.let { PdtLog.w(TAG, it) }

                // 接続直後に届く挨拶文を捨てる。
                // Socket Server が Terminal プロトコルの場合、接続した時点でバナーとプロンプトが
                // 送られてくる。これを残すと最初の Query の応答として読まれてしまう。
                // Protocol None では何も届かないため、この処理は無害である。
                val greeting = drainLocked(target, GREETING_DRAIN_TIMEOUT_MILLIS)
                if (greeting > 0) {
                    PdtLog.w(
                        TAG,
                        "接続直後に $greeting バイトの未要求データを受信しました。" +
                            "Socket Server の Protocol が Terminal になっている可能性があります。",
                    )
                }

                _state.value = ConnectionState.Connected(
                    config = config,
                    localAddress = route.localAddress,
                    remoteAddress = route.remoteAddress,
                    connectedAtEpochMillis = System.currentTimeMillis(),
                )
                PdtLog.i(TAG, "接続しました: ${config.host}:${config.port} (${route.localAddress})")
            }
        }
    }

    override suspend fun disconnect() {
        ioMutex.withLock {
            closeQuietly()
            _state.value = ConnectionState.Disconnected
            _routeInfo.value = null
        }
    }

    /**
     * 切断されていれば再接続する。
     *
     * ケーブルが抜けている状態で無限に試し続けないよう、試行回数に上限を設ける。
     */
    suspend fun reconnectIfNeeded(): Boolean {
        val target = config ?: return false
        if (_state.value.isConnected && socket?.isConnected == true && socket?.isClosed == false) return true
        if (!target.autoReconnect) return false

        for (attempt in 1..target.maxReconnectAttempts) {
            _state.value = ConnectionState.Reconnecting(
                config = target,
                attempt = attempt,
                maxAttempts = target.maxReconnectAttempts,
                cause = (_state.value as? ConnectionState.Failed)?.error,
            )
            PdtLog.i(TAG, "再接続を試行します ($attempt/${target.maxReconnectAttempts})")
            val succeeded = runCatching { connect(target) }.isSuccess
            if (succeeded) return true
            if (attempt < target.maxReconnectAttempts) delay(target.reconnectDelayMillis)
        }
        PdtLog.w(TAG, "再接続の試行回数が上限に達しました。ケーブルと設定を確認してください。")
        return false
    }

    override suspend fun write(command: ByteArray) {
        withIo("write") {
            val target = requireSink()
            target.write(command)
            target.write(requireConfig().terminator.bytes)
            target.flush()
        }
    }

    override suspend fun readText(): String = withIo("readText") {
        readLineInternal()
    }

    override suspend fun readBinary(): ByteArray = withIo("readBinary") {
        ScpiBinaryBlockReader.read(byteSource, requireConfig().maxBinaryResponseBytes)
    }

    override suspend fun queryText(command: String): String = withIo("queryText") {
        writeInternal(command)
        readLineInternal()
    }

    override suspend fun queryBinary(command: String): ByteArray = withIo("queryBinary") {
        writeInternal(command)
        ScpiBinaryBlockReader.read(byteSource, requireConfig().maxBinaryResponseBytes)
    }

    /**
     * 受信バッファに残っているものを捨てる。
     *
     * タイムアウト後に遅れて届いた応答が、次のコマンドの応答として読まれるのを防ぐ。
     * 短い締め切りを設定して読めるだけ読み、読めなくなったら終わる。
     */
    override suspend fun discardPendingInput(): Int = ioMutex.withLock {
        val target = socket ?: return@withLock 0
        withContext(ioDispatcher) { drainLocked(target, DISCARD_TIMEOUT_MILLIS) }
    }

    /**
     * 受信済みバイトを捨てる。[ioMutex] を保持した状態で呼ぶこと。
     *
     * 短い締め切りを設定して読めるだけ読み、読めなくなったら終わる。
     */
    private fun drainLocked(target: Socket, timeoutMillis: Int): Int {
        val reader = source ?: return 0
        val originalTimeout = runCatching { target.soTimeout }.getOrDefault(0)
        var discarded = pushback.size
        pushback.clear()
        runCatching {
            target.soTimeout = timeoutMillis
            val buffer = ByteArray(DISCARD_BUFFER_SIZE)
            while (discarded < MAX_DISCARD_BYTES) {
                val read = reader.read(buffer, 0, buffer.size)
                if (read <= 0) break
                discarded += read
            }
        }
        runCatching { target.soTimeout = originalTimeout }
        return discarded
    }

    private suspend fun <T> withIo(operation: String, block: suspend () -> T): T = ioMutex.withLock {
        withContext(ioDispatcher) {
            try {
                block()
            } catch (error: ScpiException) {
                throw error
            } catch (error: SocketTimeoutException) {
                throw ScpiException(
                    ScopeError.ReadTimeout(operation, config?.readTimeoutMillis ?: 0),
                )
            } catch (error: EOFException) {
                markDisconnected(error)
                throw ScpiException(ScopeError.Disconnected("応答の途中で接続が切れました ($operation)"))
            } catch (error: IOException) {
                markDisconnected(error)
                throw ScpiException(ScopeError.Disconnected(error.message ?: operation))
            }
        }
    }

    private fun writeInternal(command: String) {
        val target = requireSink()
        target.writeString(command, Charsets.US_ASCII)
        target.write(requireConfig().terminator.bytes)
        target.flush()
    }

    /**
     * 終端まで 1 行読む。
     *
     * `readUtf8LineStrict` を使うのが重要。終端が来ないまま接続が切れた場合、
     * 途中まで届いたバイト列を「完全な応答」として返してはならない。
     * それを許すと、切り詰められた値が正常値として静かに使われ続ける。
     *
     * 前の応答の残りで空行が続く場合があるため、内容のある行が来るまで限られた回数だけ読み進める。
     */
    private fun readLineInternal(): String {
        val reader = requireSource()
        repeat(MAX_EMPTY_LINES) {
            val prefix = drainPushbackAsText()
            // 終端が無いまま EOF / 上限に達したら EOFException。不完全な応答を成功にしない。
            val line = reader.readUtf8LineStrict(MAX_LINE_BYTES)
            val combined = (prefix + line).trim()
            if (combined.isNotEmpty()) return combined
        }
        return ""
    }

    /** 押し戻されたバイトが残っている場合、それを行の先頭として扱う。 */
    private fun drainPushbackAsText(): String {
        if (pushback.isEmpty()) return ""
        val builder = StringBuilder()
        while (pushback.isNotEmpty()) {
            val value = pushback.removeFirst()
            if (value == LINE_FEED) return builder.toString()
            if (value != CARRIAGE_RETURN) builder.append(value.toChar())
        }
        return builder.toString()
    }

    /** [ScpiBinaryBlockReader] へ渡すバイト読み出し口。 */
    private val byteSource = object : ScpiByteSource {
        override suspend fun readByte(): Int {
            pushback.removeFirstOrNull()?.let { return it }
            val reader = requireSource()
            return if (reader.exhausted()) -1 else reader.readByte().toInt() and BYTE_MASK
        }

        override suspend fun readFully(target: ByteArray, offset: Int, length: Int) {
            var written = 0
            while (written < length && pushback.isNotEmpty()) {
                target[offset + written] = pushback.removeFirst().toByte()
                written++
            }
            val reader = requireSource()
            while (written < length) {
                // TCP は要求したバイト数を 1 回で返さない。読み切るまで繰り返す。
                val read = reader.read(target, offset + written, length - written)
                if (read < 0) {
                    throw ScpiException(
                        ScopeError.MalformedBinaryBlock(
                            "データが不足したまま接続が閉じられました: 要求 $length バイト、取得 $written バイト",
                        ),
                    )
                }
                written += read
            }
        }

        override suspend fun unreadByte(value: Int) {
            pushback.addFirst(value)
        }
    }

    private fun markDisconnected(cause: Throwable) {
        PdtLog.w(TAG, "接続が失われました", cause)
        _state.value = ConnectionState.Failed(config, ScopeError.Disconnected(cause.message))
        closeQuietly()
    }

    private fun closeQuietly() {
        runCatching { sink?.flush() }
        runCatching { sink?.close() }
        runCatching { source?.close() }
        runCatching { socket?.close() }
        sink = null
        source = null
        socket = null
        pushback.clear()
    }

    private fun requireConfig(): ConnectionConfig = config ?: throw ScpiException(ScopeError.Disconnected("接続していません"))

    private fun requireSink(): BufferedSink = sink ?: throw ScpiException(ScopeError.Disconnected("接続していません"))

    private fun requireSource(): BufferedSource = source ?: throw ScpiException(ScopeError.Disconnected("接続していません"))

    private fun mapConnectError(error: Throwable, config: ConnectionConfig): ScopeError = when (error) {
        is SocketTimeoutException -> ScopeError.ConnectTimeout(config.host, config.port, config.connectTimeoutMillis)
        is ConnectException -> ScopeError.ConnectionRefused(config.host, config.port, error)
        is PortUnreachableException -> ScopeError.ConnectionRefused(config.host, config.port, error)
        is NoRouteToHostException -> ScopeError.Unreachable(config.host, error)
        is UnknownHostException -> ScopeError.Unreachable(config.host, error)
        is IOException -> ScopeError.Unreachable(config.host, error)
        else -> ScopeError.Unknown(error.message, error)
    }

    private companion object {
        const val TAG = "RawSocketTransport"
        const val BYTE_MASK = 0xFF
        const val LINE_FEED = 0x0A
        const val CARRIAGE_RETURN = 0x0D
        const val MIN_SOCKET_TIMEOUT_MILLIS = 200L
        const val DISCARD_TIMEOUT_MILLIS = 150
        const val GREETING_DRAIN_TIMEOUT_MILLIS = 120
        const val DISCARD_BUFFER_SIZE = 4096
        const val MAX_DISCARD_BYTES = 8 * 1024 * 1024
        const val MAX_EMPTY_LINES = 4

        /** 1 行の上限。これを超えて終端が来ない応答は異常として扱う。 */
        const val MAX_LINE_BYTES = 1L * 1024 * 1024
    }
}
