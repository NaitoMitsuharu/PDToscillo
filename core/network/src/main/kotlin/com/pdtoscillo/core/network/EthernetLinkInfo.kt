package com.pdtoscillo.core.network

/** ネットワークの種別。診断画面で「どの経路が有効か」を示す。 */
enum class NetworkTransport { ETHERNET, WIFI, CELLULAR, VPN, BLUETOOTH, USB, OTHER }

/** IP アドレスとプレフィックス長の組。 */
data class InterfaceAddress(val address: String, val prefixLength: Int) {
    /** プレフィックス長からサブネットマスクを組み立てる（IPv4 のみ）。 */
    val subnetMask: String?
        get() {
            if (address.contains(':')) return null
            if (prefixLength !in 0..IPV4_BITS) return null
            val mask = if (prefixLength == 0) 0L else (0xFFFFFFFFL shl (IPV4_BITS - prefixLength)) and 0xFFFFFFFFL
            return (0 until IPV4_OCTETS)
                .map { index -> (mask shr ((IPV4_OCTETS - 1 - index) * BITS_PER_OCTET)) and 0xFF }
                .joinToString(".")
        }

    override fun toString(): String = "$address/$prefixLength"

    private companion object {
        const val IPV4_BITS = 32
        const val IPV4_OCTETS = 4
        const val BITS_PER_OCTET = 8
    }
}

/**
 * Ethernet の接続情報。
 *
 * PDT-FP1 側の IP アドレス、サブネットマスク、ゲートウェイ、DNS を接続診断へ表示するために保持する。
 */
data class EthernetLinkInfo(
    val interfaceName: String?,
    val addresses: List<InterfaceAddress>,
    val gateways: List<String>,
    val dnsServers: List<String>,
    val mtu: Int?,
) {
    val primaryIpv4: InterfaceAddress?
        get() = addresses.firstOrNull { !it.address.contains(':') }

    /** LAN 直結ではゲートウェイが無いのが正常。判断材料として持つ。 */
    val hasDefaultGateway: Boolean get() = gateways.isNotEmpty()

    companion object {
        val EMPTY = EthernetLinkInfo(null, emptyList(), emptyList(), emptyList(), null)
    }
}

/**
 * ネットワーク全体の状態。
 *
 * Ethernet・Wi-Fi・モバイル回線が同時に有効になり得るため、Ethernet の有無だけでなく
 * 「他にどの経路が有効か」も持つ。モバイル回線への誤接続を利用者へ説明するために必要。
 */
data class NetworkStatus(
    val ethernetAvailable: Boolean,
    val ethernetLink: EthernetLinkInfo?,
    val activeTransports: Set<NetworkTransport>,
    /** `ConnectivityManager` が Ethernet を報告しない場合の予備情報。 */
    val systemInterfaces: List<SystemInterface>,
) {
    val hasCellular: Boolean get() = NetworkTransport.CELLULAR in activeTransports
    val hasWifi: Boolean get() = NetworkTransport.WIFI in activeTransports
    val hasVpn: Boolean get() = NetworkTransport.VPN in activeTransports

    /**
     * `ConnectivityManager` は Ethernet を報告していないが、OS のインターフェース一覧には
     * それらしいものがある状態。PDT-FP1 の LAN 端子で起こり得るため区別して扱う。
     */
    val hasEthernetLikeInterfaceOnly: Boolean
        get() = !ethernetAvailable && systemInterfaces.any { it.looksLikeEthernet }

    companion object {
        val UNKNOWN = NetworkStatus(
            ethernetAvailable = false,
            ethernetLink = null,
            activeTransports = emptySet(),
            systemInterfaces = emptyList(),
        )
    }
}

/** `NetworkInterface` から直接読んだ情報。`ConnectivityManager` の予備として使う。 */
data class SystemInterface(
    val name: String,
    val displayName: String?,
    val isUp: Boolean,
    val isLoopback: Boolean,
    val isVirtual: Boolean,
    val addresses: List<InterfaceAddress>,
    val mtu: Int?,
) {
    /**
     * Ethernet らしいインターフェース名か。
     *
     * Android では有線 LAN は `eth0` が一般的だが、USB-LAN アダプタでは `usb0` や `enp*` にもなる。
     */
    val looksLikeEthernet: Boolean
        get() = isUp &&
            !isLoopback &&
            ETHERNET_NAME_PREFIXES.any { name.startsWith(it, ignoreCase = true) }

    private companion object {
        val ETHERNET_NAME_PREFIXES = listOf("eth", "en", "usb", "rndis")
    }
}
