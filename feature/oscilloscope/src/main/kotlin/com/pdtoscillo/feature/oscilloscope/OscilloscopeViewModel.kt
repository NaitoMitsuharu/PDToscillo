package com.pdtoscillo.feature.oscilloscope

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.pdtoscillo.core.model.AcquisitionMode
import com.pdtoscillo.core.model.BandwidthLimit
import com.pdtoscillo.core.model.ChannelCoupling
import com.pdtoscillo.core.model.ChannelSettings
import com.pdtoscillo.core.model.ChannelTermination
import com.pdtoscillo.core.model.InstrumentCapabilities
import com.pdtoscillo.core.model.InstrumentIdentity
import com.pdtoscillo.core.model.InstrumentSnapshot
import com.pdtoscillo.core.model.ScopeError
import com.pdtoscillo.core.model.TriggerSlope
import com.pdtoscillo.core.model.TriggerSweepMode
import com.pdtoscillo.core.network.ConnectionDiagnostics
import com.pdtoscillo.core.network.InstrumentSession
import com.pdtoscillo.core.scpi.ScpiClient
import com.pdtoscillo.core.scpi.ScpiException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** 危険度の高い操作は確認を経てから実行する。 */
enum class ConfirmableAction(val title: String, val message: String) {
    AUTOSET(
        title = "Autoset を実行しますか？",
        message = "本体が信号に合わせて水平軸・垂直軸・トリガを自動設定します。現在の設定は失われます。",
    ),
    DEFAULT_SETUP(
        title = "初期設定に戻しますか？",
        message = "本体の設定がすべて初期値に戻ります。取り消せません。",
    ),
    RESET(
        title = "リセットを実行しますか？",
        message = "*RST を送信します。本体の設定がすべて初期値に戻ります。取り消せません。",
    ),
    SET_TRIGGER_LEVEL_50(
        title = "トリガレベルを 50% に合わせますか？",
        message = "現在の信号振幅の中央へトリガレベルを移動します。",
    ),
}

data class OscilloscopeUiState(
    val identity: InstrumentIdentity? = null,
    val capabilities: InstrumentCapabilities? = null,
    val snapshot: InstrumentSnapshot = InstrumentSnapshot.empty(),
    val readOnlyMode: Boolean = true,
    val busy: Boolean = false,
    val busyLabel: String = "",
    val autoRefresh: Boolean = false,
    val refreshIntervalMillis: Long = DEFAULT_REFRESH_INTERVAL_MILLIS,
    val lastResponseMillis: Long? = null,
    val error: ScopeError? = null,
    val errorRemedy: String? = null,
    val pendingConfirmation: ConfirmableAction? = null,
    /** 設定が拒否された、または丸められた場合の通知。 */
    val notice: String? = null,
) {
    companion object {
        const val DEFAULT_REFRESH_INTERVAL_MILLIS = 1_000L

        /** 現実的な更新周期。短すぎると本体の操作と競合する。 */
        val REFRESH_INTERVALS = listOf(200L, 500L, 1_000L, 2_000L, 5_000L)
    }
}

/**
 * 概要画面とチャンネル設定画面の ViewModel。
 *
 * 自動更新は画面が見えている間だけ動かす。バックグラウンドで回し続けると本体の
 * 通常操作と競合し、利用者から見て「勝手に重くなる」状態になる。
 */
class OscilloscopeViewModel(private val session: InstrumentSession) : ViewModel() {

    private val _uiState = MutableStateFlow(OscilloscopeUiState())
    val uiState: StateFlow<OscilloscopeUiState> = _uiState.asStateFlow()

    private var refreshJob: Job? = null

    init {
        viewModelScope.launch {
            session.client.readOnlyMode.collect { readOnly ->
                _uiState.value = _uiState.value.copy(readOnlyMode = readOnly)
            }
        }
        viewModelScope.launch {
            session.client.lastResponseMillis.collect { millis ->
                _uiState.value = _uiState.value.copy(lastResponseMillis = millis)
            }
        }
        _uiState.value = _uiState.value.copy(
            identity = session.client.identity.value,
            capabilities = session.client.capabilities.value,
        )
    }

    /** 画面が見えたときに呼ぶ。 */
    fun onVisible() {
        _uiState.value = _uiState.value.copy(
            identity = session.client.identity.value,
            capabilities = session.client.capabilities.value,
        )
        refresh()
        if (_uiState.value.autoRefresh) startAutoRefresh()
    }

    /** 画面が見えなくなったときに呼ぶ。取得を止めて本体と通信を占有しない。 */
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
                // 前回の取得が終わってから次の待ち時間を数える。
                // 一定周期で投げ続けると、応答が遅い機種で要求が積み上がる。
                runCatching { loadSnapshot() }
                delay(_uiState.value.refreshIntervalMillis)
            }
        }
    }

    fun refresh() {
        launchBusy("アクイジション / タイムベース / トリガ / チャンネル設定を読み込み中") { loadSnapshot() }
    }

    private suspend fun loadSnapshot() {
        val snapshot = session.driver.readSnapshot(session.client.capabilities.value)
        _uiState.value = _uiState.value.copy(snapshot = snapshot)
    }

    fun run() = launchBusy("Run") {
        session.driver.run()
        loadSnapshot()
    }

    fun stop() = launchBusy("Stop") {
        session.driver.stop()
        loadSnapshot()
    }

    fun single() = launchBusy("Single") {
        session.driver.single()
        loadSnapshot()
    }

    fun forceTrigger() = launchBusy("Force Trigger") {
        session.driver.forceTrigger()
        loadSnapshot()
    }

    fun setAcquisitionMode(mode: AcquisitionMode) = applyAndReport("Acquisition モード") {
        session.driver.applyAcquisitionMode(mode)
    }

    fun setAverageCount(count: Int) = applyAndReport("Average 回数") {
        session.driver.applyAverageCount(count)
    }

    fun setHorizontalScale(secondsPerDivision: Double) = applyAndReport("水平スケール") {
        session.driver.applyHorizontalScale(secondsPerDivision)
    }

    fun setRecordLength(length: Long) = applyAndReport("レコード長") {
        session.driver.applyRecordLength(length)
    }

    fun setChannelDisplay(channel: Int, displayed: Boolean) = applyAndReport("CH$channel 表示") {
        session.driver.applyChannelDisplay(channel, displayed)
    }

    fun setVerticalScale(channel: Int, voltsPerDivision: Double) = applyAndReport("CH$channel 垂直スケール") {
        session.driver.applyVerticalScale(channel, voltsPerDivision)
    }

    fun setVerticalPosition(channel: Int, divisions: Double) = applyAndReport("CH$channel 垂直位置") {
        session.driver.applyVerticalPosition(channel, divisions)
    }

    fun setOffset(channel: Int, volts: Double) = applyAndReport("CH$channel オフセット") {
        session.driver.applyOffset(channel, volts)
    }

    fun setCoupling(channel: Int, coupling: ChannelCoupling) = applyAndReport("CH$channel カップリング") {
        session.driver.applyCoupling(channel, coupling)
    }

    fun setBandwidthLimit(channel: Int, limit: BandwidthLimit) = applyAndReport("CH$channel 帯域制限") {
        session.driver.applyBandwidthLimit(channel, limit, _uiState.value.capabilities?.analogBandwidth)
    }

    fun setInvert(channel: Int, inverted: Boolean) = applyAndReport("CH$channel 反転") {
        session.driver.applyInvert(channel, inverted)
    }

    fun setLabel(channel: Int, label: String) = applyAndReport("CH$channel ラベル") {
        session.driver.applyLabel(channel, label)
    }

    fun setTermination(channel: Int, termination: ChannelTermination) = applyAndReport("CH$channel 終端") {
        session.driver.applyTermination(channel, termination)
    }

    fun setDeskew(channel: Int, seconds: Double) = applyAndReport("CH$channel Deskew") {
        session.driver.applyDeskew(channel, seconds)
    }

    fun setProbeAttenuation(channel: Int, attenuation: Double) = applyAndReport("CH$channel プローブ減衰比") {
        session.driver.applyProbeAttenuation(channel, attenuation)
    }

    fun setTriggerSlope(slope: TriggerSlope) = applyAndReport("トリガスロープ") {
        session.driver.applyTriggerSlope(slope)
    }

    fun setTriggerSweepMode(mode: TriggerSweepMode) = applyAndReport("トリガモード") {
        session.driver.applyTriggerSweepMode(mode)
    }

    fun setTriggerLevel(channel: Int, volts: Double) = applyAndReport("トリガレベル") {
        session.driver.applyTriggerLevel(channel, volts)
    }

    fun setTriggerSource(source: com.pdtoscillo.core.model.WaveformSource) = applyAndReport("トリガソース") {
        session.driver.applyTriggerSource(source)
    }

    fun setTriggerCoupling(coupling: com.pdtoscillo.core.model.TriggerCoupling) = applyAndReport("トリガカップリング") {
        session.driver.applyTriggerCoupling(coupling)
    }

    fun setTriggerHoldoff(seconds: Double) = applyAndReport("ホールドオフ") {
        session.driver.applyTriggerHoldoff(seconds)
    }

    /**
     * トリガ種別を変更する。
     *
     * 種別を変えると、それまでの詳細設定は種別ごとの既定値に置き換わる。
     * 元に戻すのが手間なので、切り替え前に現在の設定を控えておく。
     */
    fun requestTriggerType(type: com.pdtoscillo.core.model.TriggerType) {
        val previous = _uiState.value.snapshot.trigger.type
        if (previous == type) return
        launchBusy("トリガ種別") {
            when (val result = session.driver.applyTriggerType(type)) {
                is ScpiClient.ApplyResult.Applied -> _uiState.value = _uiState.value.copy(
                    notice = "トリガ種別を ${result.accepted?.displayName ?: type.displayName} に変更しました" +
                        (previous?.let { "（変更前: ${it.displayName}）" } ?: ""),
                )

                is ScpiClient.ApplyResult.Rejected -> _uiState.value = _uiState.value.copy(
                    error = result.error,
                    errorRemedy = ConnectionDiagnostics.remedyFor(result.error, session.lastConfig.value),
                )
            }
            loadSnapshot()
        }
    }

    fun channel(number: Int): ChannelSettings? = _uiState.value.snapshot.channels.firstOrNull { it.channel == number }

    fun setReadOnlyMode(enabled: Boolean) = session.client.setReadOnlyMode(enabled)

    /** 危険度の高い操作は確認を挟む。 */
    fun requestConfirmation(action: ConfirmableAction) {
        _uiState.value = _uiState.value.copy(pendingConfirmation = action)
    }

    fun dismissConfirmation() {
        _uiState.value = _uiState.value.copy(pendingConfirmation = null)
    }

    fun confirmPendingAction() {
        val action = _uiState.value.pendingConfirmation ?: return
        _uiState.value = _uiState.value.copy(pendingConfirmation = null)
        when (action) {
            ConfirmableAction.SET_TRIGGER_LEVEL_50 -> launchBusy(action.title) {
                session.driver.setTriggerLevelToFiftyPercent()
                loadSnapshot()
            }

            // Autoset / Default setup / Reset は Phase 5 の一括操作で扱う。
            // ここで無言に何もしないと押した意味が分からないため、明示する。
            else -> _uiState.value = _uiState.value.copy(
                notice = "${action.title.removeSuffix("？")}は未実装です。SCPI コンソールから実行できます。",
            )
        }
    }

    fun clearNotice() {
        _uiState.value = _uiState.value.copy(notice = null)
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null, errorRemedy = null)
    }

    /**
     * 設定を適用し、結果を利用者へ伝える。
     *
     * 拒否された場合と、要求値と受理値が違う場合の両方を伝える。
     * 「送ったのに反映されていない」を黙って通さない。
     */
    private fun <T> applyAndReport(label: String, block: suspend () -> ScpiClient.ApplyResult<T>) {
        launchBusy(label) {
            when (val result = block()) {
                is ScpiClient.ApplyResult.Applied -> {
                    _uiState.value = _uiState.value.copy(
                        notice = "$label: ${result.accepted ?: "不明"} を本体が受理しました" +
                            if (result.previous != null) "（変更前: ${result.previous}）" else "",
                    )
                }

                is ScpiClient.ApplyResult.Rejected -> {
                    _uiState.value = _uiState.value.copy(
                        error = result.error,
                        errorRemedy = ConnectionDiagnostics.remedyFor(result.error, session.lastConfig.value),
                    )
                }
            }
            loadSnapshot()
        }
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
        refreshJob?.cancel()
        super.onCleared()
    }

    companion object {
        fun factory(session: InstrumentSession): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = OscilloscopeViewModel(session) as T
        }
    }
}
