package com.pdtoscillo.core.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.net.InetAddress

/**
 * ソースアドレス選択の単体テスト。
 *
 * PDT-FP1 のテザリング接続では、eth0 の IP をソースアドレスに固定して有線側へ出す。
 * その選択ロジックが「宛先と同じサブネットの IP を選ぶ」ことを確かめる。
 */
class LocalAddressSelectorTest {

    private fun addr(ip: String, prefix: Int) = InterfaceAddress(ip, prefix)

    @Test
    fun `宛先と同一サブネットの候補を選ぶ`() {
        val candidates = listOf(
            addr("192.168.0.5", 24),
            addr("10.175.225.170", 24),
        )
        // 実機 DPO4034 の払い出しレンジ。10.175.225.x を選ぶこと。
        assertEquals("10.175.225.170", LocalAddressSelector.selectForTarget(candidates, "10.175.225.142"))
    }

    @Test
    fun `同一サブネットが無ければ先頭のIPv4を返す`() {
        val candidates = listOf(addr("192.168.0.5", 24))
        assertEquals("192.168.0.5", LocalAddressSelector.selectForTarget(candidates, "10.0.0.1"))
    }

    @Test
    fun `IPv6候補は無視して IPv4 を選ぶ`() {
        val candidates = listOf(
            addr("fe80::1", 64),
            addr("10.175.225.170", 24),
        )
        assertEquals("10.175.225.170", LocalAddressSelector.selectForTarget(candidates, "10.175.225.142"))
    }

    @Test
    fun `候補が空なら null`() {
        assertNull(LocalAddressSelector.selectForTarget(emptyList(), "10.175.225.142"))
    }

    @Test
    fun `IPv4候補が無ければ null`() {
        assertNull(LocalAddressSelector.selectForTarget(listOf(addr("fe80::1", 64)), "10.175.225.142"))
    }

    @Test
    fun `同一サブネット判定はプレフィックス長に従う`() {
        val local = InetAddress.getByName("10.175.225.170") as java.net.Inet4Address
        val sameSubnet = InetAddress.getByName("10.175.225.1") as java.net.Inet4Address
        val otherSubnet = InetAddress.getByName("10.175.226.1") as java.net.Inet4Address

        assert(LocalAddressSelector.sameSubnet(local, sameSubnet, 24))
        assert(!LocalAddressSelector.sameSubnet(local, otherSubnet, 24))
        // /16 なら 10.175.x.x は同一
        assert(LocalAddressSelector.sameSubnet(local, otherSubnet, 16))
    }
}
