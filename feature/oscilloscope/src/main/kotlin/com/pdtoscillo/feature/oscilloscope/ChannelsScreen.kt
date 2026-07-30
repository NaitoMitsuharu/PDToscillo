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
import com.pdtoscillo.core.model.BandwidthLimit
import com.pdtoscillo.core.model.ChannelCoupling
import com.pdtoscillo.core.model.ChannelSettings
import com.pdtoscillo.core.model.ChannelTermination
import com.pdtoscillo.core.ui.component.BusyIndicator
import com.pdtoscillo.core.ui.component.EngineeringValueField
import com.pdtoscillo.core.ui.component.ErrorCard
import com.pdtoscillo.core.ui.component.LabeledValue
import com.pdtoscillo.core.ui.component.SectionCard
import com.pdtoscillo.core.ui.component.StatusChip
import com.pdtoscillo.core.ui.component.UnavailableNotice
import com.pdtoscillo.core.ui.theme.MinTouchTarget
import com.pdtoscillo.core.ui.theme.TraceColors

/** UI テストからリストをスクロールするための目印。 */
const val CHANNELS_LIST_TAG = "channelsScreenList"

/**
 * チャンネル設定画面。
 *
 * 表示している値は常に「本体が受理した値」。入力欄へ打った値ではない。
 * 設定を送ったあとに読み戻し、丸められた場合はその値を表示する。
 */
@Composable
fun ChannelsScreen(viewModel: OscilloscopeViewModel, modifier: Modifier = Modifier) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LifecycleResumeEffect(Unit) {
        viewModel.onVisible()
        onPauseOrDispose { viewModel.onHidden() }
    }

    val channels = state.snapshot.channels
    var selectedChannel by remember { mutableStateOf(1) }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag(CHANNELS_LIST_TAG)
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

        if (state.readOnlyMode) {
            item {
                UnavailableNotice(
                    "読み取り専用モードです。値の確認のみできます。変更するには接続画面で解除してください。",
                )
            }
        }

        if (channels.isEmpty()) {
            item { UnavailableNotice("チャンネル情報を取得していません。「概要」画面で更新してください。") }
            return@LazyColumn
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                channels.forEach { channel ->
                    FilterChip(
                        selected = selectedChannel == channel.channel,
                        onClick = { selectedChannel = channel.channel },
                        label = { Text("CH${channel.channel}") },
                        modifier = Modifier.heightIn(min = MinTouchTarget),
                    )
                }
            }
        }

        val channel = channels.firstOrNull { it.channel == selectedChannel }
        if (channel == null) {
            item { UnavailableNotice("CH$selectedChannel の情報がありません。") }
            return@LazyColumn
        }

        item { ChannelDisplaySection(channel, state, viewModel) }
        item { VerticalSection(channel, state, viewModel) }
        item { InputSection(channel, state, viewModel) }
        item { ProbeSection(channel, state, viewModel) }

        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun ChannelDisplaySection(channel: ChannelSettings, state: OscilloscopeUiState, viewModel: OscilloscopeViewModel) {
    SectionCard(
        title = "CH${channel.channel}",
        trailing = {
            StatusChip(
                text = if (channel.displayed == true) "表示" else "非表示",
                color = TraceColors.forAnalogChannel(channel.channel),
            )
        },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("画面へ表示", modifier = Modifier.weight(1f))
            Switch(
                checked = channel.displayed == true,
                onCheckedChange = { viewModel.setChannelDisplay(channel.channel, it) },
                enabled = !state.readOnlyMode && !state.busy,
            )
        }

        var label by remember(channel.channel, channel.label) { mutableStateOf(channel.label.orEmpty()) }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            androidx.compose.material3.OutlinedTextField(
                value = label,
                onValueChange = { label = it },
                label = { Text("ラベル") },
                singleLine = true,
                enabled = !state.readOnlyMode,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.height(8.dp))
            TextButton(
                onClick = { viewModel.setLabel(channel.channel, label) },
                enabled = !state.readOnlyMode && !state.busy,
            ) { Text("適用") }
        }
    }
}

@Composable
private fun VerticalSection(channel: ChannelSettings, state: OscilloscopeUiState, viewModel: OscilloscopeViewModel) {
    SectionCard(title = "垂直軸") {
        LabeledValue(
            "現在の V/div",
            channel.verticalScale?.let { EngineeringUnits.formatToString(it, "V") } ?: "不明",
        )
        LabeledValue(
            "画面全体",
            channel.totalVoltageSpan?.let { EngineeringUnits.formatToString(it, "V") } ?: "不明",
        )

        var scaleText by remember(channel.channel, channel.verticalScale) {
            mutableStateOf(channel.verticalScale?.let { EngineeringUnits.format(it, "").value } ?: "")
        }
        Spacer(Modifier.height(8.dp))
        EngineeringValueField(
            label = "V/div",
            text = scaleText,
            unit = "V",
            onTextChange = { scaleText = it },
            range = MIN_VOLTS_PER_DIV..MAX_VOLTS_PER_DIV,
            enabled = !state.readOnlyMode,
        )
        ApplyButton(enabled = !state.readOnlyMode && !state.busy) {
            EngineeringUnits.parse(scaleText, "V")?.let { viewModel.setVerticalScale(channel.channel, it) }
        }

        Spacer(Modifier.height(12.dp))
        LabeledValue("垂直位置", channel.verticalPosition?.let { "$it div" } ?: "不明")
        var positionText by remember(channel.channel, channel.verticalPosition) {
            mutableStateOf(channel.verticalPosition?.toString() ?: "")
        }
        EngineeringValueField(
            label = "垂直位置",
            text = positionText,
            unit = "div",
            onTextChange = { positionText = it },
            range = MIN_POSITION_DIV..MAX_POSITION_DIV,
            enabled = !state.readOnlyMode,
        )
        ApplyButton(enabled = !state.readOnlyMode && !state.busy) {
            positionText.toDoubleOrNull()?.let { viewModel.setVerticalPosition(channel.channel, it) }
        }

        Spacer(Modifier.height(12.dp))
        LabeledValue("オフセット", channel.offset?.let { EngineeringUnits.formatToString(it, "V") } ?: "不明")
        var offsetText by remember(channel.channel, channel.offset) {
            mutableStateOf(channel.offset?.let { EngineeringUnits.format(it, "").value } ?: "")
        }
        EngineeringValueField(
            label = "オフセット",
            text = offsetText,
            unit = "V",
            onTextChange = { offsetText = it },
            enabled = !state.readOnlyMode,
        )
        ApplyButton(enabled = !state.readOnlyMode && !state.busy) {
            EngineeringUnits.parse(offsetText, "V")?.let { viewModel.setOffset(channel.channel, it) }
        }

        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("反転", modifier = Modifier.weight(1f))
            Switch(
                checked = channel.inverted == true,
                onCheckedChange = { viewModel.setInvert(channel.channel, it) },
                enabled = !state.readOnlyMode && !state.busy,
            )
        }
    }
}

@Composable
private fun InputSection(channel: ChannelSettings, state: OscilloscopeUiState, viewModel: OscilloscopeViewModel) {
    SectionCard(title = "入力") {
        Text("カップリング", style = MaterialTheme.typography.labelLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ChannelCoupling.entries.forEach { coupling ->
                FilterChip(
                    selected = channel.coupling == coupling,
                    onClick = { viewModel.setCoupling(channel.channel, coupling) },
                    label = { Text(coupling.displayName) },
                    enabled = !state.readOnlyMode && !state.busy,
                )
            }
        }

        Spacer(Modifier.height(12.dp))
        Text("帯域制限", style = MaterialTheme.typography.labelLarge)
        LabeledValue(
            "現在",
            when (val limit = channel.bandwidthLimit) {
                is BandwidthLimit.Full -> "Full"
                is BandwidthLimit.Hertz -> EngineeringUnits.formatToString(limit.value, "Hz")
                null -> "不明"
            },
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = channel.bandwidthLimit is BandwidthLimit.Full,
                onClick = {
                    viewModel.setBandwidthLimit(channel.channel, BandwidthLimit.Full)
                },
                label = { Text("Full") },
                enabled = !state.readOnlyMode && !state.busy,
            )
            COMMON_BANDWIDTH_LIMITS.forEach { hz ->
                FilterChip(
                    selected = (channel.bandwidthLimit as? BandwidthLimit.Hertz)?.value == hz,
                    onClick = { viewModel.setBandwidthLimit(channel.channel, BandwidthLimit.Hertz(hz)) },
                    label = { Text(EngineeringUnits.formatToString(hz, "Hz")) },
                    enabled = !state.readOnlyMode && !state.busy,
                )
            }
        }

        Spacer(Modifier.height(12.dp))
        Text("終端", style = MaterialTheme.typography.labelLarge)
        LabeledValue(
            "現在",
            channel.termination?.ohms?.let { EngineeringUnits.formatToString(it, "Ω") } ?: "不明",
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = channel.termination is ChannelTermination.FiftyOhm,
                onClick = { viewModel.setTermination(channel.channel, ChannelTermination.FiftyOhm) },
                label = { Text("50 Ω") },
                enabled = !state.readOnlyMode && !state.busy,
            )
            FilterChip(
                selected = channel.termination is ChannelTermination.OneMegaOhm,
                onClick = { viewModel.setTermination(channel.channel, ChannelTermination.OneMegaOhm) },
                label = { Text("1 MΩ") },
                enabled = !state.readOnlyMode && !state.busy,
            )
        }
    }
}

@Composable
private fun ProbeSection(channel: ChannelSettings, state: OscilloscopeUiState, viewModel: OscilloscopeViewModel) {
    SectionCard(title = "プローブ / 補正") {
        LabeledValue(
            "減衰比",
            channel.probeAttenuation?.let { "${EngineeringUnits.format(it, "").value} : 1" } ?: "不明",
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            COMMON_ATTENUATIONS.forEach { attenuation ->
                FilterChip(
                    selected = channel.probeAttenuation?.let {
                        kotlin.math.abs(it - attenuation) < ATTENUATION_TOLERANCE
                    } == true,
                    onClick = { viewModel.setProbeAttenuation(channel.channel, attenuation) },
                    label = { Text("${attenuation.toInt()}:1") },
                    enabled = !state.readOnlyMode && !state.busy,
                )
            }
        }

        Spacer(Modifier.height(12.dp))
        LabeledValue("Deskew", channel.deskew?.let { EngineeringUnits.formatToString(it, "s") } ?: "不明")
        var deskewText by remember(channel.channel, channel.deskew) {
            mutableStateOf(channel.deskew?.let { EngineeringUnits.format(it, "").value } ?: "")
        }
        EngineeringValueField(
            label = "Deskew",
            text = deskewText,
            unit = "s",
            onTextChange = { deskewText = it },
            enabled = !state.readOnlyMode,
        )
        ApplyButton(enabled = !state.readOnlyMode && !state.busy) {
            EngineeringUnits.parse(deskewText, "s")?.let { viewModel.setDeskew(channel.channel, it) }
        }
    }
}

@Composable
private fun ApplyButton(enabled: Boolean, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = MinTouchTarget),
    ) { Text("適用して読み戻す") }
}

private const val MIN_VOLTS_PER_DIV = 1.0e-3
private const val MAX_VOLTS_PER_DIV = 10.0
private const val MIN_POSITION_DIV = -5.0
private const val MAX_POSITION_DIV = 5.0
private const val ATTENUATION_TOLERANCE = 1.0e-6
private val COMMON_BANDWIDTH_LIMITS = listOf(20.0e6, 250.0e6)
private val COMMON_ATTENUATIONS = listOf(1.0, 10.0, 100.0)
