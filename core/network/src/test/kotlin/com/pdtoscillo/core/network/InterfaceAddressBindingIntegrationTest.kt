package com.pdtoscillo.core.network

import com.pdtoscillo.core.model.ConnectionConfig
import com.pdtoscillo.core.model.SocketBindStrategy
import com.pdtoscillo.core.scpi.ScpiClient
import com.pdtoscillo.simulator.ScopeSimulator
import com.pdtoscillo.simulator.SimulatedModel
import com.pdtoscillo.simulator.SimulatorConfig
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket

/**
 * ソースアドレス固定バインドの結合テスト。
 *
 * PDT-FP1 のテザリング接続では eth0 の IP をソースアドレスに固定して有線側へ出す
 * （[SocketBindStrategy.ETHERNET_INTERFACE_ADDRESS]）。Android の `Network` が要らない
 * この方式が、実際に `Socket.bind()` してから接続し、`*IDN?` まで通ることを
 * ループバックで確かめる（実機の eth0 相当をローカルアドレスで代用）。
 */
class InterfaceAddressBindingIntegrationTest {

    private lateinit var simulator: ScopeSimulator
    private var port: Int = 0

    /** ソースアドレスを固定するテスト用 provider。実機の EthernetSocketProvider の骨子と同じ。 */
    private class SourceBindingSocketProvider(private val localIp: String) : SocketProvider {
        override fun createSocket(strategy: SocketBindStrategy, targetHost: String?): ProvidedSocket {
            val socket = Socket()
            socket.bind(InetSocketAddress(InetAddress.getByName(localIp), 0))
            return ProvidedSocket(socket, SocketBindStrategy.ETHERNET_INTERFACE_ADDRESS, null, null)
        }

        override fun ethernetLink(): EthernetLinkInfo? = null

        override fun ethernetLikeAddresses(): Set<String> = setOf(localIp)
    }

    @Before
    fun setUp() {
        simulator = ScopeSimulator(SimulatorConfig(port = 0, model = SimulatedModel.MDO4104C))
        port = simulator.start()
    }

    @After
    fun tearDown() {
        simulator.close()
    }

    @Test
    fun `ソースアドレスを固定しても接続しIDNを取得できる`() = runBlocking {
        val transport = RawSocketTransport(SourceBindingSocketProvider("127.0.0.1"))
        val client = ScpiClient(transport)
        try {
            client.connect(
                ConnectionConfig(
                    host = "127.0.0.1",
                    port = port,
                    bindStrategy = SocketBindStrategy.ETHERNET_INTERFACE_ADDRESS,
                    connectTimeoutMillis = 3_000,
                    readTimeoutMillis = 2_000,
                ),
            )

            val identity = client.identify()
            assertTrue("*IDN? の応答が空", identity.raw.isNotBlank())

            val route = client.routeInfo.value
            assertTrue("有線側と判定されていない", route?.boundToEthernet == true)
            assertNull("有線側なのに警告が出ている", route?.warning)
        } finally {
            client.disconnect()
        }
    }
}
