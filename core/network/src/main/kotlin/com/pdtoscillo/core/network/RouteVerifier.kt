package com.pdtoscillo.core.network

import com.pdtoscillo.core.model.SocketBindStrategy
import com.pdtoscillo.core.model.TransportRouteInfo
import java.net.Socket

/**
 * 接続が本当に意図した経路を通っているかを検証する。
 *
 * 「繋がったから正しい」とは限らない。Wi-Fi やモバイル回線経由で別の機器へ繋がっている、
 * あるいはインターネット越しの誰かに繋がっている可能性がある。
 * ソケットのローカルアドレスが Ethernet に割り当てられたアドレスと一致するかで判定する。
 */
object RouteVerifier {
    fun verify(socket: Socket, requestedStrategy: SocketBindStrategy, ethernetLink: EthernetLinkInfo?): TransportRouteInfo {
        val localAddress = socket.localAddress?.hostAddress
        val remoteAddress = socket.inetAddress?.hostAddress

        val ethernetAddresses = ethernetLink?.addresses?.map { it.address }?.toSet().orEmpty()
        val boundToEthernet = localAddress != null && localAddress in ethernetAddresses

        val warning = buildWarning(
            requestedStrategy = requestedStrategy,
            boundToEthernet = boundToEthernet,
            localAddress = localAddress,
            ethernetLink = ethernetLink,
        )

        return TransportRouteInfo(
            localAddress = localAddress,
            localPort = socket.localPort.takeIf { it > 0 },
            remoteAddress = remoteAddress,
            remotePort = socket.port.takeIf { it > 0 },
            requestedBindStrategy = requestedStrategy,
            boundToEthernet = boundToEthernet,
            interfaceName = ethernetLink?.interfaceName,
            warning = warning,
        )
    }

    private fun buildWarning(
        requestedStrategy: SocketBindStrategy,
        boundToEthernet: Boolean,
        localAddress: String?,
        ethernetLink: EthernetLinkInfo?,
    ): String? = when {
        boundToEthernet -> null

        requestedStrategy == SocketBindStrategy.SYSTEM_DEFAULT ->
            "システム既定の経路で接続しました。Ethernet ではなく Wi-Fi またはモバイル回線を" +
                "経由している可能性があります（ローカルアドレス: ${localAddress ?: "不明"}）。"

        ethernetLink == null ->
            "Ethernet の情報を取得できないため、経路を確認できませんでした" +
                "（ローカルアドレス: ${localAddress ?: "不明"}）。"

        ethernetLink.addresses.isEmpty() ->
            "Ethernet に IP アドレスが割り当てられていません。静的 IP を設定するか、" +
                "DHCP でアドレスが得られるか確認してください。"

        else ->
            "Ethernet へバインドを要求しましたが、ローカルアドレス ${localAddress ?: "不明"} は " +
                "Ethernet のアドレス（${ethernetLink.addresses.joinToString()}）と一致しません。" +
                "モバイル回線へ誤ルーティングされている可能性があります。"
    }
}
