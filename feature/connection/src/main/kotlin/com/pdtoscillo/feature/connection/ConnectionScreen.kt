package com.pdtoscillo.feature.connection

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pdtoscillo.core.common.EngineeringUnits
import com.pdtoscillo.core.model.ConnectionState
import com.pdtoscillo.core.model.LineTerminator
import com.pdtoscillo.core.model.SocketBindStrategy
import com.pdtoscillo.core.model.TransportType
import com.pdtoscillo.core.network.DiagnosticStep
import com.pdtoscillo.core.ui.component.BusyIndicator
import com.pdtoscillo.core.ui.component.ErrorCard
import com.pdtoscillo.core.ui.component.LabeledValue
import com.pdtoscillo.core.ui.component.SectionCard
import com.pdtoscillo.core.ui.component.StatusChip
import com.pdtoscillo.core.ui.component.UnavailableNotice
import com.pdtoscillo.core.ui.theme.MinTouchTarget

/** UI テストからリストをスクロールするための目印。 */
const val CONNECTION_LIST_TAG = "connectionScreenList"

/**
 * 接続画面。
 *
 * 最初にここへ来る。IP とポートを入れて接続し、うまくいかないときは診断で切り分ける。
 */
@Composable
fun ConnectionScreen(
    viewModel: ConnectionViewModel,
    onOpenEscope: (String) -> Unit,
    modifier: Modifier = Modifier,
    onShareLog: (java.io.File) -> Unit = {},
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    if (state.wizardVisible) {
        ConnectionWizardDialog(
            suggestedPort = state.portInput,
            onDismiss = { viewModel.setWizardVisible(false) },
        )
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag(CONNECTION_LIST_TAG)
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { Spacer(Modifier.height(4.dp)) }

        item {
            ConnectionStatusHeader(
                state = state.connectionState,
                readOnlyMode = state.readOnlyMode,
                onReadOnlyChange = viewModel::setReadOnlyMode,
            )
        }

        item {
            BusyIndicator(visible = state.busy, label = state.busyLabel)
        }

        state.error?.let { error ->
            item {
                ErrorCard(
                    message = error.detail ?: error::class.simpleName.orEmpty(),
                    remedy = state.errorRemedy,
                    action = {
                        Row {
                            TextButton(onClick = viewModel::clearError) { Text("閉じる") }
                            if (error.isRetryable) {
                                TextButton(onClick = viewModel::connect) { Text("再試行") }
                            }
                        }
                    },
                )
            }
        }

        item {
            SectionCard(
                title = "接続先",
                trailing = {
                    TextButton(onClick = { viewModel.setWizardVisible(true) }) { Text("初期設定の手順") }
                },
            ) {
                OutlinedTextField(
                    value = state.hostInput,
                    onValueChange = viewModel::onHostChange,
                    label = { Text("IP アドレス") },
                    placeholder = { Text("192.168.10.2") },
                    isError = state.hostError,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (state.hostError) {
                    Text(
                        text = "IP アドレスまたはホスト名の形式が正しくありません。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = state.portInput,
                    onValueChange = viewModel::onPortChange,
                    label = { Text("ポート") },
                    supportingText = { Text("Tektronix Socket Server の初期候補は 4000") },
                    isError = state.portError,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        item {
            SectionCard(title = "通信方式") {
                Text("Transport", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TransportType.entries.forEach { type ->
                        FilterChip(
                            selected = state.transportType == type,
                            onClick = { viewModel.onTransportChange(type) },
                            label = { Text(transportLabel(type)) },
                            enabled = type == TransportType.RAW_SOCKET,
                        )
                    }
                }
                if (state.transportType == TransportType.VXI11) {
                    UnavailableNotice(
                        "VXI-11 は未実装です。ONC RPC の自前実装が必要なため、まず Raw Socket を完成させています。",
                    )
                }

                Spacer(Modifier.height(12.dp))
                Text("ソケットのバインド先", style = MaterialTheme.typography.labelLarge)
                Text(
                    text = "LAN 直結ではインターネット到達性が無いため、Ethernet は既定ルートに選ばれません。" +
                        "バインドしないとモバイル回線へ出てしまいます。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(4.dp))
                Column {
                    SocketBindStrategy.entries.forEach { strategy ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.heightIn(min = MinTouchTarget),
                        ) {
                            FilterChip(
                                selected = state.bindStrategy == strategy,
                                onClick = { viewModel.onBindStrategyChange(strategy) },
                                label = { Text(bindStrategyLabel(strategy)) },
                            )
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))
                Text("コマンド終端", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    LineTerminator.entries.forEach { terminator ->
                        FilterChip(
                            selected = state.terminator == terminator,
                            onClick = { viewModel.onTerminatorChange(terminator) },
                            label = { Text(terminator.name) },
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("自動再接続", modifier = Modifier.weight(1f))
                    Switch(
                        checked = state.autoReconnect,
                        onCheckedChange = viewModel::onAutoReconnectChange,
                    )
                }
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                Button(
                    onClick = viewModel::connect,
                    enabled = state.canConnect && !state.connectionState.isConnected,
                    modifier = Modifier.weight(1f).heightIn(min = MinTouchTarget),
                ) { Text("接続") }
                OutlinedButton(
                    onClick = viewModel::disconnect,
                    enabled = state.connectionState.isConnected,
                    modifier = Modifier.weight(1f).heightIn(min = MinTouchTarget),
                ) { Text("切断") }
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(
                    onClick = viewModel::runDiagnostics,
                    enabled = !state.busy,
                    modifier = Modifier.weight(1f).heightIn(min = MinTouchTarget),
                ) { Text("接続診断") }
                state.escopeUrl?.let { url ->
                    OutlinedButton(
                        onClick = { onOpenEscope(url) },
                        modifier = Modifier.weight(1f).heightIn(min = MinTouchTarget),
                    ) { Text("e*Scope を開く") }
                }
            }
        }

        item { SessionLogSection(state, viewModel, onShareLog) }

        item { EthernetInfoSection(state) }

        if (state.diagnosticSteps.isNotEmpty()) {
            item { DiagnosticsSection(state.diagnosticSteps) }
        }

        item { IdentitySection(state) }

        item { DiscoverySection(state, viewModel) }

        if (state.savedDevices.isNotEmpty()) {
            item {
                SectionCard(title = "保存済み機器") {
                    state.savedDevices.forEach { device ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth().heightIn(min = MinTouchTarget),
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(device.label, style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    "${device.host}:${device.port}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            TextButton(onClick = { viewModel.selectSaved(device) }) { Text("選択") }
                        }
                    }
                }
            }
        }

        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun ConnectionStatusHeader(state: ConnectionState, readOnlyMode: Boolean, onReadOnlyChange: (Boolean) -> Unit) {
    SectionCard(
        title = "状態",
        trailing = {
            val (label, color) = statusLabelAndColor(state)
            StatusChip(text = label, color = color)
        },
    ) {
        when (state) {
            is ConnectionState.Connected -> {
                LabeledValue("接続先", "${state.remoteAddress ?: "?"}:${state.config.port}")
                LabeledValue("こちらのアドレス", state.localAddress ?: "不明")
            }

            is ConnectionState.Reconnecting ->
                LabeledValue("再接続", "${state.attempt} / ${state.maxAttempts}")

            is ConnectionState.Failed ->
                LabeledValue("最後のエラー", state.error.detail ?: state.error::class.simpleName.orEmpty())

            else -> Unit
        }

        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text("読み取り専用モード", style = MaterialTheme.typography.bodyMedium)
                Text(
                    text = if (readOnlyMode) {
                        "設定変更コマンドを拒否します。接続直後は必ず有効です。"
                    } else {
                        "設定変更を許可しています。計測器の状態が変わります。"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(checked = readOnlyMode, onCheckedChange = onReadOnlyChange)
        }
    }
}

/**
 * セッションログの記録。
 *
 * 実機で最初に接続するときは、何が起きたかを後から追えることが重要になる。
 * 接続の前に記録を開始しておくと、接続のやり取りが最初から残る。
 */
@Composable
private fun SessionLogSection(state: ConnectionUiState, viewModel: ConnectionViewModel, onShareLog: (java.io.File) -> Unit) {
    val log = state.logState
    SectionCard(
        title = "セッションログ",
        trailing = {
            StatusChip(
                text = if (log.recording) "記録中" else "停止中",
                color = if (log.recording) Color(0xFFFF8A80) else Color(0xFFB0BEC5),
            )
        },
    ) {
        Text(
            text = "送受信した SCPI コマンドと応答、ネットワークの状態、診断結果を" +
                "端末内のファイルへ残します。実機へ最初に接続するときは、" +
                "**接続する前に**記録を開始してください。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        val fileName = log.fileName
        if (fileName != null) {
            Spacer(Modifier.height(8.dp))
            LabeledValue("ファイル", fileName)
            LabeledValue("サイズ", EngineeringUnits.formatBytes(log.sizeBytes))
            LabeledValue("記録した通信", log.entryCount.toString())
            if (log.truncated) {
                Spacer(Modifier.height(8.dp))
                UnavailableNotice("ログが上限に達したため記録を停止しました。")
            }
        }

        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            Button(
                onClick = viewModel::startLogging,
                enabled = !log.recording,
                modifier = Modifier.weight(1f).heightIn(min = MinTouchTarget),
            ) { Text("記録開始") }
            OutlinedButton(
                onClick = viewModel::stopLogging,
                enabled = log.recording,
                modifier = Modifier.weight(1f).heightIn(min = MinTouchTarget),
            ) { Text("停止") }
        }

        // 記録中でも取り出せる。長時間の記録の途中経過を確認したい場合に使う。
        val filePath = log.filePath
        if (filePath != null) {
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = { onShareLog(java.io.File(filePath)) },
                modifier = Modifier.fillMaxWidth().heightIn(min = MinTouchTarget),
            ) { Text("ログを送る / 保存する") }
            Text(
                text = "保存先: $filePath",
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun EthernetInfoSection(state: ConnectionUiState) {
    SectionCard(title = "Ethernet") {
        val status = state.networkStatus
        LabeledValue("検出", if (status.ethernetAvailable) "あり" else "なし")
        val link = status.ethernetLink
        if (link != null) {
            LabeledValue("インターフェース", link.interfaceName ?: "不明")
            link.primaryIpv4?.let { address ->
                LabeledValue("IP アドレス", address.address)
                LabeledValue("サブネットマスク", address.subnetMask ?: "不明")
            }
            LabeledValue("ゲートウェイ", link.gateways.joinToString().ifEmpty { "なし（直結では正常）" })
            LabeledValue("DNS", link.dnsServers.joinToString().ifEmpty { "なし" })
            link.mtu?.let { LabeledValue("MTU", it.toString()) }
        }
        LabeledValue(
            "有効な経路",
            status.activeTransports.joinToString { it.name }.ifEmpty { "不明" },
        )
        if (status.hasCellular) {
            Spacer(Modifier.height(8.dp))
            UnavailableNotice(
                "モバイル通信が有効です。バインド方式を Ethernet にしておくと、" +
                    "誤ってモバイル回線へ接続することを防げます。",
            )
        }
        if (status.hasEthernetLikeInterfaceOnly) {
            Spacer(Modifier.height(8.dp))
            UnavailableNotice(
                "Android は Ethernet として報告していませんが、それらしいインターフェースがあります: " +
                    status.systemInterfaces.filter { it.looksLikeEthernet }.joinToString { it.name } +
                    "。バインド方式を「システム既定」にして接続を試してください。",
            )
        }
    }
}

@Composable
private fun IdentitySection(state: ConnectionUiState) {
    val identity = state.identity ?: return
    SectionCard(title = "機器情報") {
        LabeledValue("メーカー", identity.manufacturer.ifBlank { "不明" })
        LabeledValue("モデル", identity.model.ifBlank { "不明" })
        LabeledValue("シリアル番号", identity.serialNumber ?: "不明")
        LabeledValue("ファームウェア", identity.firmwareVersion ?: "不明")
        Spacer(Modifier.height(8.dp))
        Text("*IDN? の生応答", style = MaterialTheme.typography.labelMedium)
        Text(
            text = identity.raw,
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
        )

        val capabilities = state.capabilities
        if (capabilities != null) {
            Spacer(Modifier.height(12.dp))
            LabeledValue("世代", capabilities.family.name)
            LabeledValue("アナログ CH", capabilities.analogChannelCount.toString())
            LabeledValue("デジタル CH", capabilities.digitalChannelCount.toString())
            LabeledValue("検出方法", capabilities.detectionSource.name)
            if (capabilities.undeterminedFeatures.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                UnavailableNotice(
                    "判定できなかった機能: ${capabilities.undeterminedFeatures.joinToString()}。" +
                        "安全側に倒して無効化しています。必要な操作は SCPI コンソールから実行できます。",
                )
            }
        }
    }
}

@Composable
private fun DiagnosticsSection(steps: List<DiagnosticStep>) {
    SectionCard(title = "接続診断") {
        steps.forEach { step ->
            Row(
                verticalAlignment = Alignment.Top,
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            ) {
                StatusChip(
                    text = statusMark(step.status),
                    color = statusColor(step.status),
                    showDot = false,
                )
                Spacer(Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(diagnosticLabel(step.id), style = MaterialTheme.typography.bodyMedium)
                    step.detail?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    step.remedy?.let {
                        Text(
                            text = "対処: $it",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.tertiary,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DiscoverySection(state: ConnectionUiState, viewModel: ConnectionViewModel) {
    SectionCard(
        title = "機器探索",
        trailing = {
            if (state.discovering) {
                TextButton(onClick = viewModel::stopDiscovery) { Text("停止") }
            } else {
                TextButton(onClick = viewModel::startDiscovery) { Text("探索") }
            }
        },
    ) {
        Text(
            text = "Ethernet と同じサブネット内のみを、上限を設けて探索します。" +
                "見つけた機器へは *IDN? だけを送り、設定は変更しません。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        state.discoveryProgress?.let { progress ->
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { progress.scanned.toFloat() / progress.total.coerceAtLeast(1) },
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                "${progress.scanned} / ${progress.total}",
                style = MaterialTheme.typography.bodySmall,
            )
        }
        state.discoveredDevices.forEach { device ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().heightIn(min = MinTouchTarget),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("${device.host}:${device.port}", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        text = device.identityRaw ?: "応答なし（ポートは開いています）",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (device.looksLikeTektronix) {
                    AssistChip(onClick = {}, label = { Text("Tektronix") })
                    Spacer(Modifier.width(8.dp))
                }
                TextButton(onClick = { viewModel.selectDiscovered(device) }) { Text("選択") }
            }
        }
    }
}

private fun statusLabelAndColor(state: ConnectionState): Pair<String, Color> = when (state) {
    is ConnectionState.Connected -> "接続中" to Color(0xFF69F0AE)
    is ConnectionState.Connecting -> "接続処理中" to Color(0xFFFFD180)
    is ConnectionState.Reconnecting -> "再接続中" to Color(0xFFFFD180)
    is ConnectionState.Failed -> "失敗" to Color(0xFFFF8A80)
    ConnectionState.Disconnected -> "未接続" to Color(0xFFB0BEC5)
}

private fun statusMark(status: DiagnosticStep.Status): String = when (status) {
    DiagnosticStep.Status.PASS -> "OK"
    DiagnosticStep.Status.WARN -> "注意"
    DiagnosticStep.Status.FAIL -> "失敗"
    DiagnosticStep.Status.SKIPPED -> "未実施"
}

private fun statusColor(status: DiagnosticStep.Status): Color = when (status) {
    DiagnosticStep.Status.PASS -> Color(0xFF69F0AE)
    DiagnosticStep.Status.WARN -> Color(0xFFFFD180)
    DiagnosticStep.Status.FAIL -> Color(0xFFFF8A80)
    DiagnosticStep.Status.SKIPPED -> Color(0xFFB0BEC5)
}

private fun diagnosticLabel(id: DiagnosticStep.Id): String = when (id) {
    DiagnosticStep.Id.ETHERNET_DETECTED -> "Ethernet の検出"
    DiagnosticStep.Id.LOCAL_ADDRESS -> "PDT-FP1 側のアドレス"
    DiagnosticStep.Id.SUBNET_MATCH -> "サブネットの一致"
    DiagnosticStep.Id.TCP_PORT -> "TCP ポートへの接続"
    DiagnosticStep.Id.ROUTE_VERIFIED -> "経路の検証"
    DiagnosticStep.Id.SCPI_RESPONSE -> "SCPI 応答"
    DiagnosticStep.Id.IDENTIFY -> "*IDN? の結果"
    DiagnosticStep.Id.RESPONSE_TIME -> "応答時間"
    DiagnosticStep.Id.MODEL_FAMILY -> "モデル判定"
    DiagnosticStep.Id.FIRMWARE -> "ファームウェア"
    DiagnosticStep.Id.CAPABILITIES -> "対応機能"
}

private fun transportLabel(type: TransportType): String = when (type) {
    TransportType.RAW_SOCKET -> "TCP Raw Socket"
    TransportType.VXI11 -> "VXI-11（未実装）"
}

private fun bindStrategyLabel(strategy: SocketBindStrategy): String = when (strategy) {
    SocketBindStrategy.ETHERNET_SOCKET_FACTORY -> "Ethernet（socketFactory）"
    SocketBindStrategy.ETHERNET_BIND_SOCKET -> "Ethernet（bindSocket）"
    SocketBindStrategy.SYSTEM_DEFAULT -> "システム既定（バインドなし）"
}
