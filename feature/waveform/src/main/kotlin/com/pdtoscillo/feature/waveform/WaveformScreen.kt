package com.pdtoscillo.feature.waveform

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pdtoscillo.core.common.EngineeringUnits
import com.pdtoscillo.core.ui.component.BusyIndicator
import com.pdtoscillo.core.ui.component.ErrorCard
import com.pdtoscillo.core.ui.component.LabeledValue
import com.pdtoscillo.core.ui.component.SectionCard
import com.pdtoscillo.core.ui.theme.MinTouchTarget

/** UI テストから参照するための目印。 */
const val WAVEFORM_CANVAS_TAG = "waveformCanvas"
const val WAVEFORM_CONTROLS_TAG = "waveformControls"

/**
 * 波形画面。
 *
 * 横画面では波形を大きく表示し、操作パネルを右側へ寄せる。
 * 縦画面では波形を上、操作を下に置く。
 */
@Composable
fun WaveformScreen(viewModel: WaveformViewModel, modifier: Modifier = Modifier) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val traces by viewModel.renderData.collectAsStateWithLifecycle()
    val configuration = LocalConfiguration.current
    val landscape = configuration.screenWidthDp > configuration.screenHeightDp

    LifecycleResumeEffect(Unit) {
        viewModel.onVisible()
        onPauseOrDispose { viewModel.onHidden() }
    }

    if (landscape) {
        Row(modifier = modifier.fillMaxSize()) {
            WaveformPlot(
                state = state,
                traces = traces,
                viewModel = viewModel,
                modifier = Modifier
                    .weight(LANDSCAPE_PLOT_WEIGHT)
                    .fillMaxHeight(),
            )
            ControlPanel(
                state = state,
                viewModel = viewModel,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp),
            )
        }
    } else {
        Column(modifier = modifier.fillMaxSize()) {
            WaveformPlot(
                state = state,
                traces = traces,
                viewModel = viewModel,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(PORTRAIT_PLOT_WEIGHT),
            )
            ControlPanel(
                state = state,
                viewModel = viewModel,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
            )
        }
    }
}

@Composable
private fun WaveformPlot(
    state: WaveformUiState,
    traces: List<TraceRenderData>,
    viewModel: WaveformViewModel,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .testTag(WAVEFORM_CANVAS_TAG)
            // 描画領域の横ピクセル数を間引きの目標値にする。
            // 画面より細かく描いても見えないうえ、描画が遅くなる。
            .onSizeChanged { viewModel.setCanvasWidth(it.width) },
    ) {
        val window = state.window
        if (window == null || traces.none { it.visible }) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = "波形がありません。「取得」を押してください。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            WaveformCanvas(
                traces = traces,
                window = window,
                cursors = state.cursors,
                showGrid = state.showGrid,
                triggerTime = TRIGGER_TIME_AT_ZERO,
                onTransform = viewModel::onTransform,
                onCursorDrag = viewModel::onCursorDrag,
            )
        }
    }
}

@Composable
private fun ControlPanel(state: WaveformUiState, viewModel: WaveformViewModel, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.testTag(WAVEFORM_CONTROLS_TAG),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Spacer(Modifier.height(4.dp))
        BusyIndicator(visible = state.busy, label = state.busyLabel)

        state.error?.let { error ->
            ErrorCard(
                message = error.detail ?: error::class.simpleName.orEmpty(),
                remedy = state.errorRemedy,
                action = { TextButton(onClick = viewModel::clearError) { Text("閉じる") } },
            )
        }

        state.notice?.let { notice ->
            SectionCard(
                title = "結果",
                trailing = { TextButton(onClick = viewModel::clearNotice) { Text("閉じる") } },
            ) {
                Text(notice, style = MaterialTheme.typography.bodySmall)
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            Button(
                onClick = viewModel::captureOnce,
                enabled = !state.busy,
                modifier = Modifier.weight(1f).heightIn(min = MinTouchTarget),
            ) { Text("取得") }
            OutlinedButton(
                onClick = viewModel::autoScale,
                modifier = Modifier.weight(1f).heightIn(min = MinTouchTarget),
            ) { Text("自動スケール") }
        }

        SectionCard(title = "チャンネル") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                state.traces.forEach { trace ->
                    FilterChip(
                        selected = trace.visible,
                        onClick = { viewModel.toggleTrace(trace.source) },
                        label = { Text(trace.source.displayName) },
                        leadingIcon = {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .background(
                                        com.pdtoscillo.core.ui.theme.TraceColors.forAnalogChannel(
                                            com.pdtoscillo.core.model.WaveformSource.ANALOG_CHANNELS
                                                .indexOf(trace.source) + 1,
                                        ),
                                        CircleShape,
                                    ),
                            )
                        },
                    )
                }
            }
        }

        SectionCard(title = "取得設定") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("連続取得", modifier = Modifier.weight(1f))
                Switch(checked = state.continuous, onCheckedChange = viewModel::setContinuous)
            }
            Text("取得周期", style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                WaveformUiState.INTERVALS.forEach { interval ->
                    FilterChip(
                        selected = state.intervalMillis == interval,
                        onClick = { viewModel.setInterval(interval) },
                        label = { Text(if (interval >= 1000) "${interval / 1000} s" else "$interval ms") },
                    )
                }
            }

            Spacer(Modifier.height(8.dp))
            Text("データ幅", style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(1, 2).forEach { bytes ->
                    FilterChip(
                        selected = state.bytesPerPoint == bytes,
                        onClick = { viewModel.setBytesPerPoint(bytes) },
                        label = { Text("$bytes バイト/点") },
                        enabled = !state.readOnlyMode,
                    )
                }
            }
            if (state.readOnlyMode) {
                Text(
                    text = "読み取り専用モードでは転送設定を変更できないため、本体の現在設定のまま取得します。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            state.lastCaptureMillis?.let { LabeledValue("取得時間", "$it ms") }
            state.throughputBytesPerSecond?.let {
                LabeledValue("スループット", "${EngineeringUnits.formatBytes(it.toLong())}/s")
            }
            state.visibleTraces.firstOrNull()?.waveform?.let { waveform ->
                LabeledValue("点数", waveform.pointCount.toString())
            }
        }

        SectionCard(title = "表示") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("グリッド", modifier = Modifier.weight(1f))
                Switch(checked = state.showGrid, onCheckedChange = viewModel::setShowGrid)
            }
            Text("ズーム", style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { viewModel.zoomTime(ZOOM_IN) }) { Text("時間 +") }
                OutlinedButton(onClick = { viewModel.zoomTime(ZOOM_OUT) }) { Text("時間 −") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { viewModel.zoomVoltage(ZOOM_IN) }) { Text("電圧 +") }
                OutlinedButton(onClick = { viewModel.zoomVoltage(ZOOM_OUT) }) { Text("電圧 −") }
            }
            Text(
                text = "画面上でピンチすると拡大縮小、ドラッグで移動できます。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        CursorSection(state, viewModel)

        SectionCard(title = "保存") {
            val source = state.visibleTraces.firstOrNull()?.source
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { source?.let(viewModel::exportCsv) },
                    enabled = source != null && !state.busy,
                ) { Text("CSV") }
                OutlinedButton(
                    onClick = { source?.let(viewModel::exportPng) },
                    enabled = source != null && !state.busy,
                ) { Text("PNG") }
                OutlinedButton(
                    onClick = { source?.let(viewModel::exportJson) },
                    enabled = source != null && !state.busy,
                ) { Text("JSON") }
            }
            state.exports.take(EXPORT_PREVIEW_COUNT).forEach { exported ->
                LabeledValue(exported.format.displayName, exported.file.name)
            }
        }

        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun CursorSection(state: WaveformUiState, viewModel: WaveformViewModel) {
    SectionCard(title = "カーソル") {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = state.cursors.verticalEnabled,
                onClick = {
                    viewModel.setCursorsEnabled(
                        vertical = !state.cursors.verticalEnabled,
                        horizontal = state.cursors.horizontalEnabled,
                    )
                },
                label = { Text("垂直バー") },
            )
            FilterChip(
                selected = state.cursors.horizontalEnabled,
                onClick = {
                    viewModel.setCursorsEnabled(
                        vertical = state.cursors.verticalEnabled,
                        horizontal = !state.cursors.horizontalEnabled,
                    )
                },
                label = { Text("水平バー") },
            )
        }

        if (state.cursors.verticalEnabled) {
            LabeledValue("Δt", EngineeringUnits.formatToString(state.cursors.deltaTime, "s"))
            LabeledValue(
                "1/Δt",
                state.cursors.frequency?.let { EngineeringUnits.formatToString(it, "Hz") } ?: "---",
            )
        }
        if (state.cursors.horizontalEnabled) {
            LabeledValue("ΔV", EngineeringUnits.formatToString(state.cursors.deltaVolts, "V"))
        }
        if (state.cursors.verticalEnabled || state.cursors.horizontalEnabled) {
            Text(
                text = "波形の左半分をドラッグすると 1 番目、右半分で 2 番目のカーソルが動きます。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private const val LANDSCAPE_PLOT_WEIGHT = 2.2f
private const val PORTRAIT_PLOT_WEIGHT = 1.3f
private const val ZOOM_IN = 1.5
private const val ZOOM_OUT = 1.0 / 1.5
private const val EXPORT_PREVIEW_COUNT = 3

/** トリガ位置は時間軸の 0 秒。プリアンブルの XZERO と PT_OFF がその基準になっている。 */
private const val TRIGGER_TIME_AT_ZERO = 0.0
