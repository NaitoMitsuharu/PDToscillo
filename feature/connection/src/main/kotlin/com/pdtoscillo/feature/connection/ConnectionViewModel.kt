package com.pdtoscillo.feature.connection

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.pdtoscillo.core.model.ConnectionConfig
import com.pdtoscillo.core.model.ConnectionState
import com.pdtoscillo.core.model.InstrumentCapabilities
import com.pdtoscillo.core.model.InstrumentIdentity
import com.pdtoscillo.core.model.LineTerminator
import com.pdtoscillo.core.model.ScopeError
import com.pdtoscillo.core.model.SocketBindStrategy
import com.pdtoscillo.core.model.TransportType
import com.pdtoscillo.core.network.ConnectionDiagnostics
import com.pdtoscillo.core.network.DiagnosticReport
import com.pdtoscillo.core.network.DiagnosticStep
import com.pdtoscillo.core.network.DiscoveredDevice
import com.pdtoscillo.core.network.DiscoveryProgress
import com.pdtoscillo.core.network.InstrumentSession
import com.pdtoscillo.core.network.NetworkStatus
import com.pdtoscillo.core.scpi.ScpiException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

/** 接続画面の状態。 */
data class ConnectionUiState(
    val hostInput: String = "",
    val portInput: String = ConnectionConfig.DEFAULT_PORT.toString(),
    val transportType: TransportType = TransportType.RAW_SOCKET,
    val bindStrategy: SocketBindStrategy = SocketBindStrategy.ETHERNET_SOCKET_FACTORY,
    val terminator: LineTerminator = LineTerminator.LF,
    val autoReconnect: Boolean = true,
    val connectionState: ConnectionState = ConnectionState.Disconnected,
    val networkStatus: NetworkStatus = NetworkStatus.UNKNOWN,
    val identity: InstrumentIdentity? = null,
    val capabilities: InstrumentCapabilities? = null,
    val readOnlyMode: Boolean = true,
    val busy: Boolean = false,
    val busyLabel: String = "",
    val error: ScopeError? = null,
    val errorRemedy: String? = null,
    val diagnosticSteps: List<DiagnosticStep> = emptyList(),
    val diagnosticReport: DiagnosticReport? = null,
    val discovering: Boolean = false,
    val discoveryProgress: DiscoveryProgress? = null,
    val discoveredDevices: List<DiscoveredDevice> = emptyList(),
    val savedDevices: List<SavedDeviceUi> = emptyList(),
    val wizardVisible: Boolean = false,
) {
    val portError: Boolean get() = portInput.toIntOrNull()?.let { it !in 1..MAX_PORT } ?: portInput.isNotEmpty()
    val hostError: Boolean get() = hostInput.isNotBlank() && !looksLikeHostOrIp(hostInput)
    val canConnect: Boolean get() = hostInput.isNotBlank() && !hostError && !portError && !busy

    /** e*Scope を開けるか。HTTP ポートが開いていると分かっている場合のみ提示する。 */
    val escopeUrl: String?
        get() = if (connectionState.isConnected || discoveredDevices.any { it.host == hostInput && it.hasHttpPort }) {
            "http://$hostInput/"
        } else {
            null
        }

    companion object {
        const val MAX_PORT = 65535

        private fun looksLikeHostOrIp(value: String): Boolean {
            val trimmed = value.trim()
            if (trimmed.isEmpty()) return false
            // IPv4 らしい入力は各オクテットを検証する。ホスト名も許容する。
            val octets = trimmed.split('.')
            if (octets.size == 4 && octets.all { it.toIntOrNull() != null }) {
                return octets.all { (it.toIntOrNull() ?: -1) in 0..255 }
            }
            return trimmed.all { it.isLetterOrDigit() || it == '.' || it == '-' || it == ':' }
        }
    }
}

/** 保存済み機器の表示用。永続化は Phase 6 で Room へ移す。 */
data class SavedDeviceUi(val label: String, val host: String, val port: Int, val lastIdentity: String?)

class ConnectionViewModel(private val session: InstrumentSession) : ViewModel() {

    private val _uiState = MutableStateFlow(ConnectionUiState())
    val uiState: StateFlow<ConnectionUiState> = _uiState.asStateFlow()

    private var discoveryJob: Job? = null

    init {
        session.start()

        // 接続状態・識別結果・Capability・読み取り専用モードを 1 つの UI 状態へまとめる。
        combine(
            session.client.connectionState,
            session.networkStatus,
            session.client.identity,
            session.client.capabilities,
            session.client.readOnlyMode,
        ) { state, network, identity, capabilities, readOnly ->
            Quintuple(state, network, identity, capabilities, readOnly)
        }.onEach { (state, network, identity, capabilities, readOnly) ->
            _uiState.value = _uiState.value.copy(
                connectionState = state,
                networkStatus = network,
                identity = identity,
                capabilities = capabilities,
                readOnlyMode = readOnly,
                error = (state as? ConnectionState.Failed)?.error ?: _uiState.value.error,
            )
        }.launchIn(viewModelScope)

        session.client.pendingCount.onEach { pending ->
            if (pending == 0 && _uiState.value.busyLabel == COMMUNICATING) {
                _uiState.value = _uiState.value.copy(busy = false, busyLabel = "")
            }
        }.launchIn(viewModelScope)

        val remembered = session.lastConfig.value
        if (remembered.host.isNotBlank()) {
            _uiState.value = _uiState.value.copy(
                hostInput = remembered.host,
                portInput = remembered.port.toString(),
                bindStrategy = remembered.bindStrategy,
                terminator = remembered.terminator,
                autoReconnect = remembered.autoReconnect,
            )
        }
    }

    private data class Quintuple<A, B, C, D, E>(val first: A, val second: B, val third: C, val fourth: D, val fifth: E)

    fun onHostChange(value: String) {
        _uiState.value = _uiState.value.copy(hostInput = value.trim(), error = null)
    }

    fun onPortChange(value: String) {
        _uiState.value = _uiState.value.copy(portInput = value.filter { it.isDigit() }, error = null)
    }

    fun onTransportChange(value: TransportType) {
        _uiState.value = _uiState.value.copy(transportType = value)
    }

    fun onBindStrategyChange(value: SocketBindStrategy) {
        _uiState.value = _uiState.value.copy(bindStrategy = value)
    }

    fun onTerminatorChange(value: LineTerminator) {
        _uiState.value = _uiState.value.copy(terminator = value)
    }

    fun onAutoReconnectChange(value: Boolean) {
        _uiState.value = _uiState.value.copy(autoReconnect = value)
    }

    fun setWizardVisible(visible: Boolean) {
        _uiState.value = _uiState.value.copy(wizardVisible = visible)
    }

    fun setReadOnlyMode(enabled: Boolean) {
        session.client.setReadOnlyMode(enabled)
    }

    fun currentConfig(): ConnectionConfig {
        val state = _uiState.value
        return ConnectionConfig(
            host = state.hostInput.trim(),
            port = state.portInput.toIntOrNull() ?: ConnectionConfig.DEFAULT_PORT,
            transportType = state.transportType,
            bindStrategy = state.bindStrategy,
            terminator = state.terminator,
            autoReconnect = state.autoReconnect,
        )
    }

    fun connect() {
        val state = _uiState.value
        if (!state.canConnect) return
        if (state.transportType == TransportType.VXI11) {
            // 未実装の方式を選んだ場合は、黙って別の方式へ切り替えず理由を示す。
            showError(
                ScopeError.Unknown(
                    "VXI-11 は本アプリでは未実装です。docs/vxi11-feasibility.md に理由と工数を記載しています。" +
                        "Raw Socket を選択してください。",
                ),
            )
            return
        }

        val config = currentConfig()
        session.rememberConfig(config)
        launchBusy("接続中") {
            session.client.connect(config)
            session.client.identify()
            session.client.detectCapabilities()
            addSavedDevice(config)
        }
    }

    fun disconnect() {
        launchBusy("切断中") { session.client.disconnect() }
    }

    /** 接続診断。接続していない状態でも実行でき、どこで止まっているかを示す。 */
    fun runDiagnostics() {
        val config = currentConfig()
        if (config.host.isBlank()) {
            showError(ScopeError.Unknown("IP アドレスを入力してください。"))
            return
        }
        session.rememberConfig(config)
        _uiState.value = _uiState.value.copy(diagnosticSteps = emptyList(), diagnosticReport = null)
        launchBusy("診断中") {
            val report = session.diagnostics.run(config) { step ->
                _uiState.value = _uiState.value.copy(diagnosticSteps = _uiState.value.diagnosticSteps + step)
            }
            _uiState.value = _uiState.value.copy(
                diagnosticReport = report,
                error = report.lastError,
                errorRemedy = report.lastError?.let { ConnectionDiagnostics.remedyFor(it, config) },
            )
        }
    }

    /**
     * サブネット内の限定探索を開始する。
     * 進行中は再実行せず、利用者が停止できるようにする。
     */
    fun startDiscovery() {
        if (_uiState.value.discovering) return
        val localAddress = _uiState.value.networkStatus.ethernetLink?.primaryIpv4?.address
        if (localAddress == null) {
            showError(
                ScopeError.EthernetUnavailable(
                    "Ethernet のアドレスが分からないため探索できません。IP アドレスを直接入力してください。",
                ),
            )
            return
        }

        _uiState.value = _uiState.value.copy(
            discovering = true,
            discoveredDevices = emptyList(),
            discoveryProgress = null,
            error = null,
        )
        discoveryJob = viewModelScope.launch {
            try {
                session.discovery.scanSubnet(
                    localAddress = localAddress,
                    scpiPort = _uiState.value.portInput.toIntOrNull() ?: ConnectionConfig.DEFAULT_PORT,
                    onProgress = { progress ->
                        _uiState.value = _uiState.value.copy(discoveryProgress = progress)
                    },
                ).collect { device ->
                    _uiState.value = _uiState.value.copy(
                        discoveredDevices = _uiState.value.discoveredDevices + device,
                    )
                }
            } finally {
                _uiState.value = _uiState.value.copy(discovering = false)
            }
        }
    }

    fun stopDiscovery() {
        discoveryJob?.cancel()
        discoveryJob = null
        _uiState.value = _uiState.value.copy(discovering = false)
    }

    fun selectDiscovered(device: DiscoveredDevice) {
        _uiState.value = _uiState.value.copy(
            hostInput = device.host,
            portInput = device.port.toString(),
        )
    }

    fun selectSaved(device: SavedDeviceUi) {
        _uiState.value = _uiState.value.copy(
            hostInput = device.host,
            portInput = device.port.toString(),
        )
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null, errorRemedy = null)
    }

    private fun addSavedDevice(config: ConnectionConfig) {
        val identity = session.client.identity.value
        val label = identity?.model?.takeIf { it.isNotBlank() } ?: config.host
        val entry = SavedDeviceUi(label, config.host, config.port, identity?.raw)
        val existing = _uiState.value.savedDevices.filterNot { it.host == entry.host && it.port == entry.port }
        _uiState.value = _uiState.value.copy(savedDevices = (listOf(entry) + existing).take(MAX_SAVED_DEVICES))
    }

    private fun launchBusy(label: String, block: suspend () -> Unit) {
        _uiState.value = _uiState.value.copy(busy = true, busyLabel = label, error = null, errorRemedy = null)
        viewModelScope.launch {
            try {
                block()
            } catch (exception: ScpiException) {
                showError(exception.error)
            } catch (exception: Exception) {
                showError(ScopeError.Unknown(exception.message, exception))
            } finally {
                _uiState.value = _uiState.value.copy(busy = false, busyLabel = "")
            }
        }
    }

    private fun showError(error: ScopeError) {
        _uiState.value = _uiState.value.copy(
            error = error,
            errorRemedy = ConnectionDiagnostics.remedyFor(error, currentConfig()),
        )
    }

    override fun onCleared() {
        discoveryJob?.cancel()
        super.onCleared()
    }

    companion object {
        private const val COMMUNICATING = "通信中"
        private const val MAX_SAVED_DEVICES = 10

        fun factory(session: InstrumentSession): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = ConnectionViewModel(session) as T
        }
    }
}
