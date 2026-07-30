package com.pdtoscillo.feature.oscilloscope

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pdtoscillo.core.common.EngineeringUnits
import com.pdtoscillo.core.model.AcquisitionMode
import com.pdtoscillo.core.model.TriggerRunState
import com.pdtoscillo.core.ui.component.BusyIndicator
import com.pdtoscillo.core.ui.component.ErrorCard
import com.pdtoscillo.core.ui.component.LabeledValue
import com.pdtoscillo.core.ui.component.SectionCard
import com.pdtoscillo.core.ui.component.StatusChip
import com.pdtoscillo.core.ui.component.UnavailableNotice
import com.pdtoscillo.core.ui.theme.MinTouchTarget
import com.pdtoscillo.core.ui.theme.TraceColors

/** UI テストからリストをスクロールするための目印。 */
const val OVERVIEW_LIST_TAG = "overviewScreenList"

/**
 * オシロスコープ概要画面。
 *
 * 本体の現在の状態を 1 画面で把握し、Run / Stop / Single だけはここから直接操作できる。
 */
@Composable
fun OverviewScreen(viewModel: OscilloscopeViewModel, onOpenChannels: () -> Unit, modifier: Modifier = Modifier) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    // 画面が見えている間だけ取得する。バックグラウンドで本体との通信を占有しない。
    LifecycleResumeEffect(Unit) {
        viewModel.onVisible()
        onPauseOrDispose { viewModel.onHidden() }
    }

    state.pendingConfirmation?.let { action ->
        AlertDialog(
            onDismissRequest = viewModel::dismissConfirmation,
            title = { Text(action.title) },
            text = { Text(action.message) },
            confirmButton = {
                TextButton(onClick = viewModel::confirmPendingAction) { Text("実行") }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissConfirmation) { Text("キャンセル") }
            },
        )
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag(OVERVIEW_LIST_TAG)
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { Spacer(Modifier.height(4.dp)) }

        item { BusyIndicator(visible = state.busy, label = state.busyLabel) }

        state.error?.let { error ->
            item {
                ErrorCard(
                    message = error.detail ?: error::class.simpleName.orEmpty(),
                    remedy = state.errorRemedy,
                    action = { TextButton(onClick = viewModel::clearError) { Text("閉じる") } },
                )
            }
        }

        state.notice?.let { notice ->
            item {
                SectionCard(
                    title = "結果",
                    trailing = { TextButton(onClick = viewModel::clearNotice) { Text("閉じる") } },
                ) {
                    Text(notice, style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        item { InstrumentSection(state) }

        item { AcquisitionControlSection(state, viewModel) }

        item { HorizontalSection(state) }

        item { TriggerSection(state) }

        item { ChannelSummarySection(state, onOpenChannels) }

        item { RefreshSection(state, viewModel) }

        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun InstrumentSection(state: OscilloscopeUiState) {
    SectionCard(
        title = "機器",
        trailing = {
            StatusChip(
                text = if (state.readOnlyMode) "読み取り専用" else "設定変更可",
                color = if (state.readOnlyMode) Color(0xFFB0BEC5) else Color(0xFFFFD180),
            )
        },
    ) {
        LabeledValue("モデル", state.identity?.model?.ifBlank { "不明" } ?: "未接続")
        LabeledValue("シリアル番号", state.identity?.serialNumber ?: "不明")
        LabeledValue("ファームウェア", state.identity?.firmwareVersion ?: "不明")
        state.capabilities?.let { capabilities ->
            LabeledValue("世代", capabilities.family.name)
            LabeledValue(
                "チャンネル",
                "アナログ ${capabilities.analogChannelCount} / デジタル ${capabilities.digitalChannelCount}",
            )
        }
        state.lastResponseMillis?.let {
            LabeledValue("通信遅延", "$it ms")
        }
        if (state.snapshot.elapsedMillis > 0) {
            LabeledValue("一括取得にかかった時間", "${state.snapshot.elapsedMillis} ms")
        }
    }
}

@Composable
private fun AcquisitionControlSection(state: OscilloscopeUiState, viewModel: OscilloscopeViewModel) {
    val acquisition = state.snapshot.acquisition
    SectionCard(
        title = "Acquisition",
        trailing = {
            val running = acquisition.running
            StatusChip(
                text = when (running) {
                    true -> "Run"
                    false -> "Stop"
                    null -> "不明"
                },
                color = if (running == true) Color(0xFF69F0AE) else Color(0xFFB0BEC5),
            )
        },
    ) {
        LabeledValue("モード", acquisition.mode?.displayName ?: "不明")
        LabeledValue("停止条件", acquisition.stopAfter?.displayName ?: "不明")
        if (acquisition.mode?.usesAverageCount == true) {
            LabeledValue("Average 回数", acquisition.averageCount?.toString() ?: "不明")
        }
        LabeledValue("取得回数", acquisition.acquisitionCount?.toString() ?: "不明")

        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            Button(
                onClick = viewModel::run,
                enabled = !state.readOnlyMode && !state.busy,
                modifier = Modifier.weight(1f).heightIn(min = MinTouchTarget),
            ) { Text("Run") }
            OutlinedButton(
                onClick = viewModel::stop,
                enabled = !state.readOnlyMode && !state.busy,
                modifier = Modifier.weight(1f).heightIn(min = MinTouchTarget),
            ) { Text("Stop") }
            OutlinedButton(
                onClick = viewModel::single,
                enabled = !state.readOnlyMode && !state.busy,
                modifier = Modifier.weight(1f).heightIn(min = MinTouchTarget),
            ) { Text("Single") }
        }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(
            onClick = viewModel::forceTrigger,
            enabled = !state.readOnlyMode && !state.busy,
            modifier = Modifier.fillMaxWidth().heightIn(min = MinTouchTarget),
        ) { Text("Force Trigger") }

        if (state.readOnlyMode) {
            Spacer(Modifier.height(8.dp))
            UnavailableNotice("読み取り専用モードのため操作できません。接続画面で解除してください。")
        }

        Spacer(Modifier.height(12.dp))
        Text("モード切替", style = MaterialTheme.typography.labelLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AcquisitionMode.entries.take(MODE_CHIPS).forEach { mode ->
                FilterChip(
                    selected = acquisition.mode == mode,
                    onClick = { viewModel.setAcquisitionMode(mode) },
                    label = { Text(mode.displayName) },
                    enabled = !state.readOnlyMode && !state.busy,
                )
            }
        }
    }
}

@Composable
private fun HorizontalSection(state: OscilloscopeUiState) {
    val horizontal = state.snapshot.horizontal
    SectionCard(title = "水平軸") {
        LabeledValue(
            "時間軸",
            horizontal.scaleSecondsPerDivision?.let { "${EngineeringUnits.formatToString(it, "s")}/div" } ?: "不明",
        )
        LabeledValue(
            "画面全体",
            horizontal.totalTimeSpan?.let { EngineeringUnits.formatToString(it, "s") } ?: "不明",
        )
        LabeledValue("水平位置", horizontal.positionPercent?.let { "$it %" } ?: "不明")
        LabeledValue("レコード長", horizontal.recordLength?.toString() ?: "不明")
        LabeledValue(
            "サンプルレート",
            horizontal.sampleRate?.let { EngineeringUnits.formatToString(it, "S/s") } ?: "不明",
        )
    }
}

@Composable
private fun TriggerSection(state: OscilloscopeUiState) {
    val trigger = state.snapshot.trigger
    SectionCard(
        title = "トリガ",
        trailing = {
            StatusChip(
                text = trigger.runState?.displayName ?: "不明",
                color = when (trigger.runState) {
                    TriggerRunState.TRIGGERED -> Color(0xFF69F0AE)
                    TriggerRunState.READY, TriggerRunState.ARMED -> Color(0xFFFFD180)
                    TriggerRunState.AUTO -> Color(0xFF80D8FF)
                    else -> Color(0xFFB0BEC5)
                },
            )
        },
    ) {
        LabeledValue("種類", trigger.type?.displayName ?: "不明")
        LabeledValue("モード", trigger.sweepMode?.displayName ?: "不明")
        LabeledValue("ソース", trigger.edgeSource?.displayName ?: trigger.edgeSourceRaw ?: "不明")
        LabeledValue("スロープ", trigger.slope?.displayName ?: "不明")
        LabeledValue("カップリング", trigger.coupling?.displayName ?: "不明")
        LabeledValue("レベル", trigger.level?.let { EngineeringUnits.formatToString(it, "V") } ?: "不明")
        LabeledValue(
            "ホールドオフ",
            trigger.holdoffTime?.let { EngineeringUnits.formatToString(it, "s") } ?: "不明",
        )
        if (trigger.runState == TriggerRunState.UNKNOWN && trigger.runStateRaw != null) {
            Spacer(Modifier.height(8.dp))
            UnavailableNotice("トリガ状態の応答を解釈できませんでした: ${trigger.runStateRaw}")
        }
    }
}

@Composable
private fun ChannelSummarySection(state: OscilloscopeUiState, onOpenChannels: () -> Unit) {
    SectionCard(
        title = "チャンネル",
        trailing = { TextButton(onClick = onOpenChannels) { Text("設定を開く") } },
    ) {
        if (state.snapshot.channels.isEmpty()) {
            UnavailableNotice("チャンネル情報を取得していません。")
            return@SectionCard
        }
        state.snapshot.channels.forEach { channel ->
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                StatusChip(
                    text = "CH${channel.channel}",
                    color = TraceColors.forAnalogChannel(channel.channel),
                    showDot = channel.displayed == true,
                )
                Spacer(Modifier.height(4.dp))
                LabeledValue(
                    label = "",
                    value = buildString {
                        append(channel.verticalScale?.let { "${EngineeringUnits.formatToString(it, "V")}/div" } ?: "?")
                        append("  ")
                        append(channel.coupling?.displayName ?: "?")
                        channel.label?.takeIf { it.isNotBlank() }?.let { append("  \"$it\"") }
                        if (channel.displayed != true) append("  （非表示）")
                    },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun RefreshSection(state: OscilloscopeUiState, viewModel: OscilloscopeViewModel) {
    SectionCard(title = "自動更新") {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("画面が見えている間だけ更新する", modifier = Modifier.weight(1f))
            Switch(checked = state.autoRefresh, onCheckedChange = viewModel::setAutoRefresh)
        }
        Spacer(Modifier.height(8.dp))
        Text("更新周期", style = MaterialTheme.typography.labelLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            remember { OscilloscopeUiState.REFRESH_INTERVALS }.forEach { interval ->
                FilterChip(
                    selected = state.refreshIntervalMillis == interval,
                    onClick = { viewModel.setRefreshInterval(interval) },
                    label = { Text(if (interval >= 1000) "${interval / 1000} s" else "$interval ms") },
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(
            onClick = viewModel::refresh,
            enabled = !state.busy,
            modifier = Modifier.fillMaxWidth().heightIn(min = MinTouchTarget),
        ) { Text("今すぐ更新") }
    }
}

private const val MODE_CHIPS = 4
