package com.pdtoscillo.core.network

import com.pdtoscillo.core.common.PdtLog
import com.pdtoscillo.core.model.ConnectionConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.net.InetSocketAddress
import java.net.Socket

/** 探索で見つかった機器。 */
data class DiscoveredDevice(
    val host: String,
    val port: Int,
    /** `*IDN?` の応答。取得できなかった場合は null。 */
    val identityRaw: String?,
    val hasHttpPort: Boolean,
    val responseMillis: Long,
) {
    val looksLikeTektronix: Boolean
        get() = identityRaw?.contains("TEKTRONIX", ignoreCase = true) == true
}

/** 探索の進捗。 */
data class DiscoveryProgress(val scanned: Int, val total: Int, val currentHost: String?)

/**
 * 同一サブネット内の限定的な機器探索。
 *
 * **ネットワーク全体への無制限なポートスキャンは行わない。**
 * 探索対象は指定したサブネット内のホストに限り、ホスト数とタイムアウトに上限を設ける。
 * 利用者はいつでも中断できる（Flow の収集を止めれば全ての試行がキャンセルされる）。
 *
 * 見つけた機器に対してはまず安全な問い合わせ（`*IDN?`）だけを行い、設定を変更しない。
 */
class DeviceDiscovery {

    /**
     * サブネットを走査する。
     *
     * @param localAddress 自分の IPv4 アドレス。この /24 内を探索する。
     * @param scpiPort SCPI の待受ポート。既定は 4000。
     * @param maxHosts 走査するホスト数の上限。
     */
    fun scanSubnet(
        localAddress: String,
        scpiPort: Int = ConnectionConfig.DEFAULT_PORT,
        httpPort: Int = DEFAULT_HTTP_PORT,
        connectTimeoutMillis: Long = DEFAULT_CONNECT_TIMEOUT_MILLIS,
        identifyTimeoutMillis: Long = DEFAULT_IDENTIFY_TIMEOUT_MILLIS,
        maxHosts: Int = DEFAULT_MAX_HOSTS,
        concurrency: Int = DEFAULT_CONCURRENCY,
        onProgress: (DiscoveryProgress) -> Unit = {},
    ): Flow<DiscoveredDevice> = channelFlow {
        val hosts = enumerateHosts(localAddress, maxHosts)
        if (hosts.isEmpty()) {
            PdtLog.w(TAG, "探索対象のホストを決定できませんでした: $localAddress")
            return@channelFlow
        }

        val semaphore = Semaphore(concurrency)
        val scanned = Channel<Unit>(Channel.UNLIMITED)
        var completed = 0

        coroutineScope {
            launch {
                for (unit in scanned) {
                    completed += 1
                    onProgress(DiscoveryProgress(completed, hosts.size, null))
                    if (completed >= hosts.size) break
                }
            }

            hosts.forEach { host ->
                launch {
                    semaphore.withPermit {
                        val device = probeHost(
                            host = host,
                            scpiPort = scpiPort,
                            httpPort = httpPort,
                            connectTimeoutMillis = connectTimeoutMillis,
                            identifyTimeoutMillis = identifyTimeoutMillis,
                        )
                        if (device != null) send(device)
                    }
                    scanned.trySend(Unit)
                }
            }
        }
        scanned.close()
    }

    /** 1 台だけを確認する。IP を手入力した場合に使う。 */
    suspend fun probeHost(
        host: String,
        scpiPort: Int = ConnectionConfig.DEFAULT_PORT,
        httpPort: Int = DEFAULT_HTTP_PORT,
        connectTimeoutMillis: Long = DEFAULT_CONNECT_TIMEOUT_MILLIS,
        identifyTimeoutMillis: Long = DEFAULT_IDENTIFY_TIMEOUT_MILLIS,
    ): DiscoveredDevice? = withContext(Dispatchers.IO) {
        val started = System.currentTimeMillis()
        val identity = withTimeoutOrNull(connectTimeoutMillis + identifyTimeoutMillis) {
            queryIdentity(host, scpiPort, connectTimeoutMillis, identifyTimeoutMillis)
        }
        if (identity == null && !isPortOpen(host, scpiPort, connectTimeoutMillis)) return@withContext null

        val elapsed = System.currentTimeMillis() - started
        DiscoveredDevice(
            host = host,
            port = scpiPort,
            identityRaw = identity,
            hasHttpPort = isPortOpen(host, httpPort, connectTimeoutMillis),
            responseMillis = elapsed,
        )
    }

    /**
     * `*IDN?` だけを送って機器を確認する。
     *
     * 発見した機器へ接続してすぐ設定を変更しない、という方針に従う。
     */
    private fun queryIdentity(host: String, port: Int, connectTimeoutMillis: Long, readTimeoutMillis: Long): String? = runCatching {
        Socket().use { socket ->
            socket.connect(InetSocketAddress(host, port), connectTimeoutMillis.toInt())
            socket.soTimeout = readTimeoutMillis.toInt()
            socket.getOutputStream().apply {
                write("*IDN?\n".toByteArray(Charsets.US_ASCII))
                flush()
            }
            val input = socket.getInputStream()
            val buffer = StringBuilder()
            while (buffer.length < MAX_IDENTITY_LENGTH) {
                val value = input.read()
                if (value < 0 || value == LINE_FEED) break
                if (value != CARRIAGE_RETURN) buffer.append(value.toChar())
            }
            buffer.toString().trim().takeIf { it.isNotEmpty() }
        }
    }.getOrNull()

    private fun isPortOpen(host: String, port: Int, timeoutMillis: Long): Boolean = runCatching {
        Socket().use { socket ->
            socket.connect(InetSocketAddress(host, port), timeoutMillis.toInt())
            true
        }
    }.getOrDefault(false)

    /**
     * 自分のアドレスと同じ /24 内のホストを列挙する。
     * 上限を設け、ネットワーク全体を走査しないようにする。
     */
    internal fun enumerateHosts(localAddress: String, maxHosts: Int): List<String> {
        val octets = localAddress.split('.')
        if (octets.size != IPV4_OCTETS) return emptyList()
        val prefix = octets.take(IPV4_OCTETS - 1).joinToString(".")
        val self = octets.last().toIntOrNull() ?: return emptyList()

        // 自分に近いアドレスから調べる。直結では相手が隣接アドレスであることが多い。
        return (1..MAX_HOST_OCTET)
            .filter { it != self }
            .sortedBy { kotlin.math.abs(it - self) }
            .take(maxHosts)
            .map { "$prefix.$it" }
    }

    companion object {
        private const val TAG = "DeviceDiscovery"
        const val DEFAULT_HTTP_PORT = 80
        const val DEFAULT_CONNECT_TIMEOUT_MILLIS = 300L
        const val DEFAULT_IDENTIFY_TIMEOUT_MILLIS = 500L

        /** 既定の探索上限。/24 の全ホストを無条件に叩かないための制限。 */
        const val DEFAULT_MAX_HOSTS = 64
        const val DEFAULT_CONCURRENCY = 16

        private const val IPV4_OCTETS = 4
        private const val MAX_HOST_OCTET = 254
        private const val MAX_IDENTITY_LENGTH = 256
        private const val LINE_FEED = 0x0A
        private const val CARRIAGE_RETURN = 0x0D
    }
}
