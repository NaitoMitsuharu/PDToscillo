package com.pdtoscillo.simulator

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.util.Collections
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * 疑似オシロスコープ TCP サーバー。
 *
 * 実機なしで開発とテストを進めるために用意する。統合テストからは `port = 0` で起動し、
 * [boundPort] で実際のポートを取得する。
 */
class ScopeSimulator(private val config: SimulatorConfig = SimulatorConfig()) : AutoCloseable {
    private val running = AtomicBoolean(false)
    private val connectionCount = AtomicInteger(0)
    private var serverSocket: ServerSocket? = null
    private val executor = Executors.newCachedThreadPool { runnable ->
        Thread(runnable, "scope-simulator").apply { isDaemon = true }
    }
    private val clients = Collections.synchronizedSet(mutableSetOf<Socket>())

    val boundPort: Int
        get() = serverSocket?.localPort ?: -1

    /** これまでに受け付けた接続の総数。再接続テストで使う。 */
    val acceptedConnections: Int
        get() = connectionCount.get()

    /** 起動して実際にバインドされたポートを返す。 */
    fun start(): Int {
        check(running.compareAndSet(false, true)) { "already started" }
        val socket = ServerSocket()
        socket.reuseAddress = true
        socket.bind(InetSocketAddress("127.0.0.1", config.port))
        serverSocket = socket
        log("listening on port ${socket.localPort} model=${config.model.idnModel} fault=${config.faultMode}")

        executor.execute {
            while (running.get()) {
                val client = try {
                    socket.accept()
                } catch (_: IOException) {
                    break
                }
                connectionCount.incrementAndGet()
                clients += client
                executor.execute { serveClient(client) }
            }
        }
        return socket.localPort
    }

    override fun close() {
        if (!running.compareAndSet(true, false)) return
        synchronized(clients) {
            clients.forEach { runCatching { it.close() } }
            clients.clear()
        }
        runCatching { serverSocket?.close() }
        executor.shutdownNow()
    }

    private fun serveClient(client: Socket) {
        // 1 接続 1 インスタンス。実機の 1 台に相当する状態を接続ごとに持つ。
        val instrument = SimulatedInstrument(config)
        try {
            client.tcpNoDelay = true
            val input = client.getInputStream()
            val output = client.getOutputStream()

            if (config.terminalMode) {
                writeAll(output, TERMINAL_BANNER.toByteArray(Charsets.US_ASCII))
            }

            while (running.get() && !client.isClosed) {
                val line = readLine(input) ?: break
                log("<- $line")

                if (config.terminalMode) {
                    // Terminal プロトコルはコマンドをエコーする。受信側がこれを応答と誤認しないかを試す。
                    writeAll(output, (line + "\r\n").toByteArray(Charsets.US_ASCII))
                }

                when (config.faultMode) {
                    FaultMode.NO_RESPONSE -> continue
                    FaultMode.DELAYED_RESPONSE -> Thread.sleep(config.responseDelayMillis)
                    else -> Unit
                }

                when (val response = instrument.handle(line)) {
                    SimulatedResponse.None -> Unit
                    is SimulatedResponse.Text -> sendPayload(output, textPayload(response.value))
                    is SimulatedResponse.Binary -> sendPayload(output, response.value)
                }

                if (config.terminalMode) {
                    writeAll(output, TERMINAL_PROMPT.toByteArray(Charsets.US_ASCII))
                }
            }
        } catch (_: SocketException) {
            // 切断は正常系として扱う。
        } catch (error: IOException) {
            log("client error: ${error.message}")
        } finally {
            clients -= client
            runCatching { client.close() }
            log("client closed")
        }
    }

    private fun textPayload(text: String): ByteArray = (text + "\n").toByteArray(Charsets.US_ASCII)

    /**
     * 応答の送出。障害モードに応じて分割・途中切断を再現する。
     *
     * `SPLIT_RESPONSE` は TCP のパケット境界と SCPI の応答境界が一致しないことを再現する。
     * 「1 回の read で全部届く」前提の実装はここで必ず壊れる。
     */
    private fun sendPayload(output: OutputStream, payload: ByteArray) {
        when (config.faultMode) {
            FaultMode.SPLIT_RESPONSE -> {
                var offset = 0
                while (offset < payload.size) {
                    val length = minOf(config.chunkSize, payload.size - offset)
                    output.write(payload, offset, length)
                    output.flush()
                    offset += length
                    if (config.chunkDelayMillis > 0) Thread.sleep(config.chunkDelayMillis)
                }
            }

            FaultMode.DISCONNECT_MIDWAY -> {
                val half = (payload.size / 2).coerceAtLeast(1)
                output.write(payload, 0, half)
                output.flush()
                throw SocketException("simulated disconnect")
            }

            else -> writeAll(output, payload)
        }
    }

    private fun writeAll(output: OutputStream, payload: ByteArray) {
        output.write(payload)
        output.flush()
    }

    /** LF までを 1 行として読む。CR は捨てる。 */
    private fun readLine(input: InputStream): String? {
        val buffer = ByteArrayOutputStream()
        while (true) {
            val value = input.read()
            if (value < 0) return if (buffer.size() == 0) null else buffer.toString(Charsets.US_ASCII.name())
            if (value == LINE_FEED) return buffer.toString(Charsets.US_ASCII.name())
            if (value != CARRIAGE_RETURN) buffer.write(value)
            if (buffer.size() > MAX_COMMAND_LENGTH) return buffer.toString(Charsets.US_ASCII.name())
        }
    }

    private fun log(message: String) {
        if (config.verbose) println("[simulator] $message")
    }

    companion object {
        private const val LINE_FEED = 0x0A
        private const val CARRIAGE_RETURN = 0x0D
        private const val MAX_COMMAND_LENGTH = 64 * 1024
        private const val TERMINAL_BANNER = "Tektronix Instrument Control Terminal\r\n> "
        private const val TERMINAL_PROMPT = "> "
    }
}
