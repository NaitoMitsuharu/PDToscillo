package com.pdtoscillo.core.network

import com.pdtoscillo.core.model.ConnectionConfig
import com.pdtoscillo.core.model.SocketBindStrategy
import com.pdtoscillo.core.scpi.ScpiClient
import com.pdtoscillo.simulator.FaultMode
import com.pdtoscillo.simulator.ScopeSimulator
import com.pdtoscillo.simulator.SimulatedModel
import com.pdtoscillo.simulator.SimulatorConfig
import com.pdtoscillo.simulator.WaveformShape

/**
 * 統合テストの土台。
 *
 * 実機なしで「実際に TCP を張って SCPI をやり取りする」ところまで検証する。
 * 疑似サーバーはポート 0 で起動し、実際に割り当てられたポートへ接続する。
 */
class SimulatorHarness(
    model: SimulatedModel = SimulatedModel.MDO4104C,
    faultMode: FaultMode = FaultMode.NONE,
    waveformShape: WaveformShape = WaveformShape.SINE,
    terminalMode: Boolean = false,
    chunkSize: Int = 7,
    chunkDelayMillis: Long = 0,
    responseDelayMillis: Long = 3_000,
) : AutoCloseable {

    val simulator = ScopeSimulator(
        SimulatorConfig(
            port = 0,
            model = model,
            faultMode = faultMode,
            waveformShape = waveformShape,
            terminalMode = terminalMode,
            chunkSize = chunkSize,
            chunkDelayMillis = chunkDelayMillis,
            responseDelayMillis = responseDelayMillis,
        ),
    )

    val port: Int = simulator.start()

    val transport = RawSocketTransport(PlainSocketProvider())
    val client = ScpiClient(transport)

    fun config(
        connectTimeoutMillis: Long = 3_000,
        readTimeoutMillis: Long = 2_000,
        queryTimeoutMillis: Long = 2_000,
        waveformTimeoutMillis: Long = 10_000,
        autoReconnect: Boolean = true,
        maxReconnectAttempts: Int = 2,
        reconnectDelayMillis: Long = 50,
        maxBinaryResponseBytes: Long = 32L * 1024 * 1024,
    ): ConnectionConfig = ConnectionConfig(
        host = "127.0.0.1",
        port = port,
        // テスト環境に Android の Network は無いため、バインドはしない。
        bindStrategy = SocketBindStrategy.SYSTEM_DEFAULT,
        connectTimeoutMillis = connectTimeoutMillis,
        readTimeoutMillis = readTimeoutMillis,
        queryTimeoutMillis = queryTimeoutMillis,
        waveformTimeoutMillis = waveformTimeoutMillis,
        autoReconnect = autoReconnect,
        maxReconnectAttempts = maxReconnectAttempts,
        reconnectDelayMillis = reconnectDelayMillis,
        maxBinaryResponseBytes = maxBinaryResponseBytes,
    )

    /** 接続し、設定変更を許可した状態にする。読み取り専用モードの検証時は使わない。 */
    suspend fun connectAndUnlock(config: ConnectionConfig = config()): ConnectionConfig {
        client.connect(config)
        client.setReadOnlyMode(false)
        return config
    }

    override fun close() {
        simulator.close()
    }
}
