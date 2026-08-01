package com.pdtoscillo.core.network

import com.pdtoscillo.core.common.PdtLog
import com.pdtoscillo.core.model.ScopeError
import com.pdtoscillo.core.model.SocketBindStrategy
import java.net.InetAddress
import java.net.InetSocketAddress
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
     * @param targetHost 接続先ホスト。[SocketBindStrategy.ETHERNET_INTERFACE_ADDRESS] で
     *   同一サブネットの有線 I/F を選ぶために使う。不要な方式では無視される。
     * @throws com.pdtoscillo.core.scpi.ScpiException バインドが必須なのに Ethernet が無い場合。
     */
    fun createSocket(strategy: SocketBindStrategy, targetHost: String? = null): ProvidedSocket

    /** 現在の Ethernet 情報。診断表示と経路検証に使う。 */
    fun ethernetLink(): EthernetLinkInfo?

    /**
     * eth0 相当の I/F が持つローカル IPv4 の集合。
     *
     * `TRANSPORT_ETHERNET` に依存しない経路検証に使う。テザリング扱いで [ethernetLink] が
     * 空でも、ここに eth0 のアドレスが入るため、有線側へ出ているかを判定できる。
     */
    fun ethernetLikeAddresses(): Set<String> = emptySet()
}

/**
 * バインドを行わない実装。単体テストと、Ethernet を使わない切り分け用。
 */
class PlainSocketProvider : SocketProvider {
    override fun createSocket(strategy: SocketBindStrategy, targetHost: String?): ProvidedSocket = ProvidedSocket(
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
    override fun createSocket(strategy: SocketBindStrategy, targetHost: String?): ProvidedSocket {
        if (strategy == SocketBindStrategy.SYSTEM_DEFAULT) {
            return ProvidedSocket(Socket(), SocketBindStrategy.SYSTEM_DEFAULT, monitor.status.value.ethernetLink, null)
        }

        monitor.refresh()

        // ソースアドレス固定は Network オブジェクトを必要としないため、テザリング扱いで
        // TRANSPORT_ETHERNET が無い環境でも動く。他方式より先に処理する。
        if (strategy == SocketBindStrategy.ETHERNET_INTERFACE_ADDRESS) {
            return createInterfaceBoundSocket(targetHost)
        }

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

            SocketBindStrategy.ETHERNET_INTERFACE_ADDRESS,
            SocketBindStrategy.SYSTEM_DEFAULT,
            -> error("到達しない")
        }
    }

    /**
     * 有線 I/F の IP をソースアドレスに固定したソケットを作る。
     *
     * 有線 I/F が見つからない場合は、黙って既定ルート（＝モバイル回線の可能性）へ落とさず
     * 失敗として扱う。「繋がったように見えて実はモバイル経由」を避けるため。
     */
    private fun createInterfaceBoundSocket(targetHost: String?): ProvidedSocket {
        val localIp = targetHost?.let { monitor.localAddressForTarget(it) }
            ?: monitor.ethernetLikeLocalAddresses().firstOrNull()?.address

        if (localIp == null) {
            throw com.pdtoscillo.core.scpi.ScpiException(
                ScopeError.EthernetUnavailable(
                    "有線インターフェースの IP アドレスが見つかりません。LAN ケーブルの接続を確認してください。" +
                        "有線を使わずに接続する場合はバインド方式を「システム既定」にしてください。",
                ),
            )
        }

        val socket = Socket()
        runCatching { socket.bind(InetSocketAddress(InetAddress.getByName(localIp), 0)) }.getOrElse { error ->
            runCatching { socket.close() }
            PdtLog.w(TAG, "ソースアドレス $localIp へのバインドに失敗しました", error)
            throw com.pdtoscillo.core.scpi.ScpiException(
                ScopeError.BindFailed("ソースアドレス $localIp へのバインドに失敗しました", error),
            )
        }
        PdtLog.i(TAG, "有線 I/F の $localIp をソースアドレスに固定しました")
        return ProvidedSocket(
            socket = socket,
            appliedStrategy = SocketBindStrategy.ETHERNET_INTERFACE_ADDRESS,
            ethernetLink = monitor.status.value.ethernetLink,
            fallbackReason = null,
        )
    }

    override fun ethernetLink(): EthernetLinkInfo? {
        monitor.refresh()
        return monitor.status.value.ethernetLink
    }

    override fun ethernetLikeAddresses(): Set<String> = monitor.ethernetLikeLocalAddresses().map { it.address }.toSet()

    private companion object {
        const val TAG = "EthernetSocketProvider"
    }
}
