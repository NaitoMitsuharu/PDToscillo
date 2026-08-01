package com.pdtoscillo.core.network

import java.net.Inet4Address
import java.net.InetAddress

/**
 * 宛先へ通信する際にソースアドレスへ固定すべきローカル IPv4 を選ぶ純粋ロジック。
 *
 * Android 非依存にして JVM 単体テストで検証できるようにしてある。
 * [SocketBindStrategy.ETHERNET_INTERFACE_ADDRESS][com.pdtoscillo.core.model.SocketBindStrategy]
 * のバインド先決定と、経路検証の同一サブネット判定で共有する。
 */
object LocalAddressSelector {
    /**
     * 候補（インターフェースの IP/プレフィックス）から、宛先に対して使うべき IPv4 を選ぶ。
     *
     * 宛先と同一サブネットの候補を優先する。無ければ先頭の IPv4。IPv4 候補が無ければ null。
     */
    fun selectForTarget(candidates: List<InterfaceAddress>, targetHost: String): String? {
        val ipv4 = candidates.filter { !it.address.contains(':') }
        if (ipv4.isEmpty()) return null

        val target = parseIpv4(targetHost)
        if (target != null) {
            ipv4.firstOrNull { candidate ->
                val local = parseIpv4(candidate.address)
                local != null && sameSubnet(local, target, candidate.prefixLength)
            }?.let { return it.address }
        }
        return ipv4.first().address
    }

    /** [target] が [local]/[prefixLength] と同一サブネットにあるか（IPv4）。 */
    fun sameSubnet(local: Inet4Address, target: Inet4Address, prefixLength: Int): Boolean {
        if (prefixLength !in 0..IPV4_BITS) return false
        val left = local.address.fold(0L) { acc, byte -> (acc shl BITS_PER_OCTET) or (byte.toLong() and BYTE_MASK) }
        val right = target.address.fold(0L) { acc, byte -> (acc shl BITS_PER_OCTET) or (byte.toLong() and BYTE_MASK) }
        val mask = if (prefixLength == 0) 0L else (0xFFFFFFFFL shl (IPV4_BITS - prefixLength)) and 0xFFFFFFFFL
        return (left and mask) == (right and mask)
    }

    /** 数値 IPv4 のみを解釈する。ホスト名を渡しても DNS 解決はしない（`.` 区切りの数字のみ）。 */
    private fun parseIpv4(value: String): Inet4Address? {
        val octets = value.split('.')
        if (octets.size != IPV4_OCTETS) return null
        if (octets.any { part -> part.toIntOrNull()?.let { it in 0..0xFF } != true }) return null
        return runCatching { InetAddress.getByName(value) }.getOrNull() as? Inet4Address
    }

    private const val IPV4_BITS = 32
    private const val IPV4_OCTETS = 4
    private const val BITS_PER_OCTET = 8
    private const val BYTE_MASK = 0xFFL
}
