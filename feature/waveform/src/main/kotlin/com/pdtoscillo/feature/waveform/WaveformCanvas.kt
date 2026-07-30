package com.pdtoscillo.feature.waveform

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.sp
import com.pdtoscillo.core.common.EngineeringUnits
import com.pdtoscillo.core.ui.theme.TraceColors
import androidx.compose.foundation.Canvas as ComposeCanvas

/** 描画する 1 本のトレース。 */
data class TraceRenderData(
    val label: String,
    val color: Color,
    /** 表示用に間引き済みの時間。 */
    val times: DoubleArray,
    val minValues: DoubleArray,
    val maxValues: DoubleArray,
    val visible: Boolean,
) {
    override fun equals(other: Any?): Boolean = this === other

    override fun hashCode(): Int = System.identityHashCode(this)
}

/** 表示範囲。ズームとパンで変わる。 */
data class ViewWindow(val startTime: Double, val endTime: Double, val minVolts: Double, val maxVolts: Double) {
    val timeSpan: Double get() = endTime - startTime
    val voltSpan: Double get() = maxVolts - minVolts

    /** 中心を保ったまま時間軸を拡大縮小する。 */
    fun zoomTime(factor: Double, focusRatio: Double = 0.5): ViewWindow {
        val focus = startTime + timeSpan * focusRatio
        val newSpan = (timeSpan / factor).coerceIn(MIN_TIME_SPAN, MAX_TIME_SPAN)
        return copy(
            startTime = focus - newSpan * focusRatio,
            endTime = focus + newSpan * (1 - focusRatio),
        )
    }

    fun zoomVoltage(factor: Double): ViewWindow {
        val center = (minVolts + maxVolts) / 2
        val newSpan = (voltSpan / factor).coerceIn(MIN_VOLT_SPAN, MAX_VOLT_SPAN)
        return copy(minVolts = center - newSpan / 2, maxVolts = center + newSpan / 2)
    }

    fun pan(timeDelta: Double, voltDelta: Double): ViewWindow = copy(
        startTime = startTime + timeDelta,
        endTime = endTime + timeDelta,
        minVolts = minVolts + voltDelta,
        maxVolts = maxVolts + voltDelta,
    )

    companion object {
        private const val MIN_TIME_SPAN = 1.0e-12
        private const val MAX_TIME_SPAN = 1.0e3
        private const val MIN_VOLT_SPAN = 1.0e-6
        private const val MAX_VOLT_SPAN = 1.0e4

        fun fitting(traces: List<TraceRenderData>): ViewWindow {
            val visible = traces.filter { it.visible && it.times.isNotEmpty() }
            if (visible.isEmpty()) return ViewWindow(0.0, 1.0, -1.0, 1.0)

            val start = visible.minOf { it.times.first() }
            val end = visible.maxOf { it.times.last() }
            val minimum = visible.minOf { trace -> trace.minValues.minOrNull() ?: 0.0 }
            val maximum = visible.maxOf { trace -> trace.maxValues.maxOrNull() ?: 0.0 }
            // 上下に少し余白を入れる。ぴったりだと波形が枠に張り付いて読みにくい。
            val margin = ((maximum - minimum) * VERTICAL_MARGIN_RATIO).takeIf { it > 0 } ?: 1.0
            return ViewWindow(start, end, minimum - margin, maximum + margin)
        }

        private const val VERTICAL_MARGIN_RATIO = 0.1
    }
}

/** カーソル。時間軸 2 本と電圧軸 2 本。 */
data class CursorState(
    val verticalEnabled: Boolean = false,
    val horizontalEnabled: Boolean = false,
    val time1: Double = 0.0,
    val time2: Double = 0.0,
    val volts1: Double = 0.0,
    val volts2: Double = 0.0,
) {
    val deltaTime: Double get() = time2 - time1
    val deltaVolts: Double get() = volts2 - volts1

    /** Δt から周波数へ換算する。0 のときは null。 */
    val frequency: Double? get() = EngineeringUnits.deltaTimeToFrequency(deltaTime)
}

/**
 * 波形描画。
 *
 * 描画するのは間引き済みのデータだけ。元データは ViewModel が保持しており、
 * 保存と測定はそちらを使う。
 *
 * min / max の対を縦線で結ぶことで、1 ピクセルに複数点が入る場合でも
 * ピークやグリッチが消えないようにする。
 */
@Composable
fun WaveformCanvas(
    traces: List<TraceRenderData>,
    window: ViewWindow,
    cursors: CursorState,
    showGrid: Boolean,
    triggerTime: Double?,
    onTransform: (zoomX: Float, zoomY: Float, panX: Float, panY: Float) -> Unit,
    onCursorDrag: (index: Int, ratioX: Float, ratioY: Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val textMeasurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val labelStyle = remember {
        TextStyle(fontSize = 10.sp, color = TraceColors.cursor)
    }

    ComposeCanvas(
        modifier = modifier
            .fillMaxSize()
            .background(TraceColors.screenBackground)
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    onTransform(zoom, zoom, pan.x, pan.y)
                }
            }
            .pointerInput(cursors.verticalEnabled, cursors.horizontalEnabled) {
                if (!cursors.verticalEnabled && !cursors.horizontalEnabled) return@pointerInput
                detectDragGestures { change, _ ->
                    val ratioX = (change.position.x / size.width).coerceIn(0f, 1f)
                    val ratioY = (change.position.y / size.height).coerceIn(0f, 1f)
                    // 画面の左半分は 1 番目、右半分は 2 番目のカーソルを動かす。
                    val index = if (ratioX < HALF) 0 else 1
                    onCursorDrag(index, ratioX, ratioY)
                }
            },
    ) {
        if (showGrid) drawGraticule()
        triggerTime?.let { drawTriggerMarker(it, window) }
        traces.filter { it.visible }.forEach { trace -> drawTrace(trace, window) }
        drawCursors(cursors, window, textMeasurer, labelStyle)
        drawAxisLabels(window, textMeasurer, labelStyle)
    }
}

/** 目盛。横 10 div、縦 10 div は 4000 シリーズの画面と同じ。 */
private fun DrawScope.drawGraticule() {
    val divisions = 10
    val cellWidth = size.width / divisions
    val cellHeight = size.height / divisions

    for (index in 1 until divisions) {
        val x = cellWidth * index
        val y = cellHeight * index
        val isCenter = index == divisions / 2
        val color = if (isCenter) TraceColors.graticuleCenter else TraceColors.graticule
        val width = if (isCenter) 1.5f else 1f
        drawLine(color, Offset(x, 0f), Offset(x, size.height), strokeWidth = width)
        drawLine(color, Offset(0f, y), Offset(size.width, y), strokeWidth = width)
    }
    drawRect(
        color = TraceColors.graticule,
        style = Stroke(width = 1.5f),
    )
}

private fun DrawScope.drawTrace(trace: TraceRenderData, window: ViewWindow) {
    if (trace.times.isEmpty()) return
    val timeSpan = window.timeSpan
    val voltSpan = window.voltSpan
    if (timeSpan <= 0 || voltSpan <= 0) return

    fun toX(time: Double): Float = (((time - window.startTime) / timeSpan) * size.width).toFloat()
    fun toY(volts: Double): Float = (size.height - ((volts - window.minVolts) / voltSpan) * size.height).toFloat()

    val path = Path()
    var started = false

    for (index in trace.times.indices) {
        val x = toX(trace.times[index])
        // 画面外は描かない。ズーム時に無駄な描画を減らす。
        if (x < -OFF_SCREEN_MARGIN || x > size.width + OFF_SCREEN_MARGIN) continue

        val yMax = toY(trace.maxValues[index])
        val yMin = toY(trace.minValues[index])

        if (!started) {
            path.moveTo(x, yMax)
            started = true
        } else {
            path.lineTo(x, yMax)
        }
        // min と max を結ぶ縦線。これがないと 1 ピクセル内のピークが消える。
        if (yMin != yMax) path.lineTo(x, yMin)
    }

    if (started) {
        drawPath(path, color = trace.color, style = Stroke(width = TRACE_WIDTH))
    }
}

private fun DrawScope.drawTriggerMarker(triggerTime: Double, window: ViewWindow) {
    val span = window.timeSpan
    if (span <= 0) return
    val x = (((triggerTime - window.startTime) / span) * size.width).toFloat()
    if (x < 0 || x > size.width) return
    drawLine(
        color = TraceColors.triggerMarker,
        start = Offset(x, 0f),
        end = Offset(x, size.height),
        strokeWidth = 1.5f,
    )
}

private fun DrawScope.drawCursors(cursors: CursorState, window: ViewWindow, textMeasurer: TextMeasurer, style: TextStyle) {
    val timeSpan = window.timeSpan
    val voltSpan = window.voltSpan

    if (cursors.verticalEnabled && timeSpan > 0) {
        listOf(cursors.time1, cursors.time2).forEachIndexed { index, time ->
            val x = (((time - window.startTime) / timeSpan) * size.width).toFloat()
            if (x in 0f..size.width) {
                drawLine(TraceColors.cursor, Offset(x, 0f), Offset(x, size.height), strokeWidth = 1f)
                drawText(
                    textMeasurer = textMeasurer,
                    text = "V${index + 1}",
                    topLeft = Offset(x + 2f, 2f),
                    style = style,
                )
            }
        }
    }

    if (cursors.horizontalEnabled && voltSpan > 0) {
        listOf(cursors.volts1, cursors.volts2).forEachIndexed { index, volts ->
            val y = (size.height - ((volts - window.minVolts) / voltSpan) * size.height).toFloat()
            if (y in 0f..size.height) {
                drawLine(TraceColors.cursor, Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
                drawText(
                    textMeasurer = textMeasurer,
                    text = "H${index + 1}",
                    topLeft = Offset(2f, y + 2f),
                    style = style,
                )
            }
        }
    }
}

/** 画面の四隅へ現在の表示範囲を出す。単位付きで表示する。 */
private fun DrawScope.drawAxisLabels(window: ViewWindow, textMeasurer: TextMeasurer, style: TextStyle) {
    drawText(
        textMeasurer = textMeasurer,
        text = EngineeringUnits.formatToString(window.startTime, "s"),
        topLeft = Offset(4f, size.height - LABEL_BOTTOM_MARGIN),
        style = style,
    )
    val endLabel = EngineeringUnits.formatToString(window.endTime, "s")
    val endWidth = textMeasurer.measure(endLabel, style).size.width
    drawText(
        textMeasurer = textMeasurer,
        text = endLabel,
        topLeft = Offset(size.width - endWidth - 4f, size.height - LABEL_BOTTOM_MARGIN),
        style = style,
    )
    drawText(
        textMeasurer = textMeasurer,
        text = EngineeringUnits.formatToString(window.maxVolts, "V"),
        topLeft = Offset(4f, 4f),
        style = style,
    )
    drawText(
        textMeasurer = textMeasurer,
        text = EngineeringUnits.formatToString(window.minVolts, "V"),
        topLeft = Offset(4f, size.height - LABEL_BOTTOM_MARGIN * 2),
        style = style,
    )
}

private const val TRACE_WIDTH = 1.8f
private const val OFF_SCREEN_MARGIN = 4f
private const val LABEL_BOTTOM_MARGIN = 16f
private const val HALF = 0.5f
