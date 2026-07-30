package com.pdtoscillo.core.model

import kotlinx.coroutines.flow.StateFlow

/**
 * 計測器との通信方式を抽象化する。
 *
 * Raw Socket 実装へ VXI-11 固有の処理を混在させないため、方式ごとに実装を分ける。
 * この層は SCPI の意味を知らない。バイト列の送受信と接続状態の管理だけを行う。
 *
 * 実装は**呼び出しの直列化を前提としない**。直列化は上位の SCPI コマンドキューが担う。
 */
interface InstrumentTransport {
    val state: StateFlow<ConnectionState>

    /** 現在の接続で使われているソケットの実際の経路情報。診断表示に使う。 */
    val routeInfo: StateFlow<TransportRouteInfo?>

    suspend fun connect(config: ConnectionConfig)

    suspend fun disconnect()

    /** 終端は実装が `ConnectionConfig.terminator` に従って付与する。 */
    suspend fun write(command: ByteArray)

    /** 終端（LF）までのテキストを 1 行読む。 */
    suspend fun readText(): String

    /** IEEE 488.2 definite-length block を 1 つ読む。 */
    suspend fun readBinary(): ByteArray

    suspend fun queryText(command: String): String

    suspend fun queryBinary(command: String): ByteArray

    /**
     * 受信バッファに残っているデータを破棄する。
     * 応答を読み切れずストリーム同期を失った場合の復帰に使う。
     */
    suspend fun discardPendingInput(): Int
}

/**
 * コマンド単位の締め切りを受け取れる Transport。
 *
 * ブロッキング読み取りはコルーチンのキャンセルでは中断できない。`withTimeout` だけに頼ると、
 * タイムアウト後も裏でソケットを読み続けるスレッドが残り、次のコマンドと衝突する。
 * これを避けるため、実行するコマンドの制限時間をソケット自身の読み取り締め切りへ反映させる。
 */
interface TimeoutAwareTransport {
    /** 次のコマンドに使う読み取り締め切りを設定する。 */
    fun applyOperationTimeout(millis: Long)
}

/**
 * ソケットが実際にどの経路を通っているか。
 *
 * PDT-FP1 では Ethernet・Wi-Fi・モバイル回線が同時に有効になり得るため、
 * 「意図した Ethernet を通っているか」を接続後に検証して保持する。
 */
data class TransportRouteInfo(
    val localAddress: String?,
    val localPort: Int?,
    val remoteAddress: String?,
    val remotePort: Int?,
    val requestedBindStrategy: SocketBindStrategy,
    val boundToEthernet: Boolean,
    val interfaceName: String?,
    val warning: String?,
)
