package com.pdtoscillo.feature.connection

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
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
 * 接続画面（リデザイン版）。
 *
 * 上部: 接続状態・機器情報・Ethernet 状態
 * 中部: IP/ポート入力 + 接続/切断ボタン（常に見える）
 * 下部: 詳細設定（折りたたみ）+ 診断・ログ
 *
 * 初回表示時に自動探索 → 接続を試みる。
 */
@Composable
fun ConnectionScreen(
    viewModel: ConnectionViewModel,
    onOpenEscope: (String) -> Unit,
    modifier: Modifier = Modifier,
    onShareLog: (java.io.File) -> Unit = {},
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    // 画面が初めて表示されたときに自動探索・接続を試みる
    LaunchedEffect(Unit) {
        viewModel.triggerAutoConnect()
    }

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

        // ── 1. 状態バナー ──────────────────────────────────────────
        item { StatusBanner(state = state, viewModel = viewModel) }

        // ── 2. ビジー / エラー ────────────────────────────────────
        item { BusyIndicator(visible = state.busy, label = state.busyLabel) }

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

        // ── 3. 接続先入力 + ボタン ────────────────────────────────
        item { ConnectCard(state = state, viewModel = viewModel) }

        // ── 4. 自動探索の進行 / 結果 ─────────────────────────────
        if (state.discovering || state.discoveredDevices.isNotEmpty()) {
            item { DiscoverySection(state, viewModel) }
        }

        // ── 5. 機器情報（接続済みのとき） ────────────────────────
        state.identity?.let {
            item { IdentitySection(state) }
        }

        // ── 6. 詳細設定（折りたたみ） ─────────────────────────────
        item {
            ExpandableSettingsSection(state = state, viewModel = viewModel)
        }

        // ── 7. 折りたたみ内コンテンツ ────────────────────────────
        if (state.settingsExpanded) {
            item { EthernetInfoSection(state) }

            if (state.diagnosticSteps.isNotEmpty()) {
                item { DiagnosticsSection(state.diagnosticSteps) }
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
                        ) { Text("e*Scope") }
                    }
                }
            }

            item { SessionLogSection(state, viewModel, onShareLog) }

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
        }

        item { Spacer(Modifier.height(24.dp)) }
    }
}

// ─────────────────────────────────────────────────────────────
// 状態バナー: 接続状態 + 読み取り専用トグル + LAN 状態をコンパクトに
// ─────────────────────────────────────────────────────────────
@Composable
private fun StatusBanner(state: ConnectionUiState, viewModel: ConnectionViewModel) {
    val (label, color) = statusLabelAndColor(state.connectionState)
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                StatusChip(text = label, color = color)
                Spacer(Modifier.width(8.dp))
                // 機器名（接続済みのとき）
                val model = state.identity?.model?.takeIf { it.isNotBlank() }
                if (model != null) {
                    Text(
                        text = model,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f),
                    )
                } else {
                    Text(
                        text = when (state.connectionState) {
                            is ConnectionState.Connected -> "${state.connectionState.remoteAddress}:${state.connectionState.config.port}"
                            is ConnectionState.Connecting -> "接続処理中..."
                            is ConnectionState.Reconnecting -> "再接続中 ${state.connectionState.attempt}/${state.connectionState.maxAttempts}"
                            else -> "未接続"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                }
                // 読み取り専用トグル（ラベル省スペース）
                if (state.connectionState.isConnected) {
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = if (state.readOnlyMode) "読み取り専用" else "設定変更可",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (state.readOnlyMode) MaterialTheme.colorScheme.onSurfaceVariant
                            else Color(0xFFFFD180),
                        )
                        Switch(
                            checked = !state.readOnlyMode,
                            onCheckedChange = { viewModel.setReadOnlyMode(!it) },
                        )
                    }
                }
            }

            // LAN 状態をコンパクトに
            val link = state.networkStatus.ethernetLink
            val sysEth = state.networkStatus.systemInterfaces.firstOrNull { it.looksLikeEthernet }
            val ethIp = link?.primaryIpv4?.address
                ?: sysEth?.addresses?.firstOrNull { !it.address.contains(':') }?.address
            val ethName = link?.interfaceName ?: sysEth?.name ?: "—"
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val lanColor = if (ethIp != null) Color(0xFF69F0AE) else Color(0xFFB0BEC5)
                StatusChip(text = if (ethIp != null) "LAN: $ethIp" else "LAN 未接続", color = lanColor, showDot = false)
                if (ethIp != null) {
                    Text(
                        text = "($ethName)",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────
// 接続先カード: IP + ポート + 接続/切断
// ─────────────────────────────────────────────────────────────
@Composable
private fun ConnectCard(state: ConnectionUiState, viewModel: ConnectionViewModel) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "接続先",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = { viewModel.setWizardVisible(true) }) { Text("初期設定の手順") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = state.hostInput,
                    onValueChange = viewModel::onHostChange,
                    label = { Text("IP アドレス") },
                    placeholder = { Text("10.175.225.2") },
                    isError = state.hostError,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    modifier = Modifier.weight(3f),
                )
                OutlinedTextField(
                    value = state.portInput,
                    onValueChange = viewModel::onPortChange,
                    label = { Text("ポート") },
                    isError = state.portError,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(2f),
                )
            }
            if (state.hostError) {
                Text(
                    text = "IP アドレスの形式が正しくありません。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
    }
}

// ─────────────────────────────────────────────────────────────
// 詳細設定（折りたたみ）: 通信方式 / バインド先 / 終端文字 / 再接続
// ─────────────────────────────────────────────────────────────
@Composable
private fun ExpandableSettingsSection(state: ConnectionUiState, viewModel: ConnectionViewModel) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = MinTouchTarget),
            ) {
                Text(
                    text = "詳細設定",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = { viewModel.setSettingsExpanded(!state.settingsExpanded) }) {
                    Text(if (state.settingsExpanded) "▲ 閉じる" else "▼ 開く")
                }
            }

            AnimatedVisibility(visible = state.settingsExpanded) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Spacer(Modifier.height(4.dp))

                    // バインド方式
                    Text("ソケットのバインド先", style = MaterialTheme.typography.labelLarge)
                    Text(
                        text = "テザリングモードでは「システム既定」を使ってください。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SocketBindStrategy.entries.forEach { strategy ->
                            FilterChip(
                                selected = state.bindStrategy == strategy,
                                onClick = { viewModel.onBindStrategyChange(strategy) },
                                label = { Text(bindStrategyLabel(strategy)) },
                            )
                        }
                    }

                    // 終端文字
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

                    // 自動再接続
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("自動再接続", modifier = Modifier.weight(1f))
                        Switch(
                            checked = state.autoReconnect,
                            onCheckedChange = viewModel::onAutoReconnectChange,
                        )
                    }

                    // Transport（VXI-11 未実装を示すだけなので折りたたみ内へ）
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
                        UnavailableNotice("VXI-11 は未実装です。Raw Socket を選択してください。")
                    }

                    Spacer(Modifier.height(4.dp))
                }
            }
        }
    }
}

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
        val fileName = log.fileName
        if (fileName != null) {
            LabeledValue("ファイル", fileName)
            LabeledValue("サイズ", EngineeringUnits.formatBytes(log.sizeBytes))
            LabeledValue("記録した通信", log.entryCount.toString())
            if (log.truncated) {
                UnavailableNotice("ログが上限に達しました。")
            }
            Spacer(Modifier.height(8.dp))
        }
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
        val filePath = log.filePath
        if (filePath != null) {
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = { onShareLog(java.io.File(filePath)) },
                modifier = Modifier.fillMaxWidth().heightIn(min = MinTouchTarget),
            ) { Text("ログを送る / 保存する") }
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
        }
        LabeledValue(
            "有効な経路",
            status.activeTransports.joinToString { it.name }.ifEmpty { "不明" },
        )
        if (status.hasCellular) {
            UnavailableNotice("モバイル通信が有効です。テザリングモードでは「システム既定」を使ってください。")
        }
        if (status.hasEthernetLikeInterfaceOnly) {
            UnavailableNotice(
                "Android は Ethernet として報告していませんが、${
                    status.systemInterfaces.filter { it.looksLikeEthernet }.joinToString { it.name }
                } が見つかっています。バインド方式を「システム既定」にして接続を試してください。",
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
        val capabilities = state.capabilities
        if (capabilities != null) {
            Spacer(Modifier.height(8.dp))
            LabeledValue("アナログ CH", capabilities.analogChannelCount.toString())
            LabeledValue("デジタル CH", capabilities.digitalChannelCount.toString())
            if (capabilities.undeterminedFeatures.isNotEmpty()) {
                UnavailableNotice("未確定の機能: ${capabilities.undeterminedFeatures.joinToString()}")
            }
        }
        Spacer(Modifier.height(4.dp))
        Text("*IDN? の生応答", style = MaterialTheme.typography.labelSmall)
        Text(
            text = identity.raw,
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
        )
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
                StatusChip(text = statusMark(step.status), color = statusColor(step.status), showDot = false)
                Spacer(Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(diagnosticLabel(step.id), style = MaterialTheme.typography.bodyMedium)
                    step.detail?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    step.remedy?.let {
                        Text("対処: $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary)
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
            } else if (state.discoveredDevices.isEmpty()) {
                TextButton(onClick = viewModel::startDiscovery) { Text("再探索") }
            }
        },
    ) {
        state.discoveryProgress?.let { progress ->
            LinearProgressIndicator(
                progress = { progress.scanned.toFloat() / progress.total.coerceAtLeast(1) },
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                "探索中: ${progress.scanned} / ${progress.total}",
                style = MaterialTheme.typography.bodySmall,
            )
            Spacer(Modifier.height(8.dp))
        }
        if (state.discoveredDevices.isEmpty() && state.discovering) {
            Text(
                "Tektronix 機器を探しています...",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                        text = device.identityRaw ?: "応答なし",
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
    SocketBindStrategy.ETHERNET_SOCKET_FACTORY -> "Ethernet (socketFactory)"
    SocketBindStrategy.ETHERNET_BIND_SOCKET -> "Ethernet (bindSocket)"
    SocketBindStrategy.SYSTEM_DEFAULT -> "システム既定"
}
