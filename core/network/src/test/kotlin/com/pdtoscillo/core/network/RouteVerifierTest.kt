package com.pdtoscillo.core.network

import com.pdtoscillo.core.model.SocketBindStrategy
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket

/**
 * 経路検証のテスト。
 *
 * PDT-FP1 のテザリング接続では `ConnectivityManager` が Ethernet を報告しないため
 * [EthernetLinkInfo] が空になる。それでも `NetworkInterface` 列挙で得た eth0 相当の
 * アドレスから出ていれば「有線側で正常」と判定できることを確かめる。
 */
class RouteVerifierTest {

    private lateinit var server: ServerSocket
    private lateinit var client: Socket
    private var accepted: Socket? = null

    @Before
    fun setUp() {
        server = ServerSocket(0, 1, java.net.InetAddress.getByName("127.0.0.1"))
        client = Socket()
        client.connect(InetSocketAddress("127.0.0.1", server.localPort), 2000)
        accepted = server.accept()
    }

    @After
    fun tearDown() {
        runCatching { accepted?.close() }
        runCatching { client.close() }
        runCatching { server.close() }
    }

    @Test
    fun `eth0相当のアドレスから出ていれば有線側と判定し警告しない`() {
        val route = RouteVerifier.verify(
            socket = client,
            requestedStrategy = SocketBindStrategy.ETHERNET_INTERFACE_ADDRESS,
            ethernetLink = null,
            ethernetLikeAddresses = setOf("127.0.0.1"),
        )

        assertTrue("有線側と判定されていない", route.boundToEthernet)
        assertNull("有線側なのに警告が出ている", route.warning)
        assertEquals("127.0.0.1", route.localAddress)
    }

    @Test
    fun `システム既定でも実アドレスが有線なら警告しない`() {
        // テザリング時: ethernetLink は空だが、実際には eth0 の IP から出ている。
        val route = RouteVerifier.verify(
            socket = client,
            requestedStrategy = SocketBindStrategy.SYSTEM_DEFAULT,
            ethernetLink = null,
            ethernetLikeAddresses = setOf("127.0.0.1"),
        )

        assertTrue(route.boundToEthernet)
        assertNull(route.warning)
    }

    @Test
    fun `有線アドレスの情報が無いシステム既定は警告する`() {
        val route = RouteVerifier.verify(
            socket = client,
            requestedStrategy = SocketBindStrategy.SYSTEM_DEFAULT,
            ethernetLink = null,
            ethernetLikeAddresses = emptySet(),
        )

        // 有線側の裏付けが無いので、モバイル回線へ出ている可能性を警告する。
        assertTrue("警告が出ていない", route.warning != null)
    }
}
