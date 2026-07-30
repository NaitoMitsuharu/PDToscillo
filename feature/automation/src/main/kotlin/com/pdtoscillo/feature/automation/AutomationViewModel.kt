package com.pdtoscillo.feature.automation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.pdtoscillo.core.database.export.ExportFormat
import com.pdtoscillo.core.database.export.WaveformExporter
import com.pdtoscillo.core.model.MeasurementType
import com.pdtoscillo.core.model.ScopeError
import com.pdtoscillo.core.model.WaveformSource
import com.pdtoscillo.core.network.ConnectionDiagnostics
import com.pdtoscillo.core.network.InstrumentSession
import com.pdtoscillo.core.scpi.AutomationConfig
import com.pdtoscillo.core.scpi.AutomationIteration
import com.pdtoscillo.core.scpi.AutomationProgress
import com.pdtoscillo.core.scpi.AutomationSequence
import com.pdtoscillo.core.scpi.MeasurementController
import com.pdtoscillo.core.scpi.ScpiException
import com.pdtoscillo.core.scpi.WaveformTransfer
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class AutomationUiState(
    val iterationsInput: String = AutomationConfig.DEFAULT_ITERATIONS.toString(),
    val intervalInput: String = "1000",
    val maxDurationMinutesInput: String = "60",
    val selectedSources: Set<WaveformSource> = setOf(WaveformSource.CH1),
    val selectedMeasurements: Set<MeasurementType> = setOf(
        MeasurementType.FREQUENCY,
        MeasurementType.PEAK_TO_PEAK,
    ),
    val availableSources: List<WaveformSource> = listOf(WaveformSource.CH1),
    val captureWaveform: Boolean = true,
    val captureMeasurements: Boolean = true,
    val saveCsv: Boolean = true,
    val stopOnError: Boolean = true,
    val fileNameTemplate: String = "{source}_{index}_{timestamp}",
    val running: Boolean = false,
    val currentStep: String = "",
    val progressIndex: Int = 0,
    val progressTotal: Int = 0,
    val iterations: List<AutomationIteration> = emptyList(),
    val summary: String? = null,
    val readOnlyMode: Boolean = true,
    val error: ScopeError? = null,
    val errorRemedy: String? = null,
    val savedFileCount: Int = 0,
) {
    val iterationsValid: Boolean
        get() = iterationsInput.toIntOrNull()?.let { it in 1..AutomationConfig.MAX_ITERATIONS } == true

    val canStart: Boolean
        get() = !running && !readOnlyMode && iterationsValid && selectedSources.isNotEmpty()
}

/**
 * 自動測定の ViewModel。
 *
 * 無限ループを作らないため、実行回数・上限時間・取得完了待ちの上限をすべて必須にしている。
 * 停止操作でコルーチンをキャンセルすれば、その回の途中でも止まる。
 */
class AutomationViewModel(private val session: InstrumentSession, private val exporter: WaveformExporter) : ViewModel() {

    private val sequence = AutomationSequence(
        client = session.client,
        driver = session.driver,
        measurementController = MeasurementController(session.client),
        waveformTransfer = WaveformTransfer(session.client),
    )

    private val _uiState = MutableStateFlow(AutomationUiState())
    val uiState: StateFlow<AutomationUiState> = _uiState.asStateFlow()

    private var runJob: Job? = null

    init {
        viewModelScope.launch {
            session.client.readOnlyMode.collect { readOnly ->
                _uiState.value = _uiState.value.copy(readOnlyMode = readOnly)
            }
        }
        applyCapabilities()
    }

    private fun applyCapabilities() {
        val channels = session.client.capabilities.value?.analogChannels ?: listOf(WaveformSource.CH1)
        _uiState.value = _uiState.value.copy(availableSources = channels)
    }

    fun onVisible() = applyCapabilities()

    fun onIterationsChange(value: String) {
        _uiState.value = _uiState.value.copy(iterationsInput = value.filter { it.isDigit() })
    }

    fun onIntervalChange(value: String) {
        _uiState.value = _uiState.value.copy(intervalInput = value.filter { it.isDigit() })
    }

    fun onMaxDurationChange(value: String) {
        _uiState.value = _uiState.value.copy(maxDurationMinutesInput = value.filter { it.isDigit() })
    }

    fun onTemplateChange(value: String) {
        _uiState.value = _uiState.value.copy(fileNameTemplate = value)
    }

    fun toggleSource(source: WaveformSource) {
        val current = _uiState.value.selectedSources
        _uiState.value = _uiState.value.copy(
            selectedSources = if (source in current) current - source else current + source,
        )
    }

    fun toggleMeasurement(type: MeasurementType) {
        val current = _uiState.value.selectedMeasurements
        _uiState.value = _uiState.value.copy(
            selectedMeasurements = if (type in current) current - type else current + type,
        )
    }

    fun setCaptureWaveform(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(captureWaveform = enabled)
    }

    fun setCaptureMeasurements(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(captureMeasurements = enabled)
    }

    fun setSaveCsv(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(saveCsv = enabled)
    }

    fun setStopOnError(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(stopOnError = enabled)
    }

    fun start() {
        val state = _uiState.value
        if (!state.canStart) return

        val config = try {
            AutomationConfig(
                iterations = state.iterationsInput.toIntOrNull() ?: AutomationConfig.DEFAULT_ITERATIONS,
                intervalMillis = state.intervalInput.toLongOrNull() ?: AutomationConfig.DEFAULT_INTERVAL_MILLIS,
                sources = state.selectedSources.toList(),
                measurements = state.selectedMeasurements.toList(),
                captureWaveform = state.captureWaveform,
                captureMeasurements = state.captureMeasurements,
                fileNameTemplate = state.fileNameTemplate,
                stopOnError = state.stopOnError,
                maxDurationMillis = (state.maxDurationMinutesInput.toLongOrNull() ?: DEFAULT_MINUTES) *
                    MILLIS_PER_MINUTE,
            )
        } catch (exception: IllegalArgumentException) {
            _uiState.value = _uiState.value.copy(
                error = ScopeError.ArgumentOutOfRange("automation", exception.message),
            )
            return
        }

        _uiState.value = _uiState.value.copy(
            running = true,
            iterations = emptyList(),
            summary = null,
            error = null,
            savedFileCount = 0,
            progressTotal = config.iterations,
        )

        runJob = viewModelScope.launch {
            try {
                sequence.run(
                    config = config,
                    onProgress = ::handleProgress,
                    onWaveform = { index, capture ->
                        if (_uiState.value.saveCsv) {
                            val name = exporter.buildFileName(
                                template = config.fileNameTemplate,
                                source = capture.waveform.source.scpiValue,
                                format = ExportFormat.CSV,
                                index = index,
                            )
                            exporter.exportCsv(capture.waveform, name)
                            _uiState.value = _uiState.value.copy(
                                savedFileCount = _uiState.value.savedFileCount + 1,
                            )
                        }
                    },
                )
            } catch (exception: ScpiException) {
                _uiState.value = _uiState.value.copy(
                    error = exception.error,
                    errorRemedy = ConnectionDiagnostics.remedyFor(exception.error, session.lastConfig.value),
                )
            } catch (exception: Exception) {
                _uiState.value = _uiState.value.copy(error = ScopeError.Unknown(exception.message, exception))
            } finally {
                _uiState.value = _uiState.value.copy(running = false, currentStep = "")
            }
        }
    }

    /** 実行中の停止。コルーチンをキャンセルすると、その回の途中でも止まる。 */
    fun stop() {
        runJob?.cancel()
        runJob = null
        _uiState.value = _uiState.value.copy(running = false, currentStep = "", summary = "停止しました")
    }

    private fun handleProgress(progress: AutomationProgress) {
        when (progress) {
            is AutomationProgress.Started -> _uiState.value = _uiState.value.copy(
                currentStep = "開始（${progress.config.iterations} 回）",
            )

            is AutomationProgress.Step -> _uiState.value = _uiState.value.copy(
                progressIndex = progress.index,
                progressTotal = progress.total,
                currentStep = "${progress.index}/${progress.total}: ${progress.description}",
            )

            is AutomationProgress.IterationFinished -> _uiState.value = _uiState.value.copy(
                iterations = _uiState.value.iterations + progress.iteration,
            )

            is AutomationProgress.Finished -> _uiState.value = _uiState.value.copy(
                summary = buildString {
                    append("完了 ${progress.completed} 回 / 失敗 ${progress.failed} 回")
                    progress.stoppedReason?.let { append("（$it）") }
                },
            )
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null, errorRemedy = null)
    }

    override fun onCleared() {
        runJob?.cancel()
        super.onCleared()
    }

    companion object {
        private const val MILLIS_PER_MINUTE = 60_000L
        private const val DEFAULT_MINUTES = 60L

        fun factory(session: InstrumentSession, exporter: WaveformExporter): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T = AutomationViewModel(session, exporter) as T
            }
    }
}
