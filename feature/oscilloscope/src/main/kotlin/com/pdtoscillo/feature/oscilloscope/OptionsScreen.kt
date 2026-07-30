package com.pdtoscillo.feature.oscilloscope

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pdtoscillo.core.common.EngineeringUnits
import com.pdtoscillo.core.scpi.OptionControllers
import com.pdtoscillo.core.ui.component.BusyIndicator
import com.pdtoscillo.core.ui.component.EngineeringValueField
import com.pdtoscillo.core.ui.component.ErrorCard
import com.pdtoscillo.core.ui.component.LabeledValue
import com.pdtoscillo.core.ui.component.SectionCard
import com.pdtoscillo.core.ui.component.UnavailableNotice
import com.pdtoscillo.core.ui.theme.MinTouchTarget

/** UI テスト用の目印。 */
const val OPTIONS_LIST_TAG = "optionsScreenList"

/**
 * オプション機能画面（デジタル / スペクトラム / AFG / DVM / バス）。
 *
 * **搭載していない機能は表示しない。** 判定できなかった機能は理由を添えて無効化する。
 * 「選べるのに動かない」状態を作らない。
 */
@Composable
fun OptionsScreen(viewModel: OptionsViewModel, modifier: Modifier = Modifier) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LifecycleResumeEffect(Unit) {
        viewModel.onVisible()
        onPauseOrDispose { }
    }

    if (state.pendingAfgOutputEnable) {
        AlertDialog(
            onDismissRequest = viewModel::dismissAfgOutput,
            title = { Text("AFG 出力を有効にしますか？") },
            text = {
                Text(
                    "被測定回路へ実際に信号が出力されます。接続先と設定値を確認してください。\n" +
                        "現在の設定: ${state.afg.function ?: "?"} / " +
                        "${state.afg.frequency?.let { EngineeringUnits.formatToString(it, "Hz") } ?: "?"} / " +
                        "${state.afg.amplitude?.let { EngineeringUnits.formatToString(it, "V") } ?: "?"}",
                )
            },
            confirmButton = { TextButton(onClick = viewModel::confirmAfgOutput) { Text("出力する") } },
            dismissButton = { TextButton(onClick = viewModel::dismissAfgOutput) { Text("キャンセル") } },
        )
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag(OPTIONS_LIST_TAG)
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

        val capabilities = state.capabilities
        if (capabilities == null) {
            item { UnavailableNotice("機能検出がまだです。接続画面で接続してください。") }
            return@LazyColumn
        }

        if (!state.hasAnyOption) {
            item {
                UnavailableNotice(
                    "この機種ではオプション機能（デジタル / スペクトラム / AFG / DVM / バス）が" +
                        "確認できませんでした。判定できなかった項目: " +
                        capabilities.undeterminedFeatures.joinToString().ifEmpty { "なし" },
                )
            }
            return@LazyColumn
        }

        if (capabilities.hasDigitalChannels) item { DigitalSection(state, viewModel) }
        if (capabilities.hasBusDecode) item { BusSection(state, viewModel) }
        if (capabilities.hasSpectrumAnalyzer) item { SpectrumSection(state, viewModel) }
        if (capabilities.hasAfg) item { AfgSection(state, viewModel) }
        if (capabilities.hasDvm) item { DvmSection(state, viewModel) }

        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun DigitalSection(state: OptionsUiState, viewModel: OptionsViewModel) {
    SectionCard(title = "デジタルチャンネル") {
        if (state.digital.isEmpty()) {
            UnavailableNotice("デジタルチャンネルの状態を取得していません。")
            return@SectionCard
        }
        var selectedBit by remember { mutableStateOf(0) }

        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.horizontalScroll(rememberScrollState()),
        ) {
            state.digital.forEach { channel ->
                FilterChip(
                    selected = selectedBit == channel.bit,
                    onClick = { selectedBit = channel.bit },
                    label = { Text("D${channel.bit}") },
                )
            }
        }

        val channel = state.digital.firstOrNull { it.bit == selectedBit } ?: return@SectionCard
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("D${channel.bit} を表示", modifier = Modifier.weight(1f))
            Switch(
                checked = channel.displayed == true,
                onCheckedChange = { viewModel.setDigitalDisplay(channel.bit, it) },
                enabled = !state.readOnlyMode && !state.busy,
            )
        }
        LabeledValue(
            "しきい値",
            channel.threshold?.let { EngineeringUnits.formatToString(it, "V") } ?: "不明",
        )
        LabeledValue("ラベル", channel.label?.ifBlank { "（なし）" } ?: "不明")

        var thresholdText by remember(channel.bit, channel.threshold) {
            mutableStateOf(channel.threshold?.let { EngineeringUnits.format(it, "").value } ?: "")
        }
        EngineeringValueField(
            label = "しきい値",
            text = thresholdText,
            unit = "V",
            onTextChange = { thresholdText = it },
            enabled = !state.readOnlyMode,
        )
        OutlinedButton(
            onClick = {
                EngineeringUnits.parse(thresholdText, "V")?.let {
                    viewModel.setDigitalThreshold(channel.bit, it)
                }
            },
            enabled = !state.readOnlyMode && !state.busy,
            modifier = Modifier.fillMaxWidth().heightIn(min = MinTouchTarget),
        ) { Text("適用して読み戻す") }
    }
}

@Composable
private fun BusSection(state: OptionsUiState, viewModel: OptionsViewModel) {
    SectionCard(title = "バス") {
        val supported = state.capabilities?.supportedBusTypes.orEmpty()
        LabeledValue("搭載しているバス", supported.joinToString { it.displayName }.ifEmpty { "なし" })

        state.busTypes.forEach { (bus, type) ->
            Spacer(Modifier.height(8.dp))
            LabeledValue("B$bus の種別", type ?: "不明")
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.horizontalScroll(rememberScrollState()),
            ) {
                supported.forEach { busType ->
                    FilterChip(
                        selected = type?.startsWith(busType.scpiValue.take(3), ignoreCase = true) == true,
                        onClick = { viewModel.setBusType(bus, busType.scpiValue) },
                        label = { Text(busType.displayName) },
                        enabled = !state.readOnlyMode && !state.busy,
                    )
                }
            }
            OutlinedButton(
                onClick = { viewModel.setBusDisplay(bus, true) },
                enabled = !state.readOnlyMode && !state.busy,
                modifier = Modifier.fillMaxWidth().heightIn(min = MinTouchTarget),
            ) { Text("B$bus を表示") }
        }

        Spacer(Modifier.height(8.dp))
        UnavailableNotice(
            "バスごとの詳細設定（クロック源、ビットレート、しきい値など）は未実装です。" +
                "SCPI コンソールから BUS:B<x>:<種別>:... で設定できます。",
        )
    }
}

@Composable
private fun SpectrumSection(state: OptionsUiState, viewModel: OptionsViewModel) {
    SectionCard(title = "スペクトラム（RF）") {
        LabeledValue(
            "中心周波数",
            state.rf.centerFrequency?.let { EngineeringUnits.formatToString(it, "Hz") } ?: "不明",
        )
        LabeledValue("スパン", state.rf.span?.let { EngineeringUnits.formatToString(it, "Hz") } ?: "不明")
        LabeledValue(
            "開始 / 終了",
            "${state.rf.startFrequency?.let { EngineeringUnits.formatToString(it, "Hz") } ?: "?"} / " +
                (state.rf.stopFrequency?.let { EngineeringUnits.formatToString(it, "Hz") } ?: "?"),
        )
        LabeledValue(
            "RBW",
            state.rf.resolutionBandwidth?.let { EngineeringUnits.formatToString(it, "Hz") } ?: "不明",
        )
        LabeledValue(
            "基準レベル",
            state.rf.referenceLevel?.let { EngineeringUnits.formatToString(it, "dBm") } ?: "不明",
        )
        LabeledValue("窓関数", state.rf.window ?: "不明")

        Spacer(Modifier.height(8.dp))
        var centerText by remember(state.rf.centerFrequency) {
            mutableStateOf(state.rf.centerFrequency?.let { EngineeringUnits.format(it, "").value } ?: "")
        }
        EngineeringValueField(
            label = "中心周波数",
            text = centerText,
            unit = "Hz",
            onTextChange = { centerText = it },
            enabled = !state.readOnlyMode,
        )
        OutlinedButton(
            onClick = { EngineeringUnits.parse(centerText, "Hz")?.let(viewModel::setRfCenterFrequency) },
            enabled = !state.readOnlyMode && !state.busy,
            modifier = Modifier.fillMaxWidth().heightIn(min = MinTouchTarget),
        ) { Text("中心周波数を適用") }

        var spanText by remember(state.rf.span) {
            mutableStateOf(state.rf.span?.let { EngineeringUnits.format(it, "").value } ?: "")
        }
        EngineeringValueField(
            label = "スパン",
            text = spanText,
            unit = "Hz",
            onTextChange = { spanText = it },
            enabled = !state.readOnlyMode,
        )
        OutlinedButton(
            onClick = { EngineeringUnits.parse(spanText, "Hz")?.let(viewModel::setRfSpan) },
            enabled = !state.readOnlyMode && !state.busy,
            modifier = Modifier.fillMaxWidth().heightIn(min = MinTouchTarget),
        ) { Text("スパンを適用") }

        Spacer(Modifier.height(8.dp))
        UnavailableNotice(
            "スペクトラム波形の取得は「波形」画面から RF ソースを選ぶと行えます。" +
                "Spectrogram とマーカーは未実装です。",
        )
    }
}

@Composable
private fun AfgSection(state: OptionsUiState, viewModel: OptionsViewModel) {
    SectionCard(title = "AFG（任意波形/関数発生器）") {
        LabeledValue("波形", state.afg.function ?: "不明")
        LabeledValue(
            "周波数",
            state.afg.frequency?.let { EngineeringUnits.formatToString(it, "Hz") } ?: "不明",
        )
        LabeledValue("振幅", state.afg.amplitude?.let { EngineeringUnits.formatToString(it, "V") } ?: "不明")
        LabeledValue(
            "オフセット",
            state.afg.offset?.let { EngineeringUnits.formatToString(it, "V") } ?: "不明",
        )
        LabeledValue("デューティ", state.afg.dutyPercent?.let { "$it %" } ?: "不明")

        Spacer(Modifier.height(8.dp))
        Text("波形の種類", style = MaterialTheme.typography.labelLarge)
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.horizontalScroll(rememberScrollState()),
        ) {
            OptionControllers.AFG_FUNCTIONS.forEach { function ->
                FilterChip(
                    selected = state.afg.function?.startsWith(function.take(3), ignoreCase = true) == true,
                    onClick = { viewModel.setAfgFunction(function) },
                    label = { Text(function) },
                    enabled = !state.readOnlyMode && !state.busy,
                )
            }
        }

        Spacer(Modifier.height(8.dp))
        var frequencyText by remember(state.afg.frequency) {
            mutableStateOf(state.afg.frequency?.let { EngineeringUnits.format(it, "").value } ?: "")
        }
        EngineeringValueField(
            label = "周波数",
            text = frequencyText,
            unit = "Hz",
            onTextChange = { frequencyText = it },
            enabled = !state.readOnlyMode,
        )
        OutlinedButton(
            onClick = { EngineeringUnits.parse(frequencyText, "Hz")?.let(viewModel::setAfgFrequency) },
            enabled = !state.readOnlyMode && !state.busy,
            modifier = Modifier.fillMaxWidth().heightIn(min = MinTouchTarget),
        ) { Text("周波数を適用") }

        var amplitudeText by remember(state.afg.amplitude) {
            mutableStateOf(state.afg.amplitude?.let { EngineeringUnits.format(it, "").value } ?: "")
        }
        EngineeringValueField(
            label = "振幅",
            text = amplitudeText,
            unit = "V",
            onTextChange = { amplitudeText = it },
            enabled = !state.readOnlyMode,
        )
        OutlinedButton(
            onClick = { EngineeringUnits.parse(amplitudeText, "V")?.let(viewModel::setAfgAmplitude) },
            enabled = !state.readOnlyMode && !state.busy,
            modifier = Modifier.fillMaxWidth().heightIn(min = MinTouchTarget),
        ) { Text("振幅を適用") }

        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "出力",
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f),
            )
            Switch(
                checked = state.afg.outputEnabled == true,
                onCheckedChange = viewModel::requestAfgOutput,
                enabled = !state.readOnlyMode && !state.busy,
            )
        }
        Text(
            text = "出力を有効にすると被測定回路へ実際に信号が出ます。有効化の前に確認を求めます。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun DvmSection(state: OptionsUiState, viewModel: OptionsViewModel) {
    SectionCard(title = "DVM（デジタル電圧計）") {
        LabeledValue("モード", state.dvm.mode ?: "不明")
        LabeledValue("ソース", state.dvm.source ?: "不明")
        LabeledValue("測定値", state.dvm.value?.let { EngineeringUnits.formatToString(it, "V") } ?: "---")
        LabeledValue(
            "周波数",
            state.dvm.frequency?.let { EngineeringUnits.formatToString(it, "Hz") } ?: "---",
        )

        Spacer(Modifier.height(8.dp))
        Text("モード", style = MaterialTheme.typography.labelLarge)
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.horizontalScroll(rememberScrollState()),
        ) {
            OptionControllers.DVM_MODES.forEach { mode ->
                FilterChip(
                    selected = state.dvm.mode?.startsWith(mode.take(3), ignoreCase = true) == true,
                    onClick = { viewModel.setDvmMode(mode) },
                    label = { Text(mode) },
                    enabled = !state.readOnlyMode && !state.busy,
                )
            }
        }

        Spacer(Modifier.height(8.dp))
        Text("ソース", style = MaterialTheme.typography.labelLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            state.capabilities?.analogChannels.orEmpty().forEach { source ->
                FilterChip(
                    selected = state.dvm.source?.equals(source.scpiValue, ignoreCase = true) == true,
                    onClick = { viewModel.setDvmSource(source.scpiValue) },
                    label = { Text(source.displayName) },
                    enabled = !state.readOnlyMode && !state.busy,
                )
            }
        }
    }
}
