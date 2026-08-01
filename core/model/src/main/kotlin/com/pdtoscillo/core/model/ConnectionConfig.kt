package com.pdtoscillo.core.model

/** 通信方式。Transport 実装ごとに 1 つ。 */
enum class TransportType {
    /** TCP Raw Socket（Tektronix の Socket Server）。本アプリの第一実装。 */
    RAW_SOCKET,

    /** VXI-11（ONC RPC）。未実装。docs/vxi11-feasibility.md を参照。 */
    VXI11,
}

/** コマンド末尾に付与する終端。機種設定に合わせて切り替える。 */
enum class LineTerminator(val bytes: ByteArray) {
    LF(byteArrayOf(0x0A)),
    CRLF(byteArrayOf(0x0D, 0x0A)),
}

/**
 * ソケットを Ethernet の Network へ結びつける方法。
 *
 * PDT-FP1 では Ethernet・Wi-Fi・モバイル回線が同時に有効になり得る。LAN 直結ではインターネット
 * 到達性が無いため Ethernet が既定ルートに選ばれず、何もしないとモバイル回線へ出てしまう。
 */
enum class SocketBindStrategy {
    /** `Network.socketFactory.createSocket()` でソケットを生成する。既定。 */
    ETHERNET_SOCKET_FACTORY,

    /** 生成済みソケットへ `Network.bindSocket()` を適用する。 */
    ETHERNET_BIND_SOCKET,

    /**
     * ソケットのソース（ローカル）アドレスを有線 I/F の IP に固定する。
     *
     * 上の 2 方式は Android の `TRANSPORT_ETHERNET` な `Network` オブジェクトを必要とする。
     * ところが PDT-FP1 の LAN 端子はイーサネットテザリング扱いになり、`ConnectivityManager`
     * に Ethernet として登録されないことがある（実機 DPO4034 で確認）。その場合、上の 2 方式は
     * バインド対象の `Network` が無く失敗する。
     *
     * 本方式は `Network` 抽象を経由せず、`NetworkInterface` 列挙で得た eth0 相当の IP を
     * `Socket.bind()` でソースアドレスに設定する。宛先が eth0 の直結サブネット上にあれば、
     * OS は最長プレフィックス一致でその経路（eth0）を選ぶため、モバイル回線へ漏れない。
     * テザリングモードでも機能する。
     */
    ETHERNET_INTERFACE_ADDRESS,

    /** バインドしない（既定ルート）。診断や Wi-Fi 経由の切り分け用。 */
    SYSTEM_DEFAULT,
}

/**
 * 接続設定。
 *
 * 初期値は Tektronix Socket Server の一般的な設定（ポート 4000 / Protocol None）に合わせているが、
 * すべてユーザーが変更できる。
 */
data class ConnectionConfig(
    val host: String,
    val port: Int = DEFAULT_PORT,
    val transportType: TransportType = TransportType.RAW_SOCKET,
    val bindStrategy: SocketBindStrategy = SocketBindStrategy.ETHERNET_SOCKET_FACTORY,
    val terminator: LineTerminator = LineTerminator.LF,
    val connectTimeoutMillis: Long = DEFAULT_CONNECT_TIMEOUT_MILLIS,
    val readTimeoutMillis: Long = DEFAULT_READ_TIMEOUT_MILLIS,
    val queryTimeoutMillis: Long = DEFAULT_QUERY_TIMEOUT_MILLIS,
    val waveformTimeoutMillis: Long = DEFAULT_WAVEFORM_TIMEOUT_MILLIS,
    val keepAlive: Boolean = true,
    val tcpNoDelay: Boolean = true,
    val autoReconnect: Boolean = true,
    val maxReconnectAttempts: Int = DEFAULT_MAX_RECONNECT_ATTEMPTS,
    val reconnectDelayMillis: Long = DEFAULT_RECONNECT_DELAY_MILLIS,
    val maxBinaryResponseBytes: Long = DEFAULT_MAX_BINARY_RESPONSE_BYTES,
    val label: String? = null,
) {
    init {
        require(port in 1..65535) { "ポート番号が範囲外です: $port" }
        require(connectTimeoutMillis > 0) { "connectTimeoutMillis は正の値である必要があります" }
        require(maxBinaryResponseBytes > 0) { "maxBinaryResponseBytes は正の値である必要があります" }
        require(maxReconnectAttempts >= 0) { "maxReconnectAttempts は 0 以上である必要があります" }
    }

    companion object {
        /** Tektronix Socket Server の初期候補ポート。 */
        const val DEFAULT_PORT: Int = 4000

        const val DEFAULT_CONNECT_TIMEOUT_MILLIS: Long = 5_000
        const val DEFAULT_READ_TIMEOUT_MILLIS: Long = 5_000
        const val DEFAULT_QUERY_TIMEOUT_MILLIS: Long = 5_000

        /** 波形転送は通常の Query より長い時間を要する。 */
        const val DEFAULT_WAVEFORM_TIMEOUT_MILLIS: Long = 30_000

        const val DEFAULT_MAX_RECONNECT_ATTEMPTS: Int = 3
        const val DEFAULT_RECONNECT_DELAY_MILLIS: Long = 2_000

        /** 異常なブロック長で無制限に確保しないための上限（32 MiB）。 */
        const val DEFAULT_MAX_BINARY_RESPONSE_BYTES: Long = 32L * 1024 * 1024
    }
}
