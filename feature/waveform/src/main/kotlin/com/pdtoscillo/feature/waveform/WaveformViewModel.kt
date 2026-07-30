package com.pdtoscillo.feature.waveform

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.pdtoscillo.core.database.export.ExportFormat
import com.pdtoscillo.core.database.export.ExportedFile
import com.pdtoscillo.core.database.export.WaveformExporter
import com.pdtoscillo.core.model.ScopeError
import com.pdtoscillo.core.model.WaveformSource
import com.pdtoscillo.core.network.ConnectionDiagnostics
import com.pdtoscillo.core.network.InstrumentSession
import com.pdtoscillo.core.scpi.ScpiException
import com.pdtoscillo.core.scpi.WaveformTransfer
import com.pdtoscillo.core.scpi.WaveformTransferConfig
import com.pdtoscillo.core.ui.theme.TraceColors
import com.pdtoscillo.core.waveform.AnalogWaveform
import com.pdtoscillo.core.waveform.EnvelopeWaveform
import com.pdtoscillo.core.waveform.MinMaxDecimator
import com.pdtoscillo.core.waveform.SpectrumTrace
import com.pdtoscillo.core.waveform.Waveform
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** 1 チャンネル分の取得結果と表示設定。 */
data class ChannelTrace(
    val source: WaveformSource,
    val visible: Boolean,
    val waveform: Waveform?,
    val transferredBytes: Int = 0,
    val elapsedMillis: Long = 0,
    /** 生バイナリ。生データ保存に使う。 */
    val rawPayload: ByteArray? = null,
) {
    override fun equals(other: Any?): Boolean = this === other

    override fun hashCode(): Int = System.identityHashCode(this)
}

data class WaveformUiState(
    val traces: List<ChannelTrace> = emptyList(),
    val window: ViewWindow? = null,
    val cursors: CursorState = CursorState(),
    val showGrid: Boolean = true,
    val continuous: Boolean = false,
    val intervalMillis: Long = DEFAULT_INTERVAL_MILLIS,
    val bytesPerPoint: Int = 1,
    val busy: Boolean = false,
    val busyLabel: String = "",
    val error: ScopeError? = null,
    val errorRemedy: String? = null,
    val notice: String? = null,
    val readOnlyMode: Boolean = true,
    /** 実測のスループット。 */
    val throughputBytesPerSecond: Double? = null,
    val lastCaptureMillis: Long? = null,
    val exports: List<ExportedFile> = emptyList(),
    /** 描画に使う横ピクセル数。間引きの目標値。 */
    val canvasWidthPx: Int = DEFAULT_CANVAS_WIDTH,
) {
    val visibleTraces: List<ChannelTrace> get() = traces.filter { it.visible && it.waveform != null }

    companion object {
        const val DEFAULT_INTERVAL_MILLIS = 1_000L
        const val DEFAULT_CANVAS_WIDTH = 1080

        /** 現実的な取得周期。短すぎると本体の更新速度を超えて要求してしまう。 */
        val INTERVALS = listOf(200L, 500L, 1_000L, 2_000L, 5_000L)
    }
}

/**
 * 波形画面の ViewModel。
 *
 * 連続取得では次の点に注意している。
 * - 前回の取得が終わってから次の待ち時間を数える。一定周期で投げ続けると要求が積み上がる。
 * - 画面が見えなくなったら取得を止める。
 * - 切断時は無限に再試行せず、回数の上限で止める。
 * - 元データは保持し、間引くのは表示用の副本だけ。保存と測定は元データを使う。
 */
class WaveformViewModel(private val session: InstrumentSession, private val exporter: WaveformExporter) : ViewModel() {

    private val transfer = WaveformTransfer(session.client)

    private val _uiState = MutableStateFlow(WaveformUiState())
    val uiState: StateFlow<WaveformUiState> = _uiState.asStateFlow()

    /** 表示用の間引き結果。元データとは別に持つ。 */
    private val _renderData = MutableStateFlow<List<TraceRenderData>>(emptyList())
    val renderData: StateFlow<List<TraceRenderData>> = _renderData.asStateFlow()

    private var continuousJob: Job? = null
    private var consecutiveFailures = 0

    init {
        viewModelScope.launch {
            session.client.readOnlyMode.collect { readOnly ->
                _uiState.value = _uiState.value.copy(readOnlyMode = readOnly)
            }
        }
        resetTraces()
    }

    /** Capability に合わせて取得対象を作り直す。 */
    private fun resetTraces() {
        val capabilities = session.client.capabilities.value
        val channels = capabilities?.analogChannels ?: listOf(WaveformSource.CH1)
        _uiState.value = _uiState.value.copy(
            traces = channels.map { source ->
                ChannelTrace(source = source, visible = source == WaveformSource.CH1, waveform = null)
            },
        )
    }

    fun onVisible() {
        if (_uiState.value.traces.isEmpty()) resetTraces()
        if (_uiState.value.continuous) startContinuous()
    }

    /** 画面が見えなくなったら取得を止める。本体との通信を占有し続けない。 */
    fun onHidden() {
        continuousJob?.cancel()
        continuousJob = null
    }

    fun setCanvasWidth(widthPx: Int) {
        if (widthPx <= 0 || widthPx == _uiState.value.canvasWidthPx) return
        _uiState.value = _uiState.value.copy(canvasWidthPx = widthPx)
        rebuildRenderData()
    }

    fun toggleTrace(source: WaveformSource) {
        _uiState.value = _uiState.value.copy(
            traces = _uiState.value.traces.map { trace ->
                if (trace.source == source) {
                    ChannelTrace(
                        source = trace.source,
                        visible = !trace.visible,
                        waveform = trace.waveform,
                        transferredBytes = trace.transferredBytes,
                        elapsedMillis = trace.elapsedMillis,
                        rawPayload = trace.rawPayload,
                    )
                } else {
                    trace
                }
            },
        )
        rebuildRenderData()
    }

    fun setBytesPerPoint(bytes: Int) {
        _uiState.value = _uiState.value.copy(bytesPerPoint = bytes.coerceIn(1, 2))
    }

    fun setShowGrid(show: Boolean) {
        _uiState.value = _uiState.value.copy(showGrid = show)
    }

    fun setInterval(millis: Long) {
        _uiState.value = _uiState.value.copy(intervalMillis = millis)
        if (_uiState.value.continuous) startContinuous()
    }

    /** 単発取得。 */
    fun captureOnce() {
        launchBusy("波形取得") { captureAllVisible() }
    }

    fun setContinuous(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(continuous = enabled)
        if (enabled) startContinuous() else onHidden()
    }

    private fun startContinuous() {
        continuousJob?.cancel()
        consecutiveFailures = 0
        continuousJob = viewModelScope.launch {
            while (isActive) {
                val succeeded = runCatching { captureAllVisible() }.isSuccess
                if (succeeded) {
                    consecutiveFailures = 0
                } else {
                    consecutiveFailures += 1
                    // 切断したまま無限に試し続けない。
                    if (consecutiveFailures >= MAX_CONSECUTIVE_FAILURES) {
                        _uiState.value = _uiState.value.copy(
                            continuous = false,
                            notice = "取得が $MAX_CONSECUTIVE_FAILURES 回続けて失敗したため、連続取得を停止しました。",
                        )
                        break
                    }
                }
                // 前回が終わってから待つ。要求の積み上がりを防ぐ。
                delay(_uiState.value.intervalMillis)
            }
        }
    }

    private suspend fun captureAllVisible() {
        val targets = _uiState.value.traces.filter { it.visible }
        if (targets.isEmpty()) {
            _uiState.value = _uiState.value.copy(notice = "表示するチャンネルが選ばれていません。")
            return
        }

        val started = System.currentTimeMillis()
        var totalBytes = 0
        val updated = _uiState.value.traces.toMutableList()

        for ((index, trace) in _uiState.value.traces.withIndex()) {
            if (!trace.visible) continue
            val capture = transfer.capture(
                source = trace.source,
                config = WaveformTransferConfig(bytesPerPoint = _uiState.value.bytesPerPoint),
                // 読み取り専用モードでは DATa:* を送れないため、本体の現在設定のまま取得する。
                configureTransfer = !_uiState.value.readOnlyMode,
            )
            totalBytes += capture.transferredBytes
            updated[index] = ChannelTrace(
                source = trace.source,
                visible = true,
                waveform = capture.waveform,
                transferredBytes = capture.transferredBytes,
                elapsedMillis = capture.elapsedMillis,
            )
        }

        val elapsed = (System.currentTimeMillis() - started).coerceAtLeast(1)
        _uiState.value = _uiState.value.copy(
            traces = updated,
            lastCaptureMillis = elapsed,
            throughputBytesPerSecond = totalBytes * MILLIS_PER_SECOND / elapsed,
        )
        rebuildRenderData(resetWindow = _uiState.value.window == null)
    }

    /** 自動スケール。表示中の波形が収まる範囲へ戻す。 */
    fun autoScale() {
        rebuildRenderData(resetWindow = true)
    }

    fun onTransform(zoomX: Float, zoomY: Float, panX: Float, panY: Float) {
        val window = _uiState.value.window ?: return
        val width = _uiState.value.canvasWidthPx.toDouble()
        // ピクセル移動量を時間・電圧の移動量へ直す。
        val timeDelta = -panX / width * window.timeSpan
        val voltDelta = panY / CANVAS_HEIGHT_REFERENCE * window.voltSpan

        var next = window.pan(timeDelta, voltDelta)
        if (zoomX != 1f) next = next.zoomTime(zoomX.toDouble())
        if (zoomY != 1f) next = next.zoomVoltage(zoomY.toDouble())

        _uiState.value = _uiState.value.copy(window = next)
        rebuildRenderData()
    }

    fun zoomTime(factor: Double) {
        val window = _uiState.value.window ?: return
        _uiState.value = _uiState.value.copy(window = window.zoomTime(factor))
        rebuildRenderData()
    }

    fun zoomVoltage(factor: Double) {
        val window = _uiState.value.window ?: return
        _uiState.value = _uiState.value.copy(window = window.zoomVoltage(factor))
        rebuildRenderData()
    }

    fun setCursorsEnabled(vertical: Boolean, horizontal: Boolean) {
        val window = _uiState.value.window
        val cursors = _uiState.value.cursors.copy(
            verticalEnabled = vertical,
            horizontalEnabled = horizontal,
            // 初期位置は表示範囲の 1/3 と 2/3。重なった状態で出すと動かしにくい。
            time1 = if (window != null && _uiState.value.cursors.time1 == 0.0) {
                window.startTime + window.timeSpan / 3
            } else {
                _uiState.value.cursors.time1
            },
            time2 = if (window != null && _uiState.value.cursors.time2 == 0.0) {
                window.startTime + window.timeSpan * 2 / 3
            } else {
                _uiState.value.cursors.time2
            },
            volts1 = if (window != null && _uiState.value.cursors.volts1 == 0.0) {
                window.minVolts + window.voltSpan / 3
            } else {
                _uiState.value.cursors.volts1
            },
            volts2 = if (window != null && _uiState.value.cursors.volts2 == 0.0) {
                window.minVolts + window.voltSpan * 2 / 3
            } else {
                _uiState.value.cursors.volts2
            },
        )
        _uiState.value = _uiState.value.copy(cursors = cursors)
    }

    fun onCursorDrag(index: Int, ratioX: Float, ratioY: Float) {
        val window = _uiState.value.window ?: return
        val time = window.startTime + window.timeSpan * ratioX
        // 画面座標は上が 0 なので反転する。
        val volts = window.maxVolts - window.voltSpan * ratioY
        val cursors = _uiState.value.cursors
        _uiState.value = _uiState.value.copy(
            cursors = if (index == 0) {
                cursors.copy(time1 = time, volts1 = volts)
            } else {
                cursors.copy(time2 = time, volts2 = volts)
            },
        )
    }

    /**
     * 表示用データを作り直す。
     *
     * **元データは変更しない。** 間引きの結果を元データへ書き戻すと、
     * 保存や測定でピークが失われる。
     */
    private fun rebuildRenderData(resetWindow: Boolean = false) {
        val state = _uiState.value
        val target = state.canvasWidthPx

        val rendered = state.traces.mapNotNull { trace ->
            val waveform = trace.waveform ?: return@mapNotNull null
            val (times, values) = when (waveform) {
                is AnalogWaveform -> waveform.times to waveform.volts
                is SpectrumTrace -> waveform.frequencies to waveform.amplitudes
                is EnvelopeWaveform -> waveform.times to waveform.maxVoltsPerPoint
                else -> return@mapNotNull null
            }

            val window = if (resetWindow) null else state.window
            val decimated = if (window != null) {
                MinMaxDecimator.decimateRange(times, values, window.startTime, window.endTime, target)
            } else {
                MinMaxDecimator.decimate(times, values, target)
            }

            TraceRenderData(
                label = trace.source.displayName,
                color = colorFor(trace.source),
                times = decimated.times,
                minValues = decimated.minValues,
                maxValues = decimated.maxValues,
                visible = trace.visible,
            )
        }

        _renderData.value = rendered
        if (resetWindow || state.window == null) {
            // 全体を表示するため、間引きは範囲指定なしでやり直す。
            val full = state.traces.mapNotNull { trace ->
                val waveform = trace.waveform as? AnalogWaveform ?: return@mapNotNull null
                val decimated = MinMaxDecimator.decimate(waveform.times, waveform.volts, target)
                TraceRenderData(
                    label = trace.source.displayName,
                    color = colorFor(trace.source),
                    times = decimated.times,
                    minValues = decimated.minValues,
                    maxValues = decimated.maxValues,
                    visible = trace.visible,
                )
            }
            if (full.isNotEmpty()) {
                _uiState.value = _uiState.value.copy(window = ViewWindow.fitting(full))
                _renderData.value = full
            }
        }
    }

    private fun colorFor(source: WaveformSource): androidx.compose.ui.graphics.Color = when {
        source.isAnalogChannel -> TraceColors.forAnalogChannel(WaveformSource.ANALOG_CHANNELS.indexOf(source) + 1)
        source.isRf -> TraceColors.rf
        source.isDigitalBit -> TraceColors.digital
        source == WaveformSource.MATH -> TraceColors.math
        else -> TraceColors.reference
    }

    // ---- 保存 ----

    fun exportCsv(source: WaveformSource) = exportWith(source, ExportFormat.CSV) { waveform, name ->
        exporter.exportCsv(waveform, name)
    }

    fun exportPng(source: WaveformSource) = exportWith(source, ExportFormat.PNG) { waveform, name ->
        exporter.exportPng(waveform, name)
    }

    fun exportJson(source: WaveformSource) = exportWith(source, ExportFormat.JSON) { waveform, name ->
        exporter.exportJson(waveform, name)
    }

    private fun exportWith(source: WaveformSource, format: ExportFormat, block: (Waveform, String) -> ExportedFile) {
        val waveform = _uiState.value.traces.firstOrNull { it.source == source }?.waveform
        if (waveform == null) {
            _uiState.value = _uiState.value.copy(notice = "${source.displayName} の波形がありません。先に取得してください。")
            return
        }
        launchBusy("${format.displayName} 保存") {
            val name = exporter.buildFileName(
                template = WaveformExporter.DEFAULT_FILE_NAME_TEMPLATE,
                source = source.scpiValue,
                format = format,
            )
            val exported = block(waveform, name)
            _uiState.value = _uiState.value.copy(
                exports = (listOf(exported) + _uiState.value.exports).take(MAX_EXPORT_HISTORY),
                notice = "${exported.file.name} を保存しました" +
                    "（${exported.sizeBytes} バイト / ${exported.pointCount} 点）",
            )
        }
    }

    fun clearNotice() {
        _uiState.value = _uiState.value.copy(notice = null)
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null, errorRemedy = null)
    }

    private fun launchBusy(label: String, block: suspend () -> Unit) {
        _uiState.value = _uiState.value.copy(busy = true, busyLabel = label)
        viewModelScope.launch {
            try {
                block()
            } catch (exception: ScpiException) {
                _uiState.value = _uiState.value.copy(
                    error = exception.error,
                    errorRemedy = ConnectionDiagnostics.remedyFor(exception.error, session.lastConfig.value),
                )
            } catch (exception: Exception) {
                _uiState.value = _uiState.value.copy(error = ScopeError.Unknown(exception.message, exception))
            } finally {
                _uiState.value = _uiState.value.copy(busy = false, busyLabel = "")
            }
        }
    }

    override fun onCleared() {
        continuousJob?.cancel()
        super.onCleared()
    }

    companion object {
        private const val MAX_CONSECUTIVE_FAILURES = 3
        private const val MAX_EXPORT_HISTORY = 20
        private const val MILLIS_PER_SECOND = 1000.0
        private const val CANVAS_HEIGHT_REFERENCE = 720.0

        fun factory(session: InstrumentSession, exporter: WaveformExporter): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T = WaveformViewModel(session, exporter) as T
            }
    }
}
