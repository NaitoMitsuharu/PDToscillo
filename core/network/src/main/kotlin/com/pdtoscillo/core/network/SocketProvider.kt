package com.pdtoscillo.core.network

import com.pdtoscillo.core.common.PdtLog
import com.pdtoscillo.core.model.ScopeError
import com.pdtoscillo.core.model.SocketBindStrategy
import java.net.Socket

/** ソケット生成の結果。どのようにバインドされたかを併せて返す。 */
data class ProvidedSocket(
    val socket: Socket,
    val appliedStrategy: SocketBindStrategy,
    val ethernetLink: EthernetLinkInfo?,
    /** バインドを要求したが実現できなかった場合の理由。 */
    val fallbackReason: String?,
)

/**
 * ソケットの生成方法を抽象化する。
 *
 * Android 依存部分（`Network` へのバインド）をここへ閉じ込め、
 * [RawSocketTransport] を JVM 単体テストで動かせるようにする。
 */
interface SocketProvider {
    /**
     * 未接続のソケットを生成する。
     *
     * @throws com.pdtoscillo.core.scpi.ScpiException バインドが必須なのに Ethernet が無い場合。
     */
    fun createSocket(strategy: SocketBindStrategy): ProvidedSocket

    /** 現在の Ethernet 情報。診断表示と経路検証に使う。 */
    fun ethernetLink(): EthernetLinkInfo?
}

/**
 * バインドを行わない実装。単体テストと、Ethernet を使わない切り分け用。
 */
class PlainSocketProvider : SocketProvider {
    override fun createSocket(strategy: SocketBindStrategy): ProvidedSocket = ProvidedSocket(
        socket = Socket(),
        appliedStrategy = SocketBindStrategy.SYSTEM_DEFAULT,
        ethernetLink = null,
        fallbackReason = if (strategy == SocketBindStrategy.SYSTEM_DEFAULT) {
            null
        } else {
            "この環境では Ethernet へのバインドを行いません"
        },
    )

    override fun ethernetLink(): EthernetLinkInfo? = null
}

/**
 * Ethernet の [android.net.Network] へソケットを結びつける実装。
 *
 * PDT-FP1 では Ethernet・Wi-Fi・モバイル回線が同時に有効になり得る。LAN 直結では
 * インターネット到達性が無いため Ethernet は既定ルートに選ばれず、**バインドしないと
 * モバイル回線へ出てしまう。**
 *
 * 2 通りの方式を用意する。機種によりどちらかが期待どおりに動かない可能性があるため、
 * ユーザーが切り替えられるようにしてある。
 *
 * - [SocketBindStrategy.ETHERNET_SOCKET_FACTORY]: `network.socketFactory.createSocket()`
 * - [SocketBindStrategy.ETHERNET_BIND_SOCKET]: 生成済みソケットへ `network.bindSocket()`
 */
class EthernetSocketProvider(private val monitor: EthernetNetworkMonitor) : SocketProvider {
    override fun createSocket(strategy: SocketBindStrategy): ProvidedSocket {
        if (strategy == SocketBindStrategy.SYSTEM_DEFAULT) {
            return ProvidedSocket(Socket(), SocketBindStrategy.SYSTEM_DEFAULT, monitor.status.value.ethernetLink, null)
        }

        monitor.refresh()
        val network = monitor.ethernetNetwork()
        if (network == null) {
            // ここで黙って既定ルートへ落とすと、モバイル回線経由で「繋がったように見える」ため危険。
            // 呼び出し側が明示的に SYSTEM_DEFAULT を選ぶまでは失敗として扱う。
            throw com.pdtoscillo.core.scpi.ScpiException(
                ScopeError.EthernetUnavailable(
                    "Ethernet が見つかりません。LAN ケーブルの接続を確認してください。" +
                        "Ethernet を使わずに接続する場合はバインド方式を「システム既定」にしてください。",
                ),
            )
        }

        val link = monitor.status.value.ethernetLink
        return when (strategy) {
            SocketBindStrategy.ETHERNET_SOCKET_FACTORY -> {
                val socket = runCatching { network.socketFactory.createSocket() }.getOrElse { error ->
                    PdtLog.w(TAG, "socketFactory でのソケット生成に失敗しました", error)
                    throw com.pdtoscillo.core.scpi.ScpiException(
                        ScopeError.BindFailed("socketFactory.createSocket() が失敗しました", error),
                    )
                }
                ProvidedSocket(socket, strategy, link, null)
            }

            SocketBindStrategy.ETHERNET_BIND_SOCKET -> {
                val socket = Socket()
                runCatching { network.bindSocket(socket) }.getOrElse { error ->
                    runCatching { socket.close() }
                    PdtLog.w(TAG, "bindSocket に失敗しました", error)
                    throw com.pdtoscillo.core.scpi.ScpiException(
                        ScopeError.BindFailed("network.bindSocket() が失敗しました", error),
                    )
                }
                ProvidedSocket(socket, strategy, link, null)
            }

            SocketBindStrategy.SYSTEM_DEFAULT -> error("到達しない")
        }
    }

    override fun ethernetLink(): EthernetLinkInfo? {
        monitor.refresh()
        return monitor.status.value.ethernetLink
    }

    private companion object {
        const val TAG = "EthernetSocketProvider"
    }
}
