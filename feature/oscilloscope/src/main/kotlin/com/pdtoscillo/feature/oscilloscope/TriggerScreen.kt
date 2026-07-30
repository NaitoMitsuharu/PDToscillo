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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pdtoscillo.core.common.EngineeringUnits
import com.pdtoscillo.core.model.TriggerCoupling
import com.pdtoscillo.core.model.TriggerSettings
import com.pdtoscillo.core.model.TriggerSlope
import com.pdtoscillo.core.model.TriggerSweepMode
import com.pdtoscillo.core.model.TriggerType
import com.pdtoscillo.core.model.WaveformSource
import com.pdtoscillo.core.ui.component.BusyIndicator
import com.pdtoscillo.core.ui.component.EngineeringValueField
import com.pdtoscillo.core.ui.component.ErrorCard
import com.pdtoscillo.core.ui.component.LabeledValue
import com.pdtoscillo.core.ui.component.SectionCard
import com.pdtoscillo.core.ui.component.UnavailableNotice
import com.pdtoscillo.core.ui.theme.MinTouchTarget

/** UI テスト用の目印。 */
const val TRIGGER_LIST_TAG = "triggerScreenList"

/**
 * トリガ画面。
 *
 * トリガ種別ごとに設定項目がまったく違うため、1 つの巨大画面へ詰め込まず
 * 種別ごとに Composable を分けている。
 *
 * 表示するのは Capability が対応と判断した種別だけ。機種が持たない種別を出しても
 * 設定できないうえ、送れば未定義ヘッダーのエラーになる。
 */
@Composable
fun TriggerScreen(viewModel: OscilloscopeViewModel, modifier: Modifier = Modifier) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LifecycleResumeEffect(Unit) {
        viewModel.onVisible()
        onPauseOrDispose { viewModel.onHidden() }
    }

    val trigger = state.snapshot.trigger
    val supported = state.capabilities?.supportedTriggerTypes.orEmpty()

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag(TRIGGER_LIST_TAG)
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
            item { UnavailableNotice("読み取り専用モードです。変更するには接続画面で解除してください。") }
        }

        item { TriggerStatusSection(trigger) }

        item { TriggerTypeSection(trigger, supported, state, viewModel) }

        // 種別ごとに設定項目を分ける。
        val triggerType = trigger.type
        when (triggerType) {
            TriggerType.EDGE, null -> item { EdgeTriggerSection(trigger, state, viewModel) }
            TriggerType.PULSE_WIDTH -> item { PulseWidthTriggerSection() }
            TriggerType.RUNT -> item { RuntTriggerSection() }
            TriggerType.TRANSITION -> item { TransitionTriggerSection() }
            TriggerType.TIMEOUT -> item { TimeoutTriggerSection() }
            TriggerType.LOGIC, TriggerType.SETUP_HOLD -> item { LogicTriggerSection(triggerType) }
            TriggerType.BUS -> item { BusTriggerSection(state) }
            TriggerType.VIDEO -> item { VideoTriggerSection() }
        }

        item { HoldoffSection(trigger, state, viewModel) }

        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun TriggerStatusSection(trigger: TriggerSettings) {
    SectionCard(title = "状態") {
        LabeledValue("トリガ状態", trigger.runState?.displayName ?: "不明")
        LabeledValue("種類", trigger.type?.displayName ?: "不明")
        LabeledValue("ソース", trigger.edgeSource?.displayName ?: trigger.edgeSourceRaw ?: "不明")
        LabeledValue("レベル", trigger.level?.let { EngineeringUnits.formatToString(it, "V") } ?: "不明")
    }
}

@Composable
private fun TriggerTypeSection(
    trigger: TriggerSettings,
    supported: Set<TriggerType>,
    state: OscilloscopeUiState,
    viewModel: OscilloscopeViewModel,
) {
    SectionCard(title = "トリガ種別") {
        if (supported.isEmpty()) {
            UnavailableNotice("対応するトリガ種別を判定できていません。接続画面で機能検出を実行してください。")
            return@SectionCard
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            supported.sortedBy { it.displayName }.take(TYPE_CHIP_LIMIT).forEach { type ->
                FilterChip(
                    selected = trigger.type == type,
                    onClick = { viewModel.requestTriggerType(type) },
                    label = { Text(type.displayName) },
                    enabled = !state.readOnlyMode && !state.busy,
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Text("スイープモード", style = MaterialTheme.typography.labelLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TriggerSweepMode.entries.forEach { mode ->
                FilterChip(
                    selected = trigger.sweepMode == mode,
                    onClick = { viewModel.setTriggerSweepMode(mode) },
                    label = { Text(mode.displayName) },
                    enabled = !state.readOnlyMode && !state.busy,
                )
            }
        }
    }
}

/** エッジトリガ。最も使うため、この画面だけ完全な設定項目を持つ。 */
@Composable
private fun EdgeTriggerSection(trigger: TriggerSettings, state: OscilloscopeUiState, viewModel: OscilloscopeViewModel) {
    SectionCard(title = "エッジトリガ") {
        Text("ソース", style = MaterialTheme.typography.labelLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val channels = state.capabilities?.analogChannels ?: listOf(WaveformSource.CH1)
            channels.forEach { source ->
                FilterChip(
                    selected = trigger.edgeSource == source,
                    onClick = { viewModel.setTriggerSource(source) },
                    label = { Text(source.displayName) },
                    enabled = !state.readOnlyMode && !state.busy,
                )
            }
        }

        Spacer(Modifier.height(12.dp))
        Text("スロープ", style = MaterialTheme.typography.labelLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TriggerSlope.entries.forEach { slope ->
                FilterChip(
                    selected = trigger.slope == slope,
                    onClick = { viewModel.setTriggerSlope(slope) },
                    label = { Text(slope.displayName) },
                    enabled = !state.readOnlyMode && !state.busy,
                )
            }
        }

        Spacer(Modifier.height(12.dp))
        Text("カップリング", style = MaterialTheme.typography.labelLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TriggerCoupling.entries.forEach { coupling ->
                FilterChip(
                    selected = trigger.coupling == coupling,
                    onClick = { viewModel.setTriggerCoupling(coupling) },
                    label = { Text(coupling.displayName) },
                    enabled = !state.readOnlyMode && !state.busy,
                )
            }
        }

        Spacer(Modifier.height(12.dp))
        LabeledValue("現在のレベル", trigger.level?.let { EngineeringUnits.formatToString(it, "V") } ?: "不明")
        var levelText by remember(trigger.level) {
            mutableStateOf(trigger.level?.let { EngineeringUnits.format(it, "").value } ?: "")
        }
        EngineeringValueField(
            label = "トリガレベル",
            text = levelText,
            unit = "V",
            onTextChange = { levelText = it },
            enabled = !state.readOnlyMode,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(
                onClick = {
                    val channel = trigger.edgeSource
                        ?.takeIf { it.isAnalogChannel }
                        ?.let { WaveformSource.ANALOG_CHANNELS.indexOf(it) + 1 }
                        ?: 1
                    EngineeringUnits.parse(levelText, "V")?.let { viewModel.setTriggerLevel(channel, it) }
                },
                enabled = !state.readOnlyMode && !state.busy,
                modifier = Modifier.weight(1f).heightIn(min = MinTouchTarget),
            ) { Text("適用") }
            OutlinedButton(
                onClick = { viewModel.requestConfirmation(ConfirmableAction.SET_TRIGGER_LEVEL_50) },
                enabled = !state.readOnlyMode && !state.busy,
                modifier = Modifier.weight(1f).heightIn(min = MinTouchTarget),
            ) { Text("50% に合わせる") }
        }
    }
}

@Composable
private fun PulseWidthTriggerSection() = NotImplementedTriggerSection(
    title = "パルス幅トリガ",
    commands = listOf(
        "TRIGger:A:PULSEWidth:SOUrce",
        "TRIGger:A:PULSEWidth:WHEn",
        "TRIGger:A:PULSEWidth:LOWLimit",
        "TRIGger:A:PULSEWidth:HIGHLimit",
        "TRIGger:A:PULSEWidth:POLarity",
    ),
)

@Composable
private fun RuntTriggerSection() = NotImplementedTriggerSection(
    title = "ラントトリガ",
    commands = listOf(
        "TRIGger:A:RUNT:SOUrce",
        "TRIGger:A:RUNT:WHEn",
        "TRIGger:A:RUNT:WIDth",
        "TRIGger:A:RUNT:POLarity",
    ),
)

@Composable
private fun TransitionTriggerSection() = NotImplementedTriggerSection(
    title = "立ち上がり / 立ち下がりトリガ",
    commands = listOf("TRIGger:A:PULse:CLAss TRANsition", "TRIGger:A:TRANsition?"),
)

@Composable
private fun TimeoutTriggerSection() = NotImplementedTriggerSection(
    title = "タイムアウトトリガ",
    commands = listOf(
        "TRIGger:A:TIMEOut:SOUrce",
        "TRIGger:A:TIMEOut:TIMe",
        "TRIGger:A:TIMEOut:POLarity",
    ),
)

@Composable
private fun LogicTriggerSection(type: TriggerType) = NotImplementedTriggerSection(
    title = if (type == TriggerType.SETUP_HOLD) "セットアップ/ホールドトリガ" else "ロジックトリガ",
    commands = listOf("TRIGger:A:LOGIc:CLAss", "TRIGger:A:LOGIc?"),
)

@Composable
private fun VideoTriggerSection() = NotImplementedTriggerSection(
    title = "ビデオトリガ",
    commands = listOf(
        "TRIGger:A:VIDeo:SOUrce",
        "TRIGger:A:VIDeo:STANdard",
        "TRIGger:A:VIDeo:SYNC",
        "TRIGger:A:VIDeo:POLarity",
    ),
)

@Composable
private fun BusTriggerSection(state: OscilloscopeUiState) {
    val buses = state.capabilities?.supportedBusTypes.orEmpty()
    SectionCard(title = "バストリガ") {
        if (buses.isEmpty()) {
            UnavailableNotice("この機種ではバスデコードのオプションが確認できませんでした。")
            return@SectionCard
        }
        LabeledValue("搭載しているバス", buses.joinToString { it.displayName })
        Spacer(Modifier.height(8.dp))
        UnavailableNotice(
            "バストリガの詳細設定は未実装です。SCPI コンソールから " +
                "TRIGger:A:BUS と TRIGger:A:BUS:SOUrce で設定できます。",
        )
    }
}

/**
 * 未実装の種別。
 *
 * 「選べるのに何も起きない」状態を作らない。実装していないことと、
 * 代わりに使えるコマンドを示す。SCPI コンソールから同じ操作ができる。
 */
@Composable
private fun NotImplementedTriggerSection(title: String, commands: List<String>) {
    SectionCard(title = title) {
        UnavailableNotice(
            "この種別の詳細設定は未実装です。種別の切り替えは上のボタンで行えます。\n" +
                "詳細設定は SCPI コンソールから次のコマンドで行えます:\n" +
                commands.joinToString("\n") { "  $it" },
        )
    }
}

@Composable
private fun HoldoffSection(trigger: TriggerSettings, state: OscilloscopeUiState, viewModel: OscilloscopeViewModel) {
    SectionCard(title = "ホールドオフ") {
        LabeledValue(
            "現在値",
            trigger.holdoffTime?.let { EngineeringUnits.formatToString(it, "s") } ?: "不明",
        )
        var holdoffText by remember(trigger.holdoffTime) {
            mutableStateOf(trigger.holdoffTime?.let { EngineeringUnits.format(it, "").value } ?: "")
        }
        EngineeringValueField(
            label = "ホールドオフ時間",
            text = holdoffText,
            unit = "s",
            onTextChange = { holdoffText = it },
            enabled = !state.readOnlyMode,
        )
        OutlinedButton(
            onClick = {
                EngineeringUnits.parse(holdoffText, "s")?.let(viewModel::setTriggerHoldoff)
            },
            enabled = !state.readOnlyMode && !state.busy,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = MinTouchTarget),
        ) { Text("適用して読み戻す") }
    }
}

private const val TYPE_CHIP_LIMIT = 8
