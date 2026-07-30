package com.pdtoscillo.core.network

import android.content.Context
import com.pdtoscillo.core.common.CommunicationLogRecorder
import com.pdtoscillo.core.model.ConnectionConfig
import com.pdtoscillo.core.model.SocketBindStrategy
import com.pdtoscillo.core.scpi.ScpiClient
import com.pdtoscillo.core.scpi.Tektronix4000Driver
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * アプリ全体で 1 つだけ持つ計測器セッション。
 *
 * 1 台のオシロスコープに対して 1 本の接続と 1 本のコマンドキューを共有する。
 * 画面ごとに接続を作ると、Query の応答が別画面へ渡るなどの取り違えが起きる。
 *
 * 画面回転や画面遷移で作り直さないこと。`Application` が保持する。
 */
class InstrumentSession(context: Context) {

    val networkMonitor: EthernetNetworkMonitor = EthernetNetworkMonitor(context)

    private val socketProvider: SocketProvider = EthernetSocketProvider(networkMonitor)

    val transport: RawSocketTransport = RawSocketTransport(socketProvider)

    val logRecorder: CommunicationLogRecorder = CommunicationLogRecorder()

    val client: ScpiClient = ScpiClient(transport, logRecorder)

    /** 高レベル操作。画面はこれを通して計測器を操作する。 */
    val driver: Tektronix4000Driver = Tektronix4000Driver(client)

    val diagnostics: ConnectionDiagnostics = ConnectionDiagnostics(networkMonitor, client)

    val discovery: DeviceDiscovery = DeviceDiscovery()

    val networkStatus: StateFlow<NetworkStatus> = networkMonitor.status

    private val _lastConfig = MutableStateFlow(
        ConnectionConfig(host = "", port = ConnectionConfig.DEFAULT_PORT),
    )

    /** 直近に使った接続設定。再接続と診断の既定値に使う。 */
    val lastConfig: StateFlow<ConnectionConfig> = _lastConfig.asStateFlow()

    fun start() {
        networkMonitor.start()
    }

    fun stop() {
        networkMonitor.stop()
    }

    fun rememberConfig(config: ConnectionConfig) {
        _lastConfig.value = config
    }

    /**
     * Ethernet が使えない環境向けに、バインド方式をシステム既定へ落とした設定を返す。
     *
     * 自動でこれに切り替えることはしない。モバイル回線経由で「繋がったように見える」状態を
     * 黙って作らないため、切り替えは利用者の明示的な選択に委ねる。
     */
    fun withSystemDefaultBinding(config: ConnectionConfig): ConnectionConfig = config.copy(bindStrategy = SocketBindStrategy.SYSTEM_DEFAULT)
}
