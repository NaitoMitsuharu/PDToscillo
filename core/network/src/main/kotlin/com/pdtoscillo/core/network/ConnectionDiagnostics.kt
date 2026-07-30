package com.pdtoscillo.core.network

import com.pdtoscillo.core.common.EngineeringUnits
import com.pdtoscillo.core.model.ConnectionConfig
import com.pdtoscillo.core.model.InstrumentCapabilities
import com.pdtoscillo.core.model.InstrumentIdentity
import com.pdtoscillo.core.model.ScopeError
import com.pdtoscillo.core.model.SocketBindStrategy
import com.pdtoscillo.core.scpi.ScpiClient
import com.pdtoscillo.core.scpi.ScpiException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.InetSocketAddress
import java.net.Socket

/** 診断 1 項目の結果。 */
data class DiagnosticStep(
    val id: Id,
    val status: Status,
    val detail: String?,
    /** 失敗・警告時の対処方法。エラー内容と対処を同時に見せるために必須とする。 */
    val remedy: String?,
) {
    enum class Id {
        ETHERNET_DETECTED,
        LOCAL_ADDRESS,
        SUBNET_MATCH,
        TCP_PORT,
        ROUTE_VERIFIED,
        SCPI_RESPONSE,
        IDENTIFY,
        RESPONSE_TIME,
        MODEL_FAMILY,
        FIRMWARE,
        CAPABILITIES,
    }

    enum class Status { PASS, WARN, FAIL, SKIPPED }
}

/** 診断結果全体。 */
data class DiagnosticReport(
    val steps: List<DiagnosticStep>,
    val identity: InstrumentIdentity?,
    val capabilities: InstrumentCapabilities?,
    val networkStatus: NetworkStatus?,
    val lastError: ScopeError?,
) {
    val hasFailure: Boolean get() = steps.any { it.status == DiagnosticStep.Status.FAIL }
    val hasWarning: Boolean get() = steps.any { it.status == DiagnosticStep.Status.WARN }

    fun step(id: DiagnosticStep.Id): DiagnosticStep? = steps.firstOrNull { it.id == id }
}

/**
 * 接続診断。
 *
 * 「繋がらない」ときに、どこで止まっているのかを利用者が自分で切り分けられるようにする。
 * 各項目は失敗しても後続を止めず、可能なところまで進めて結果を並べる。
 */
class ConnectionDiagnostics(private val monitor: EthernetNetworkMonitor?, private val client: ScpiClient) {
    /**
     * 診断を実行する。
     *
     * @param onStep 1 項目終わるごとに呼ばれる。UI へ逐次表示するために使う。
     */
    @Suppress("LongMethod", "CyclomaticComplexMethod")
    suspend fun run(config: ConnectionConfig, onStep: (DiagnosticStep) -> Unit = {}): DiagnosticReport {
        val steps = mutableListOf<DiagnosticStep>()
        var lastError: ScopeError? = null

        fun record(step: DiagnosticStep) {
            steps += step
            onStep(step)
        }

        // 1. Ethernet の検出
        monitor?.refresh()
        val networkStatus = monitor?.status?.value
        record(ethernetStep(networkStatus, config))

        // 2. こちら側のアドレス
        record(localAddressStep(networkStatus))

        // 3. 同一サブネットか
        record(subnetStep(config))

        // 4. TCP ポートへ到達できるか
        val tcpResult = probeTcpPort(config)
        record(tcpResult.first)
        if (tcpResult.second != null) lastError = tcpResult.second

        val tcpReachable = tcpResult.first.status == DiagnosticStep.Status.PASS

        // 5. 経路の検証（実際に接続してから確認する）
        if (!tcpReachable) {
            record(skipped(DiagnosticStep.Id.ROUTE_VERIFIED, "TCP 接続ができていないため確認できません"))
            record(skipped(DiagnosticStep.Id.SCPI_RESPONSE, "TCP 接続ができていないため確認できません"))
            record(skipped(DiagnosticStep.Id.IDENTIFY, "TCP 接続ができていないため確認できません"))
            record(skipped(DiagnosticStep.Id.RESPONSE_TIME, "TCP 接続ができていないため確認できません"))
            record(skipped(DiagnosticStep.Id.MODEL_FAMILY, "TCP 接続ができていないため確認できません"))
            record(skipped(DiagnosticStep.Id.FIRMWARE, "TCP 接続ができていないため確認できません"))
            record(skipped(DiagnosticStep.Id.CAPABILITIES, "TCP 接続ができていないため確認できません"))
            return DiagnosticReport(steps, null, null, networkStatus, lastError)
        }

        if (!client.connectionState.value.isConnected) {
            val connectError = runCatching { client.connect(config) }.exceptionOrNull()
            if (connectError != null) {
                val mapped = (connectError as? ScpiException)?.error
                    ?: ScopeError.Unknown(connectError.message, connectError)
                lastError = mapped
                record(
                    DiagnosticStep(
                        DiagnosticStep.Id.ROUTE_VERIFIED,
                        DiagnosticStep.Status.FAIL,
                        mapped.detail,
                        remedyFor(mapped, config),
                    ),
                )
                return DiagnosticReport(steps, null, null, networkStatus, lastError)
            }
        }

        record(routeStep(config))

        // 6-7. SCPI 応答と識別
        val started = System.currentTimeMillis()
        var identity: InstrumentIdentity? = null
        val identifyError = runCatching { identity = client.identify() }.exceptionOrNull()
        val elapsed = System.currentTimeMillis() - started

        if (identifyError != null) {
            val mapped = (identifyError as? ScpiException)?.error
                ?: ScopeError.Unknown(identifyError.message, identifyError)
            lastError = mapped
            record(
                DiagnosticStep(
                    DiagnosticStep.Id.SCPI_RESPONSE,
                    DiagnosticStep.Status.FAIL,
                    mapped.detail,
                    remedyFor(mapped, config),
                ),
            )
            record(skipped(DiagnosticStep.Id.IDENTIFY, "SCPI 応答が得られませんでした"))
            record(skipped(DiagnosticStep.Id.RESPONSE_TIME, "SCPI 応答が得られませんでした"))
            record(skipped(DiagnosticStep.Id.MODEL_FAMILY, "SCPI 応答が得られませんでした"))
            record(skipped(DiagnosticStep.Id.FIRMWARE, "SCPI 応答が得られませんでした"))
            record(skipped(DiagnosticStep.Id.CAPABILITIES, "SCPI 応答が得られませんでした"))
            return DiagnosticReport(steps, null, null, networkStatus, lastError)
        }

        record(DiagnosticStep(DiagnosticStep.Id.SCPI_RESPONSE, DiagnosticStep.Status.PASS, "応答あり", null))
        record(identifyStep(identity))
        record(responseTimeStep(elapsed))

        // 8. Capability 検出
        val capabilities = runCatching { client.detectCapabilities() }.getOrNull()
        record(modelFamilyStep(identity, capabilities))
        record(firmwareStep(identity))
        record(capabilityStep(capabilities))

        return DiagnosticReport(steps, identity, capabilities, networkStatus, lastError)
    }

    private fun ethernetStep(status: NetworkStatus?, config: ConnectionConfig): DiagnosticStep = when {
        status == null -> DiagnosticStep(
            DiagnosticStep.Id.ETHERNET_DETECTED,
            DiagnosticStep.Status.SKIPPED,
            "この環境では Ethernet を確認できません",
            null,
        )

        status.ethernetAvailable -> {
            val others = status.activeTransports
                .filter { it != NetworkTransport.ETHERNET }
                .joinToString { transportLabel(it) }
            DiagnosticStep(
                DiagnosticStep.Id.ETHERNET_DETECTED,
                DiagnosticStep.Status.PASS,
                buildString {
                    append("検出")
                    status.ethernetLink?.interfaceName?.let { append("（$it）") }
                    if (others.isNotEmpty()) append(" / 他に有効: $others")
                },
                if (config.bindStrategy == SocketBindStrategy.SYSTEM_DEFAULT && others.isNotEmpty()) {
                    "他の経路も有効です。バインド方式を「Ethernet」にすると確実に有線側へ接続できます。"
                } else {
                    null
                },
            )
        }

        status.hasEthernetLikeInterfaceOnly -> DiagnosticStep(
            DiagnosticStep.Id.ETHERNET_DETECTED,
            DiagnosticStep.Status.WARN,
            "Android は Ethernet として報告していませんが、" +
                "それらしいインターフェースがあります: " +
                status.systemInterfaces.filter { it.looksLikeEthernet }.joinToString { it.name },
            "バインド方式を「システム既定」にして接続を試してください。" +
                "その場合はモバイル回線へ出ていないか経路検証の結果を確認してください。",
        )

        else -> DiagnosticStep(
            DiagnosticStep.Id.ETHERNET_DETECTED,
            DiagnosticStep.Status.FAIL,
            "検出されません" + if (status.hasCellular || status.hasWifi) {
                "（有効な経路: ${status.activeTransports.joinToString { transportLabel(it) }}）"
            } else {
                ""
            },
            "LAN ケーブルが両端で挿さっているか確認してください。" +
                "端末の設定にイーサネット項目があるかも確認してください。",
        )
    }

    private fun localAddressStep(status: NetworkStatus?): DiagnosticStep {
        val link = status?.ethernetLink
        val primary = link?.primaryIpv4
        return when {
            primary != null -> DiagnosticStep(
                DiagnosticStep.Id.LOCAL_ADDRESS,
                DiagnosticStep.Status.PASS,
                buildString {
                    append("IP: ${primary.address}")
                    primary.subnetMask?.let { append(" / マスク: $it") }
                    if (link.gateways.isNotEmpty()) append(" / GW: ${link.gateways.joinToString()}")
                    if (link.dnsServers.isNotEmpty()) append(" / DNS: ${link.dnsServers.joinToString()}")
                },
                null,
            )

            link != null -> DiagnosticStep(
                DiagnosticStep.Id.LOCAL_ADDRESS,
                DiagnosticStep.Status.FAIL,
                "Ethernet に IPv4 アドレスが割り当てられていません",
                "直結で DHCP が無い場合は静的 IP を設定してください（例: 192.168.10.1 / 255.255.255.0）。",
            )

            else -> skipped(DiagnosticStep.Id.LOCAL_ADDRESS, "Ethernet が検出されていません")
        }
    }

    private fun subnetStep(config: ConnectionConfig): DiagnosticStep = when (monitor?.isInEthernetSubnet(config.host)) {
        true -> DiagnosticStep(
            DiagnosticStep.Id.SUBNET_MATCH,
            DiagnosticStep.Status.PASS,
            "${config.host} は Ethernet と同じサブネットです",
            null,
        )

        false -> DiagnosticStep(
            DiagnosticStep.Id.SUBNET_MATCH,
            DiagnosticStep.Status.WARN,
            "${config.host} は Ethernet と別のサブネットです",
            "直結の場合は双方を同じサブネットに揃えてください" +
                "（例: 端末 192.168.10.1/24、オシロスコープ 192.168.10.2/24）。",
        )

        null -> skipped(DiagnosticStep.Id.SUBNET_MATCH, "判定に必要な情報が揃っていません")
    }

    /**
     * TCP ポートへ到達できるかを、SCPI を送らずに確認する。
     *
     * 候補機器へ接続してすぐ設定を変えないという方針のため、ここでは接続の可否だけを見る。
     */
    private suspend fun probeTcpPort(config: ConnectionConfig): Pair<DiagnosticStep, ScopeError?> = withContext(Dispatchers.IO) {
        val started = System.currentTimeMillis()
        try {
            Socket().use { socket ->
                socket.connect(
                    InetSocketAddress(config.host, config.port),
                    config.connectTimeoutMillis.toInt(),
                )
            }
            val elapsed = System.currentTimeMillis() - started
            DiagnosticStep(
                DiagnosticStep.Id.TCP_PORT,
                DiagnosticStep.Status.PASS,
                "${config.host}:${config.port} へ接続できました（$elapsed ms）",
                null,
            ) to null
        } catch (error: Exception) {
            val mapped = when (error) {
                is java.net.SocketTimeoutException ->
                    ScopeError.ConnectTimeout(config.host, config.port, config.connectTimeoutMillis)

                is java.net.ConnectException -> ScopeError.ConnectionRefused(config.host, config.port, error)
                else -> ScopeError.Unreachable(config.host, error)
            }
            DiagnosticStep(
                DiagnosticStep.Id.TCP_PORT,
                DiagnosticStep.Status.FAIL,
                mapped.detail,
                remedyFor(mapped, config),
            ) to mapped
        }
    }

    private fun routeStep(config: ConnectionConfig): DiagnosticStep {
        val route = client.routeInfo.value
        return when {
            route == null -> skipped(DiagnosticStep.Id.ROUTE_VERIFIED, "経路情報が取得できませんでした")

            route.boundToEthernet -> DiagnosticStep(
                DiagnosticStep.Id.ROUTE_VERIFIED,
                DiagnosticStep.Status.PASS,
                "Ethernet 経由（${route.localAddress} → ${route.remoteAddress}:${route.remotePort}）",
                null,
            )

            else -> DiagnosticStep(
                DiagnosticStep.Id.ROUTE_VERIFIED,
                DiagnosticStep.Status.WARN,
                route.warning ?: "Ethernet 経由であることを確認できませんでした",
                if (config.bindStrategy == SocketBindStrategy.SYSTEM_DEFAULT) {
                    "バインド方式を「Ethernet」にして再接続してください。"
                } else {
                    "Wi-Fi とモバイル通信を切って再試行し、経路が変わるか確認してください。"
                },
            )
        }
    }

    private fun identifyStep(identity: InstrumentIdentity?): DiagnosticStep = when {
        identity == null -> skipped(DiagnosticStep.Id.IDENTIFY, "識別できませんでした")

        identity.model.isBlank() -> DiagnosticStep(
            DiagnosticStep.Id.IDENTIFY,
            DiagnosticStep.Status.WARN,
            "応答は得られましたがモデル名を取り出せません: ${identity.raw}",
            "SCPI コンソールから *IDN? を送り、応答形式を確認してください。",
        )

        !identity.isTektronix -> DiagnosticStep(
            DiagnosticStep.Id.IDENTIFY,
            DiagnosticStep.Status.WARN,
            identity.raw,
            "Tektronix 以外の機器に接続している可能性があります。IP アドレスを確認してください。",
        )

        else -> DiagnosticStep(DiagnosticStep.Id.IDENTIFY, DiagnosticStep.Status.PASS, identity.raw, null)
    }

    private fun responseTimeStep(elapsedMillis: Long): DiagnosticStep = DiagnosticStep(
        DiagnosticStep.Id.RESPONSE_TIME,
        if (elapsedMillis > SLOW_RESPONSE_MILLIS) DiagnosticStep.Status.WARN else DiagnosticStep.Status.PASS,
        EngineeringUnits.formatToString(elapsedMillis / 1000.0, "s"),
        if (elapsedMillis > SLOW_RESPONSE_MILLIS) {
            "応答が遅いです。連続取得の周期を長めに設定してください。"
        } else {
            null
        },
    )

    private fun modelFamilyStep(identity: InstrumentIdentity?, capabilities: InstrumentCapabilities?): DiagnosticStep = when {
        capabilities == null -> skipped(DiagnosticStep.Id.MODEL_FAMILY, "機能検出ができませんでした")

        capabilities.family == com.pdtoscillo.core.model.ModelFamily.UNSUPPORTED -> DiagnosticStep(
            DiagnosticStep.Id.MODEL_FAMILY,
            DiagnosticStep.Status.WARN,
            "4000 シリーズとして認識できません: ${identity?.model}",
            "SCPI コンソールは使用できますが、専用画面の一部は無効になります。",
        )

        capabilities.family == com.pdtoscillo.core.model.ModelFamily.UNKNOWN_4000 -> DiagnosticStep(
            DiagnosticStep.Id.MODEL_FAMILY,
            DiagnosticStep.Status.WARN,
            "世代を特定できませんでした: ${identity?.model}",
            "docs/compatibility-matrix.md へ *IDN? の応答を記録してください。",
        )

        else -> DiagnosticStep(
            DiagnosticStep.Id.MODEL_FAMILY,
            DiagnosticStep.Status.PASS,
            "${capabilities.model}（${capabilities.family}）",
            null,
        )
    }

    private fun firmwareStep(identity: InstrumentIdentity?): DiagnosticStep {
        val firmware = identity?.firmwareVersion
        return if (firmware.isNullOrBlank()) {
            DiagnosticStep(
                DiagnosticStep.Id.FIRMWARE,
                DiagnosticStep.Status.WARN,
                "ファームウェア情報が得られません",
                null,
            )
        } else {
            DiagnosticStep(DiagnosticStep.Id.FIRMWARE, DiagnosticStep.Status.PASS, firmware, null)
        }
    }

    private fun capabilityStep(capabilities: InstrumentCapabilities?): DiagnosticStep = when {
        capabilities == null -> skipped(DiagnosticStep.Id.CAPABILITIES, "機能検出ができませんでした")

        capabilities.undeterminedFeatures.isNotEmpty() -> DiagnosticStep(
            DiagnosticStep.Id.CAPABILITIES,
            DiagnosticStep.Status.WARN,
            "アナログ ${capabilities.analogChannelCount} ch / " +
                "デジタル ${capabilities.digitalChannelCount} ch / " +
                "検出方法: ${capabilities.detectionSource} / " +
                "不明: ${capabilities.undeterminedFeatures.joinToString()}",
            "判定できなかった機能は安全側に倒して無効化しています。" +
                "必要な操作は SCPI コンソールから実行できます。",
        )

        else -> DiagnosticStep(
            DiagnosticStep.Id.CAPABILITIES,
            DiagnosticStep.Status.PASS,
            "アナログ ${capabilities.analogChannelCount} ch / " +
                "デジタル ${capabilities.digitalChannelCount} ch / " +
                "検出方法: ${capabilities.detectionSource}",
            null,
        )
    }

    private fun skipped(id: DiagnosticStep.Id, reason: String) = DiagnosticStep(id, DiagnosticStep.Status.SKIPPED, reason, null)

    private fun transportLabel(transport: NetworkTransport): String = when (transport) {
        NetworkTransport.ETHERNET -> "Ethernet"
        NetworkTransport.WIFI -> "Wi-Fi"
        NetworkTransport.CELLULAR -> "モバイル通信"
        NetworkTransport.VPN -> "VPN"
        NetworkTransport.BLUETOOTH -> "Bluetooth"
        NetworkTransport.USB -> "USB"
        NetworkTransport.OTHER -> "その他"
    }

    companion object {
        private const val SLOW_RESPONSE_MILLIS = 500L

        /** エラー分類ごとの対処方法。UI ではエラー内容と並べて表示する。 */
        fun remedyFor(error: ScopeError, config: ConnectionConfig): String = when (error) {
            is ScopeError.EthernetUnavailable ->
                "LAN ケーブルの接続を確認してください。Ethernet を使わない場合はバインド方式を「システム既定」にしてください。"

            is ScopeError.BindFailed ->
                "バインド方式を切り替えて再試行してください（socketFactory ↔ bindSocket）。"

            is ScopeError.ConnectTimeout ->
                "IP アドレスが正しいか、双方が同じサブネットにあるかを確認してください。"

            is ScopeError.ConnectionRefused ->
                "オシロスコープの Utility → I/O → Socket Server を有効にし、" +
                    "ポート番号が ${config.port} と一致しているか確認してください。"

            is ScopeError.Unreachable ->
                "サブネットとケーブルを確認してください。静的 IP の設定内容も確認してください。"

            is ScopeError.Disconnected ->
                "ケーブルの接触を確認してください。他のアプリが同時に接続していないかも確認してください。"

            is ScopeError.ReadTimeout ->
                "Socket Server の Protocol が None になっているか確認してください。" +
                    "Terminal のままだと応答形式が異なります。"

            is ScopeError.MalformedResponse ->
                "Socket Server の Protocol を None に設定してください。"

            is ScopeError.StreamDesynchronized ->
                "いったん切断して再接続してください。"

            is ScopeError.UndefinedHeader ->
                "この機種はこのコマンドに対応していません。対応する画面は自動的に無効化されます。"

            is ScopeError.OptionNotInstalled ->
                "この機能はオプション搭載機のみで利用できます。"

            is ScopeError.InstrumentBusy ->
                "本体が処理中です。しばらく待ってから再試行してください。"

            is ScopeError.ReadOnlyModeRejected ->
                "設定変更を行うには、読み取り専用モードを解除してください。"

            is ScopeError.MalformedBinaryBlock ->
                "波形データの形式が想定と異なります。データ幅とエンコーディングの設定を確認してください。"

            is ScopeError.WaveformNotAvailable ->
                "対象のチャンネルを表示状態にしてから取得してください。"

            else -> "接続診断を実行して、どの段階で止まっているか確認してください。"
        }
    }
}
