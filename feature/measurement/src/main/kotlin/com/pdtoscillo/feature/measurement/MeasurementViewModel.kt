package com.pdtoscillo.feature.measurement

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.pdtoscillo.core.model.MeasurementType
import com.pdtoscillo.core.model.ScopeError
import com.pdtoscillo.core.model.WaveformSource
import com.pdtoscillo.core.network.ConnectionDiagnostics
import com.pdtoscillo.core.network.InstrumentSession
import com.pdtoscillo.core.scpi.MeasurementController
import com.pdtoscillo.core.scpi.MeasurementSlot
import com.pdtoscillo.core.scpi.ScpiClient
import com.pdtoscillo.core.scpi.ScpiException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class MeasurementUiState(
    val slots: List<MeasurementSlot> = emptyList(),
    val maxSlots: Int = 0,
    val availableSources: List<WaveformSource> = emptyList(),
    val availableTypes: List<MeasurementType> = MeasurementType.BASIC,
    val statisticsEnabled: Boolean = false,
    val autoRefresh: Boolean = false,
    val refreshIntervalMillis: Long = DEFAULT_INTERVAL_MILLIS,
    val busy: Boolean = false,
    val busyLabel: String = "",
    val readOnlyMode: Boolean = true,
    val error: ScopeError? = null,
    val errorRemedy: String? = null,
    val notice: String? = null,
    /** 追加操作の対象。 */
    val pendingType: MeasurementType = MeasurementType.FREQUENCY,
    val pendingSource: WaveformSource = WaveformSource.CH1,
    val pendingSecondSource: WaveformSource = WaveformSource.CH2,
) {
    val activeSlots: List<MeasurementSlot> get() = slots.filter { it.enabled }
    val freeSlot: Int? get() = slots.firstOrNull { !it.enabled }?.slot

    companion object {
        const val DEFAULT_INTERVAL_MILLIS = 1_000L
        val INTERVALS = listOf(500L, 1_000L, 2_000L, 5_000L)
    }
}

/**
 * 測定画面の ViewModel。
 *
 * スロット数と選べる測定種別は Capability から決める。機種が持たないスロットを
 * 問い合わせると、未定義ヘッダーのタイムアウトを毎回待つことになる。
 */
class MeasurementViewModel(private val session: InstrumentSession) : ViewModel() {

    private val controller = MeasurementController(session.client)

    private val _uiState = MutableStateFlow(MeasurementUiState())
    val uiState: StateFlow<MeasurementUiState> = _uiState.asStateFlow()

    private var refreshJob: Job? = null

    init {
        viewModelScope.launch {
            session.client.readOnlyMode.collect { readOnly ->
                _uiState.value = _uiState.value.copy(readOnlyMode = readOnly)
            }
        }
        applyCapabilities()
    }

    private fun applyCapabilities() {
        val capabilities = session.client.capabilities.value
        val maxSlots = capabilities?.maxMeasurementCount?.takeIf { it > 0 } ?: DEFAULT_SLOT_COUNT
        val sources = capabilities?.supportedWaveformSources
            ?.filter { it.isAnalogChannel || it == WaveformSource.MATH }
            ?.sortedBy { it.scpiValue }
            ?: listOf(WaveformSource.CH1)
        val types = capabilities?.supportedMeasurements
            ?.filter { it in MeasurementType.BASIC || !it.requiresOption }
            ?.sortedBy { it.displayName }
            ?: MeasurementType.BASIC

        _uiState.value = _uiState.value.copy(
            maxSlots = maxSlots,
            availableSources = sources.ifEmpty { listOf(WaveformSource.CH1) },
            availableTypes = types.ifEmpty { MeasurementType.BASIC },
            pendingSource = sources.firstOrNull() ?: WaveformSource.CH1,
            slots = (1..maxSlots).map { MeasurementSlot.empty(it) },
        )
    }

    fun onVisible() {
        if (_uiState.value.maxSlots == 0) applyCapabilities()
        refresh()
        if (_uiState.value.autoRefresh) startAutoRefresh()
    }

    fun onHidden() {
        refreshJob?.cancel()
        refreshJob = null
    }

    fun setAutoRefresh(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(autoRefresh = enabled)
        if (enabled) startAutoRefresh() else onHidden()
    }

    fun setRefreshInterval(millis: Long) {
        _uiState.value = _uiState.value.copy(refreshIntervalMillis = millis)
        if (_uiState.value.autoRefresh) startAutoRefresh()
    }

    private fun startAutoRefresh() {
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            while (isActive) {
                runCatching { loadSlots() }
                // 前回の取得が終わってから待つ。
                delay(_uiState.value.refreshIntervalMillis)
            }
        }
    }

    fun refresh() = launchBusy("測定値取得") { loadSlots() }

    private suspend fun loadSlots() {
        val slots = controller.readAll(
            slotCount = _uiState.value.maxSlots,
            withStatistics = _uiState.value.statisticsEnabled,
        )
        _uiState.value = _uiState.value.copy(slots = slots)
    }

    fun setStatisticsEnabled(enabled: Boolean) = launchBusy("統計") {
        when (val result = controller.setStatisticsEnabled(enabled)) {
            is ScpiClient.ApplyResult.Applied ->
                _uiState.value = _uiState.value.copy(statisticsEnabled = result.accepted ?: enabled)

            is ScpiClient.ApplyResult.Rejected -> reportError(result.error)
        }
        loadSlots()
    }

    fun setPendingType(type: MeasurementType) {
        _uiState.value = _uiState.value.copy(pendingType = type)
    }

    fun setPendingSource(source: WaveformSource) {
        _uiState.value = _uiState.value.copy(pendingSource = source)
    }

    fun setPendingSecondSource(source: WaveformSource) {
        _uiState.value = _uiState.value.copy(pendingSecondSource = source)
    }

    /** 空きスロットへ測定を追加する。空きが無ければその旨を伝える。 */
    fun addMeasurement() {
        val slot = _uiState.value.freeSlot
        if (slot == null) {
            _uiState.value = _uiState.value.copy(
                notice = "空きスロットがありません。この機種の同時測定数は ${_uiState.value.maxSlots} です。",
            )
            return
        }
        val state = _uiState.value
        launchBusy("測定を追加") {
            when (
                val result = controller.configureSlot(
                    slot = slot,
                    type = state.pendingType,
                    source = state.pendingSource,
                    secondSource = state.pendingSecondSource,
                )
            ) {
                is ScpiClient.ApplyResult.Applied -> _uiState.value = _uiState.value.copy(
                    notice = "スロット $slot に ${result.accepted?.displayName ?: state.pendingType.displayName} " +
                        "(${state.pendingSource.displayName}) を割り当てました。",
                )

                is ScpiClient.ApplyResult.Rejected -> reportError(result.error)
            }
            loadSlots()
        }
    }

    fun removeMeasurement(slot: Int) = launchBusy("測定を削除") {
        when (val result = controller.disableSlot(slot)) {
            is ScpiClient.ApplyResult.Applied -> Unit
            is ScpiClient.ApplyResult.Rejected -> reportError(result.error)
        }
        loadSlots()
    }

    fun changeSource(slot: Int, source: WaveformSource) {
        val current = _uiState.value.slots.firstOrNull { it.slot == slot }
        val type = current?.type ?: _uiState.value.pendingType
        launchBusy("ソース変更") {
            when (val result = controller.configureSlot(slot, type, source)) {
                is ScpiClient.ApplyResult.Applied -> Unit
                is ScpiClient.ApplyResult.Rejected -> reportError(result.error)
            }
            loadSlots()
        }
    }

    fun clearNotice() {
        _uiState.value = _uiState.value.copy(notice = null)
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null, errorRemedy = null)
    }

    private fun reportError(error: ScopeError) {
        _uiState.value = _uiState.value.copy(
            error = error,
            errorRemedy = ConnectionDiagnostics.remedyFor(error, session.lastConfig.value),
        )
    }

    private fun launchBusy(label: String, block: suspend () -> Unit) {
        _uiState.value = _uiState.value.copy(busy = true, busyLabel = label)
        viewModelScope.launch {
            try {
                block()
            } catch (exception: ScpiException) {
                reportError(exception.error)
            } catch (exception: Exception) {
                _uiState.value = _uiState.value.copy(error = ScopeError.Unknown(exception.message, exception))
            } finally {
                _uiState.value = _uiState.value.copy(busy = false, busyLabel = "")
            }
        }
    }

    override fun onCleared() {
        refreshJob?.cancel()
        super.onCleared()
    }

    companion object {
        private const val DEFAULT_SLOT_COUNT = 4

        fun factory(session: InstrumentSession): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = MeasurementViewModel(session) as T
        }
    }
}
