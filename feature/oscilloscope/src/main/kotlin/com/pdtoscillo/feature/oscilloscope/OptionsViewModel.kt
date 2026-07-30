package com.pdtoscillo.feature.oscilloscope

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.pdtoscillo.core.model.InstrumentCapabilities
import com.pdtoscillo.core.model.ScopeError
import com.pdtoscillo.core.network.ConnectionDiagnostics
import com.pdtoscillo.core.network.InstrumentSession
import com.pdtoscillo.core.scpi.AfgState
import com.pdtoscillo.core.scpi.DigitalChannelState
import com.pdtoscillo.core.scpi.DvmState
import com.pdtoscillo.core.scpi.OptionControllers
import com.pdtoscillo.core.scpi.RfState
import com.pdtoscillo.core.scpi.ScpiClient
import com.pdtoscillo.core.scpi.ScpiException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class OptionsUiState(
    val capabilities: InstrumentCapabilities? = null,
    val afg: AfgState = AfgState.UNKNOWN,
    val dvm: DvmState = DvmState.UNKNOWN,
    val rf: RfState = RfState.UNKNOWN,
    val digital: List<DigitalChannelState> = emptyList(),
    val busTypes: Map<Int, String?> = emptyMap(),
    val busy: Boolean = false,
    val busyLabel: String = "",
    val readOnlyMode: Boolean = true,
    val error: ScopeError? = null,
    val errorRemedy: String? = null,
    val notice: String? = null,
    /** AFG 出力を有効にする前の確認。 */
    val pendingAfgOutputEnable: Boolean = false,
) {
    val hasAnyOption: Boolean
        get() = capabilities?.let {
            it.hasAfg || it.hasDvm || it.hasSpectrumAnalyzer || it.hasDigitalChannels || it.hasBusDecode
        } ?: false
}

/**
 * オプション機能（デジタル / スペクトラム / AFG / DVM / バス）の ViewModel。
 *
 * すべて Capability で搭載を確認してから読み書きする。搭載していない機種へ送ると
 * 未定義ヘッダーになり、毎回タイムアウトを待つことになる。
 */
class OptionsViewModel(private val session: InstrumentSession) : ViewModel() {

    private val controllers = OptionControllers(session.client)

    private val _uiState = MutableStateFlow(OptionsUiState())
    val uiState: StateFlow<OptionsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            session.client.readOnlyMode.collect { readOnly ->
                _uiState.value = _uiState.value.copy(readOnlyMode = readOnly)
            }
        }
        _uiState.value = _uiState.value.copy(capabilities = session.client.capabilities.value)
    }

    fun onVisible() {
        _uiState.value = _uiState.value.copy(capabilities = session.client.capabilities.value)
        refresh()
    }

    fun refresh() = launchBusy("取得") {
        val capabilities = session.client.capabilities.value
        _uiState.value = _uiState.value.copy(
            capabilities = capabilities,
            // 搭載していない機能は問い合わせない。
            afg = if (capabilities?.hasAfg == true) controllers.readAfg() else AfgState.UNKNOWN,
            dvm = if (capabilities?.hasDvm == true) controllers.readDvm() else DvmState.UNKNOWN,
            rf = if (capabilities?.hasSpectrumAnalyzer == true) controllers.readRf() else RfState.UNKNOWN,
            digital = if (capabilities?.hasDigitalChannels == true) {
                controllers.readDigitalChannels(capabilities.digitalChannelCount.coerceAtMost(MAX_DIGITAL_READ))
            } else {
                emptyList()
            },
            busTypes = if (capabilities?.hasBusDecode == true) {
                (1..BUS_COUNT).associateWith { controllers.readBusType(it) }
            } else {
                emptyMap()
            },
        )
    }

    // ---- AFG ----

    fun setAfgFunction(function: String) = applyAndReport("AFG 波形") { controllers.applyAfgFunction(function) }

    fun setAfgFrequency(hertz: Double) = applyAndReport("AFG 周波数") { controllers.applyAfgFrequency(hertz) }

    fun setAfgAmplitude(volts: Double) = applyAndReport("AFG 振幅") { controllers.applyAfgAmplitude(volts) }

    fun setAfgOffset(volts: Double) = applyAndReport("AFG オフセット") { controllers.applyAfgOffset(volts) }

    fun setAfgDuty(percent: Double) = applyAndReport("AFG デューティ") { controllers.applyAfgDuty(percent) }

    /**
     * AFG 出力の切り替え。
     *
     * 有効にする場合は確認を挟む。被測定回路へ実際に信号が出るため、
     * 押し間違いで回路を壊し得る。切る操作は確認しない。
     */
    fun requestAfgOutput(enabled: Boolean) {
        if (enabled) {
            _uiState.value = _uiState.value.copy(pendingAfgOutputEnable = true)
        } else {
            applyAndReport("AFG 出力") { controllers.applyAfgOutput(false) }
        }
    }

    fun confirmAfgOutput() {
        _uiState.value = _uiState.value.copy(pendingAfgOutputEnable = false)
        applyAndReport("AFG 出力") { controllers.applyAfgOutput(true) }
    }

    fun dismissAfgOutput() {
        _uiState.value = _uiState.value.copy(pendingAfgOutputEnable = false)
    }

    // ---- DVM ----

    fun setDvmMode(mode: String) = applyAndReport("DVM モード") { controllers.applyDvmMode(mode) }

    fun setDvmSource(source: String) = applyAndReport("DVM ソース") { controllers.applyDvmSource(source) }

    // ---- RF ----

    fun setRfCenterFrequency(hertz: Double) = applyAndReport("中心周波数") {
        controllers.applyRfCenterFrequency(hertz)
    }

    fun setRfSpan(hertz: Double) = applyAndReport("スパン") { controllers.applyRfSpan(hertz) }

    fun setRfResolutionBandwidth(hertz: Double) = applyAndReport("RBW") {
        controllers.applyRfResolutionBandwidth(hertz)
    }

    fun setRfReferenceLevel(dbm: Double) = applyAndReport("基準レベル") {
        controllers.applyRfReferenceLevel(dbm)
    }

    // ---- Digital ----

    fun setDigitalDisplay(bit: Int, displayed: Boolean) = applyAndReport("D$bit 表示") {
        controllers.applyDigitalDisplay(bit, displayed)
    }

    fun setDigitalThreshold(bit: Int, volts: Double) = applyAndReport("D$bit しきい値") {
        controllers.applyDigitalThreshold(bit, volts)
    }

    // ---- Bus ----

    fun setBusType(bus: Int, type: String) = applyAndReport("B$bus 種別") {
        controllers.applyBusType(bus, type)
    }

    fun setBusDisplay(bus: Int, enabled: Boolean) = applyAndReport("B$bus 表示") {
        controllers.applyBusDisplay(bus, enabled)
    }

    fun clearNotice() {
        _uiState.value = _uiState.value.copy(notice = null)
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null, errorRemedy = null)
    }

    private fun <T> applyAndReport(label: String, block: suspend () -> ScpiClient.ApplyResult<T>) {
        launchBusy(label) {
            when (val result = block()) {
                is ScpiClient.ApplyResult.Applied -> _uiState.value = _uiState.value.copy(
                    notice = "$label: ${result.accepted ?: "不明"} を本体が受理しました",
                )

                is ScpiClient.ApplyResult.Rejected -> _uiState.value = _uiState.value.copy(
                    error = result.error,
                    errorRemedy = ConnectionDiagnostics.remedyFor(result.error, session.lastConfig.value),
                )
            }
            refreshQuietly()
        }
    }

    private suspend fun refreshQuietly() {
        val capabilities = session.client.capabilities.value ?: return
        _uiState.value = _uiState.value.copy(
            afg = if (capabilities.hasAfg) controllers.readAfg() else AfgState.UNKNOWN,
            dvm = if (capabilities.hasDvm) controllers.readDvm() else DvmState.UNKNOWN,
            rf = if (capabilities.hasSpectrumAnalyzer) controllers.readRf() else RfState.UNKNOWN,
        )
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

    companion object {
        /** 一度に読むデジタルチャンネル数の上限。16 本すべてを毎回読むと遅い。 */
        private const val MAX_DIGITAL_READ = 16
        private const val BUS_COUNT = 2

        fun factory(session: InstrumentSession): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = OptionsViewModel(session) as T
        }
    }
}
