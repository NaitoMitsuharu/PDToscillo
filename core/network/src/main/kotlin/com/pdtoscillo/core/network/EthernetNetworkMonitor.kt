package com.pdtoscillo.core.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import com.pdtoscillo.core.common.PdtLog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.net.Inet4Address
import java.net.NetworkInterface

/**
 * Ethernet の検出と監視。
 *
 * PDT-FP1 では Ethernet・Wi-Fi・モバイル通信が同時に有効になり得る。さらに LAN 直結では
 * インターネット到達性が無いため、Ethernet は「既定のネットワーク」に選ばれない。
 * したがって、
 *
 * 1. **インターネット到達性を要求しない** `NetworkRequest` で監視する
 *    （`clearCapabilities()` を呼ばないと `NET_CAPABILITY_NOT_RESTRICTED` などが付き、
 *    直結時に一致しない可能性がある）
 * 2. Ethernet の [Network] を掴んでソケットのバインド先に使う
 * 3. `ConnectivityManager` が Ethernet を報告しない機種向けに、
 *    `NetworkInterface` 列挙による予備情報も併せて持つ
 */
class EthernetNetworkMonitor(context: Context) {
    private val connectivityManager =
        context.applicationContext.getSystemService(ConnectivityManager::class.java)

    private val _status = MutableStateFlow(NetworkStatus.UNKNOWN)
    val status: StateFlow<NetworkStatus> = _status.asStateFlow()

    private val networks = mutableMapOf<Network, NetworkSnapshot>()
    private var callback: ConnectivityManager.NetworkCallback? = null

    private data class NetworkSnapshot(val capabilities: NetworkCapabilities?, val linkProperties: LinkProperties?)

    /** 監視を開始する。既に開始済みなら何もしない。 */
    fun start() {
        if (callback != null) return
        val manager = connectivityManager ?: run {
            PdtLog.e(TAG, "ConnectivityManager を取得できませんでした")
            refresh()
            return
        }

        // インターネット到達性を要求しない。LAN 直結では到達性が無いのが正常。
        val request = NetworkRequest.Builder()
            .clearCapabilities()
            .build()

        val newCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                update(network) { it.copy() }
            }

            override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
                update(network) { it.copy(capabilities = capabilities) }
            }

            override fun onLinkPropertiesChanged(network: Network, linkProperties: LinkProperties) {
                update(network) { it.copy(linkProperties = linkProperties) }
            }

            override fun onLost(network: Network) {
                synchronized(networks) { networks.remove(network) }
                PdtLog.i(TAG, "ネットワークが切断されました: $network")
                refresh()
            }
        }

        runCatching { manager.registerNetworkCallback(request, newCallback) }
            .onSuccess { callback = newCallback }
            .onFailure { PdtLog.e(TAG, "ネットワーク監視を開始できませんでした", it) }

        refresh()
    }

    fun stop() {
        val manager = connectivityManager ?: return
        callback?.let { runCatching { manager.unregisterNetworkCallback(it) } }
        callback = null
        synchronized(networks) { networks.clear() }
    }

    private fun update(network: Network, transform: (NetworkSnapshot) -> NetworkSnapshot) {
        synchronized(networks) {
            val current = networks[network] ?: NetworkSnapshot(null, null)
            val updated = transform(current)
            networks[network] = NetworkSnapshot(
                capabilities = updated.capabilities
                    ?: runCatching { connectivityManager?.getNetworkCapabilities(network) }.getOrNull(),
                linkProperties = updated.linkProperties
                    ?: runCatching { connectivityManager?.getLinkProperties(network) }.getOrNull(),
            )
        }
        refresh()
    }

    /** 現在の Ethernet ネットワーク。ソケットのバインド先に使う。 */
    fun ethernetNetwork(): Network? {
        synchronized(networks) {
            networks.entries
                .firstOrNull { it.value.capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) == true }
                ?.let { return it.key }
        }
        // コールバックが来ていない起動直後などに備え、その場で問い合わせる。
        return queryEthernetNetworkDirectly()
    }

    @Suppress("DEPRECATION")
    private fun queryEthernetNetworkDirectly(): Network? {
        val manager = connectivityManager ?: return null
        return runCatching {
            manager.allNetworks.firstOrNull { network ->
                manager.getNetworkCapabilities(network)
                    ?.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) == true
            }
        }.getOrNull()
    }

    /** 状態を今すぐ読み直す。診断の実行時に呼ぶ。 */
    fun refresh() {
        val manager = connectivityManager
        val snapshots = synchronized(networks) { networks.toMap() }

        val transports = mutableSetOf<NetworkTransport>()
        var ethernetLink: EthernetLinkInfo? = null

        for ((network, snapshot) in snapshots) {
            val capabilities = snapshot.capabilities
                ?: runCatching { manager?.getNetworkCapabilities(network) }.getOrNull()
            transports += classify(capabilities)
            if (capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) == true) {
                val link = snapshot.linkProperties
                    ?: runCatching { manager?.getLinkProperties(network) }.getOrNull()
                ethernetLink = link?.let(::toEthernetLinkInfo) ?: EthernetLinkInfo.EMPTY
            }
        }

        // コールバック未着でも判定できるようにする。
        if (ethernetLink == null) {
            queryEthernetNetworkDirectly()?.let { network ->
                transports += NetworkTransport.ETHERNET
                ethernetLink = runCatching { manager?.getLinkProperties(network) }.getOrNull()
                    ?.let(::toEthernetLinkInfo) ?: EthernetLinkInfo.EMPTY
            }
        }

        _status.value = NetworkStatus(
            ethernetAvailable = ethernetLink != null,
            ethernetLink = ethernetLink,
            activeTransports = transports,
            systemInterfaces = readSystemInterfaces(),
        )
    }

    private fun classify(capabilities: NetworkCapabilities?): Set<NetworkTransport> {
        if (capabilities == null) return emptySet()
        val result = mutableSetOf<NetworkTransport>()
        if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) result += NetworkTransport.ETHERNET
        if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) result += NetworkTransport.WIFI
        if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) result += NetworkTransport.CELLULAR
        if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) result += NetworkTransport.VPN
        if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_BLUETOOTH)) result += NetworkTransport.BLUETOOTH
        if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_USB)) result += NetworkTransport.USB
        if (result.isEmpty()) result += NetworkTransport.OTHER
        return result
    }

    private fun toEthernetLinkInfo(link: LinkProperties): EthernetLinkInfo = EthernetLinkInfo(
        interfaceName = link.interfaceName,
        addresses = link.linkAddresses.map {
            InterfaceAddress(it.address.hostAddress ?: it.address.toString(), it.prefixLength)
        },
        gateways = link.routes.mapNotNull { route ->
            route.gateway?.hostAddress?.takeIf { !route.gateway!!.isAnyLocalAddress }
        }.distinct(),
        dnsServers = link.dnsServers.mapNotNull { it.hostAddress },
        mtu = link.mtu.takeIf { it > 0 },
    )

    /**
     * OS のインターフェース一覧。
     *
     * `ConnectivityManager` が LAN 端子を Ethernet として報告しない場合でも、
     * ここに `eth0` 相当が現れることがある。診断で切り分けるために使う。
     */
    private fun readSystemInterfaces(): List<SystemInterface> = runCatching {
        NetworkInterface.getNetworkInterfaces()?.toList().orEmpty().map { nic ->
            SystemInterface(
                name = nic.name,
                displayName = runCatching { nic.displayName }.getOrNull(),
                isUp = runCatching { nic.isUp }.getOrDefault(false),
                isLoopback = runCatching { nic.isLoopback }.getOrDefault(false),
                isVirtual = runCatching { nic.isVirtual }.getOrDefault(false),
                addresses = nic.interfaceAddresses.mapNotNull { address ->
                    val host = address.address?.hostAddress ?: return@mapNotNull null
                    InterfaceAddress(host, address.networkPrefixLength.toInt())
                },
                mtu = runCatching { nic.mtu }.getOrNull()?.takeIf { it > 0 },
            )
        }
    }.getOrElse {
        PdtLog.w(TAG, "インターフェース一覧を取得できませんでした", it)
        emptyList()
    }

    /**
     * 指定アドレスが Ethernet と同一サブネットにあるか。
     *
     * 直結時に「相手の IP がこちらのサブネット外」という設定間違いを診断で指摘できるようにする。
     */
    fun isInEthernetSubnet(targetAddress: String): Boolean? {
        val link = _status.value.ethernetLink ?: return null
        val target = runCatching { java.net.InetAddress.getByName(targetAddress) }.getOrNull() ?: return null
        if (target !is Inet4Address) return null

        return link.addresses.any { local ->
            val localAddress = runCatching { java.net.InetAddress.getByName(local.address) }.getOrNull()
            if (localAddress !is Inet4Address) return@any false
            LocalAddressSelector.sameSubnet(localAddress, target, local.prefixLength)
        }
    }

    /**
     * eth0 相当のインターフェースが持つ IPv4 アドレス一覧。
     *
     * `ConnectivityManager` が Ethernet を報告しない（テザリング扱いの）環境でも、
     * `NetworkInterface` 列挙から取得できる。[SocketBindStrategy.ETHERNET_INTERFACE_ADDRESS]
     * のバインド先を決めるために使う。
     */
    fun ethernetLikeLocalAddresses(): List<InterfaceAddress> = _status.value.systemInterfaces
        .filter { it.looksLikeEthernet }
        .flatMap { it.addresses }
        .filter { !it.address.contains(':') }

    /**
     * 指定ホストへ通信する際にソースアドレスへ固定すべき有線 I/F の IPv4。
     *
     * eth0 相当の候補のうち、宛先と同一サブネットのものを優先する。無ければ先頭を返す。
     * 有線 I/F がまったく無ければ null。
     */
    fun localAddressForTarget(targetHost: String): String? = LocalAddressSelector.selectForTarget(ethernetLikeLocalAddresses(), targetHost)

    private companion object {
        const val TAG = "EthernetMonitor"
    }
}
