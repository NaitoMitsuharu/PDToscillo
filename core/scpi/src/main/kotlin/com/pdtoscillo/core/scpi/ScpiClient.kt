package com.pdtoscillo.core.scpi

import com.pdtoscillo.core.common.CommunicationLogRecorder
import com.pdtoscillo.core.common.PdtLog
import com.pdtoscillo.core.model.ConnectionConfig
import com.pdtoscillo.core.model.ConnectionState
import com.pdtoscillo.core.model.InstrumentCapabilities
import com.pdtoscillo.core.model.InstrumentIdentity
import com.pdtoscillo.core.model.InstrumentTransport
import com.pdtoscillo.core.model.ScopeError
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * SCPI 層の入口。
 *
 * feature 層はこのクラスだけを見る。ソケットの詳細も、コマンド文字列の組み立ても、
 * 直列化も、読み取り専用モードの強制もここより下で完結させる。
 */
class ScpiClient(private val transport: InstrumentTransport, val logRecorder: CommunicationLogRecorder = CommunicationLogRecorder()) {
    private var currentConfig: ConnectionConfig? = null

    val queue: ScpiCommandQueue = ScpiCommandQueue(transport, logRecorder) { currentConfig }
    val errorQueue: ScpiErrorQueue = ScpiErrorQueue(queue)
    private val capabilityDetector = TektronixCapabilityDetector(queue, errorQueue)

    val connectionState: StateFlow<ConnectionState> = transport.state
    val routeInfo = transport.routeInfo
    val readOnlyMode: StateFlow<Boolean> = queue.readOnlyMode
    val pendingCount: StateFlow<Int> = queue.pendingCount

    private val _identity = MutableStateFlow<InstrumentIdentity?>(null)
    val identity: StateFlow<InstrumentIdentity?> = _identity.asStateFlow()

    private val _capabilities = MutableStateFlow<InstrumentCapabilities?>(null)
    val capabilities: StateFlow<InstrumentCapabilities?> = _capabilities.asStateFlow()

    private val _lastResponseMillis = MutableStateFlow<Long?>(null)

    /** 直近の往復時間。UI の通信遅延表示に使う。 */
    val lastResponseMillis: StateFlow<Long?> = _lastResponseMillis.asStateFlow()

    suspend fun connect(config: ConnectionConfig) {
        currentConfig = config
        // 接続直後は必ず読み取り専用に戻す。前の接続で解除していても引き継がない。
        queue.setReadOnlyMode(true)
        _identity.value = null
        _capabilities.value = null
        transport.connect(config)
    }

    suspend fun disconnect() {
        transport.disconnect()
        _identity.value = null
        _capabilities.value = null
    }

    fun setReadOnlyMode(enabled: Boolean) = queue.setReadOnlyMode(enabled)

    /** `*IDN?` で機器を識別する。 */
    suspend fun identify(): InstrumentIdentity {
        val started = System.currentTimeMillis()
        val response = queue.query(ScpiCommand.Query(TektronixCommands.Common.IDENTIFY))
        _lastResponseMillis.value = System.currentTimeMillis() - started

        if (ScpiResponseParser.looksLikeTerminalArtifact(response, TektronixCommands.Common.IDENTIFY)) {
            throw ScpiException(
                ScopeError.MalformedResponse(
                    TektronixCommands.Common.IDENTIFY,
                    "コマンドがエコーされています。オシロスコープの Socket Server の Protocol を " +
                        "None に設定してください（現在 Terminal の可能性があります）。応答: $response",
                ),
            )
        }

        val parsed = IdnParser.parse(response)
        _identity.value = parsed
        PdtLog.i(TAG, "識別: ${parsed.manufacturer} / ${parsed.model} / ${parsed.firmwareVersion}")
        return parsed
    }

    /** 対応機能を検出する。破壊的な設定変更は行わない。 */
    suspend fun detectCapabilities(onProgress: (TektronixCapabilityDetector.Progress) -> Unit = {}): InstrumentCapabilities {
        val identity = _identity.value ?: identify()
        val detected = capabilityDetector.detect(identity, onProgress)
        _capabilities.value = detected
        return detected
    }

    suspend fun queryText(command: String, timeoutMillis: Long? = null): String {
        val started = System.currentTimeMillis()
        val response = queue.query(ScpiCommand.Query(command, timeoutMillis))
        _lastResponseMillis.value = System.currentTimeMillis() - started
        return response
    }

    suspend fun queryBinary(command: String, timeoutMillis: Long? = null): ByteArray {
        val started = System.currentTimeMillis()
        val response = queue.queryBinary(ScpiCommand.BinaryQuery(command, timeoutMillis))
        _lastResponseMillis.value = System.currentTimeMillis() - started
        return response
    }

    suspend fun queryDouble(command: String): Double? = ScpiResponseParser.parseDouble(queryText(command))

    suspend fun queryLong(command: String): Long? = ScpiResponseParser.parseLong(queryText(command))

    suspend fun queryInt(command: String): Int? = ScpiResponseParser.parseInt(queryText(command))

    suspend fun queryBoolean(command: String): Boolean? = ScpiResponseParser.parseBoolean(queryText(command))

    /** 値だけを取り出す（ヘッダと引用符を除去）。 */
    suspend fun queryValue(command: String): String = ScpiResponseParser.unquote(ScpiResponseParser.stripHeader(queryText(command)))

    suspend fun write(command: String, timeoutMillis: Long? = null) {
        queue.write(ScpiCommand.Write(command, timeoutMillis))
    }

    suspend fun writeAll(commands: List<String>) {
        commands.forEach { write(it) }
    }

    /**
     * 設定を適用し、実際に受理された値を読み戻す。
     *
     * 計測器は要求値をそのまま受け付けるとは限らない（離散値へ丸める、範囲へ収める）。
     * 「送った値」ではなく「本体が受理した値」を UI へ表示するために必ず読み戻す。
     */
    suspend fun <T> applyAndVerify(setCommand: String, value: String, readBackQuery: String, parser: (String) -> T?): ApplyResult<T> {
        val before = runCatching { parser(queryText(readBackQuery)) }.getOrNull()
        return try {
            write("$setCommand $value")
            val error = errorQueue.classifyLatest(setCommand)
            if (error != null) {
                ApplyResult.Rejected(before, error)
            } else {
                val after = parser(queryText(readBackQuery))
                ApplyResult.Applied(before, after)
            }
        } catch (exception: ScpiException) {
            ApplyResult.Rejected(before, exception.error)
        }
    }

    sealed interface ApplyResult<out T> {
        /** 設定が通った。[accepted] は本体が実際に受理した値。 */
        data class Applied<T>(val previous: T?, val accepted: T?) : ApplyResult<T>

        /** 設定が拒否された。[previous] は変更前の値。 */
        data class Rejected<T>(val previous: T?, val error: ScopeError) : ApplyResult<T>
    }

    /** 実行完了を待つ。`*OPC?` は処理が終わると 1 を返す。 */
    suspend fun waitForOperationComplete(timeoutMillis: Long): Boolean {
        val response = runCatching {
            queue.query(ScpiCommand.Query(TektronixCommands.Common.OPERATION_COMPLETE_QUERY, timeoutMillis))
        }.getOrNull() ?: return false
        return ScpiResponseParser.parseBoolean(response) == true
    }

    suspend fun isBusy(): Boolean? = runCatching { queryBoolean(TektronixCommands.Common.BUSY) }.getOrNull()

    private companion object {
        const val TAG = "ScpiClient"
    }
}
