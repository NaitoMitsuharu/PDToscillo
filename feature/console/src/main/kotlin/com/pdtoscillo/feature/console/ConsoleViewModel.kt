package com.pdtoscillo.feature.console

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.pdtoscillo.core.common.Digest
import com.pdtoscillo.core.model.ScopeError
import com.pdtoscillo.core.network.ConnectionDiagnostics
import com.pdtoscillo.core.network.InstrumentSession
import com.pdtoscillo.core.scpi.DangerLevel
import com.pdtoscillo.core.scpi.ScpiCommand
import com.pdtoscillo.core.scpi.ScpiDangerClassifier
import com.pdtoscillo.core.scpi.ScpiErrorQueue
import com.pdtoscillo.core.scpi.ScpiEvent
import com.pdtoscillo.core.scpi.ScpiException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** コンソールの 1 行。 */
data class ConsoleEntry(
    val id: Long,
    val command: String,
    val kind: Kind,
    val response: String?,
    /** バイナリ応答の要約。本体は保持しない。 */
    val binarySummary: String?,
    val error: ScopeError?,
    val elapsedMillis: Long,
    val timestampEpochMillis: Long,
) {
    enum class Kind { WRITE, QUERY, BINARY_QUERY, INFO }

    val succeeded: Boolean get() = error == null
}

data class ConsoleUiState(
    val input: String = "",
    val entries: List<ConsoleEntry> = emptyList(),
    val history: List<String> = emptyList(),
    val favorites: List<String> = DEFAULT_FAVORITES,
    val busy: Boolean = false,
    val readOnlyMode: Boolean = true,
    /** 危険なコマンドの確認待ち。 */
    val pendingDangerousCommand: String? = null,
    val error: ScopeError? = null,
    val errorRemedy: String? = null,
    val notice: String? = null,
    /** 複数行スクリプトの入力。 */
    val scriptInput: String = "",
    val scriptRunning: Boolean = false,
) {
    /** 入力が問い合わせか。末尾が `?` なら応答を待つ。 */
    val inputIsQuery: Boolean get() = input.trim().endsWith("?")

    val inputDangerLevel: DangerLevel
        get() = if (input.isBlank()) DangerLevel.SAFE else ScpiDangerClassifier.classify(input)

    companion object {
        /**
         * よく使う問い合わせ。
         *
         * すべて安全な問い合わせのみ。設定変更コマンドを初期値へ入れると、
         * 押し間違いで本体の状態が変わる。
         */
        val DEFAULT_FAVORITES = listOf(
            "*IDN?",
            "*ESR?",
            "EVMsg?",
            "ALLEv?",
            "BUSY?",
            "ACQuire:STATE?",
            "HORizontal:RECOrdlength?",
            "HORizontal:SAMPLERate?",
            "TRIGger:STATE?",
            "WFMOutpre?",
            "CONFIGuration:ANALOg:NUMCHANnels?",
        )

        const val MAX_ENTRIES = 200
        const val MAX_HISTORY = 50
    }
}

/**
 * SCPI コンソール。
 *
 * ネイティブ画面に無い操作でも、公式マニュアルに記載された SCPI であればここから実行できる。
 * 未実装の機能があっても行き止まりにならないための逃げ道。
 *
 * 安全のため次を守る。
 * - 読み取り専用モードでは設定変更を送れない（キュー層が拒否する）
 * - 危険度の高いコマンドは確認を挟む
 * - バイナリ応答は本体を保持せず、サイズとハッシュだけを記録する
 */
class ConsoleViewModel(private val session: InstrumentSession) : ViewModel() {

    private val _uiState = MutableStateFlow(ConsoleUiState())
    val uiState: StateFlow<ConsoleUiState> = _uiState.asStateFlow()

    private var nextId = 1L

    init {
        viewModelScope.launch {
            session.client.readOnlyMode.collect { readOnly ->
                _uiState.value = _uiState.value.copy(readOnlyMode = readOnly)
            }
        }
    }

    fun onInputChange(value: String) {
        _uiState.value = _uiState.value.copy(input = value)
    }

    fun onScriptChange(value: String) {
        _uiState.value = _uiState.value.copy(scriptInput = value)
    }

    fun useFavorite(command: String) {
        _uiState.value = _uiState.value.copy(input = command)
    }

    fun addFavorite() {
        val command = _uiState.value.input.trim()
        if (command.isEmpty() || command in _uiState.value.favorites) return
        _uiState.value = _uiState.value.copy(favorites = _uiState.value.favorites + command)
    }

    fun removeFavorite(command: String) {
        _uiState.value = _uiState.value.copy(favorites = _uiState.value.favorites - command)
    }

    /**
     * 入力を実行する。
     *
     * 危険度が高い場合は即実行せず確認を求める。
     */
    fun submit() {
        val command = _uiState.value.input.trim()
        if (command.isEmpty()) return

        if (ScpiDangerClassifier.classify(command) == DangerLevel.DANGEROUS) {
            _uiState.value = _uiState.value.copy(pendingDangerousCommand = command)
            return
        }
        execute(command)
    }

    fun confirmDangerousCommand() {
        val command = _uiState.value.pendingDangerousCommand ?: return
        _uiState.value = _uiState.value.copy(pendingDangerousCommand = null)
        execute(command)
    }

    fun dismissDangerousCommand() {
        _uiState.value = _uiState.value.copy(pendingDangerousCommand = null)
    }

    private fun execute(command: String) {
        _uiState.value = _uiState.value.copy(busy = true, input = "", error = null, errorRemedy = null)
        viewModelScope.launch {
            runSingle(command)
            _uiState.value = _uiState.value.copy(busy = false)
        }
    }

    /**
     * 複数行スクリプトを実行する。
     *
     * 1 行ずつ順に送る。途中で失敗したらそこで止める。
     * 失敗を無視して続けると、前提が崩れた状態で以降のコマンドが走る。
     */
    fun runScript() {
        val lines = _uiState.value.scriptInput
            .lines()
            .map { it.substringBefore(COMMENT_PREFIX).trim() }
            .filter { it.isNotEmpty() }
        if (lines.isEmpty()) return

        // スクリプト内に危険なコマンドがあれば、実行前にまとめて確認する。
        val dangerous = lines.filter { ScpiDangerClassifier.classify(it) == DangerLevel.DANGEROUS }
        if (dangerous.isNotEmpty() && _uiState.value.pendingDangerousCommand == null) {
            _uiState.value = _uiState.value.copy(
                notice = "スクリプトに確認が必要なコマンドが含まれています: ${dangerous.joinToString()}。" +
                    "個別に実行して確認してください。",
            )
            return
        }

        _uiState.value = _uiState.value.copy(scriptRunning = true, busy = true)
        viewModelScope.launch {
            appendInfo("スクリプトを開始します（${lines.size} 行）")
            for ((index, line) in lines.withIndex()) {
                val succeeded = runSingle(line)
                if (!succeeded) {
                    appendInfo("${index + 1} 行目で失敗したため中断しました: $line")
                    break
                }
            }
            appendInfo("スクリプトを終了しました")
            _uiState.value = _uiState.value.copy(scriptRunning = false, busy = false)
        }
    }

    private suspend fun runSingle(command: String): Boolean {
        val started = System.currentTimeMillis()
        val isQuery = command.trim().endsWith("?")
        // 波形本体などのバイナリ応答は `CURVe?` に限らないため、
        // ブロック応答になり得るコマンドを明示的に判定する。
        val expectsBinary = isQuery && BINARY_QUERY_HINTS.any { command.contains(it, ignoreCase = true) }

        return try {
            when {
                expectsBinary -> {
                    val payload = session.client.queryBinary(command)
                    appendEntry(
                        command = command,
                        kind = ConsoleEntry.Kind.BINARY_QUERY,
                        response = null,
                        binarySummary = "${payload.size} バイト / SHA-256 ${Digest.shortSha256(payload)}",
                        error = null,
                        elapsed = System.currentTimeMillis() - started,
                    )
                }

                isQuery -> {
                    val response = session.client.queryText(command)
                    appendEntry(
                        command = command,
                        kind = ConsoleEntry.Kind.QUERY,
                        response = response,
                        binarySummary = null,
                        error = null,
                        elapsed = System.currentTimeMillis() - started,
                    )
                }

                else -> {
                    session.client.write(command)
                    appendEntry(
                        command = command,
                        kind = ConsoleEntry.Kind.WRITE,
                        response = null,
                        binarySummary = null,
                        error = null,
                        elapsed = System.currentTimeMillis() - started,
                    )
                }
            }
            rememberHistory(command)
            true
        } catch (exception: ScpiException) {
            appendEntry(
                command = command,
                kind = if (isQuery) ConsoleEntry.Kind.QUERY else ConsoleEntry.Kind.WRITE,
                response = null,
                binarySummary = null,
                error = exception.error,
                elapsed = System.currentTimeMillis() - started,
            )
            _uiState.value = _uiState.value.copy(
                error = exception.error,
                errorRemedy = ConnectionDiagnostics.remedyFor(exception.error, session.lastConfig.value),
            )
            false
        } catch (exception: Exception) {
            val mapped = ScopeError.Unknown(exception.message, exception)
            appendEntry(command, ConsoleEntry.Kind.WRITE, null, null, mapped, System.currentTimeMillis() - started)
            false
        }
    }

    /** エラーキューを確認する。設定変更のあとに何が起きたか調べるために使う。 */
    fun checkErrorQueue() {
        _uiState.value = _uiState.value.copy(busy = true)
        viewModelScope.launch {
            val events = runCatching { session.client.errorQueue.readEvents() }.getOrDefault(emptyList())
            if (events.isEmpty()) {
                appendInfo("エラーキューは空です")
            } else {
                events.forEach { event -> appendInfo(formatEvent(event)) }
            }
            _uiState.value = _uiState.value.copy(busy = false)
        }
    }

    private fun formatEvent(event: ScpiEvent): String = "イベント ${event.code} (${event.category}): ${event.message}"

    /** ログを文字列として書き出す。診断のために共有できる形にする。 */
    fun buildLogText(): String = buildString {
        appendLine("PDToscillo SCPI コンソールログ")
        appendLine("接続先: ${session.lastConfig.value.host}:${session.lastConfig.value.port}")
        appendLine("機器: ${session.client.identity.value?.raw ?: "不明"}")
        appendLine("---")
        _uiState.value.entries.forEach { entry ->
            appendLine("[${entry.timestampEpochMillis}] ${entry.kind} ${entry.command} (${entry.elapsedMillis} ms)")
            entry.response?.let { appendLine("  <- $it") }
            entry.binarySummary?.let { appendLine("  <- (バイナリ) $it") }
            entry.error?.let { appendLine("  !! ${it::class.simpleName}: ${it.detail}") }
        }
    }

    fun clearEntries() {
        _uiState.value = _uiState.value.copy(entries = emptyList())
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null, errorRemedy = null)
    }

    fun clearNotice() {
        _uiState.value = _uiState.value.copy(notice = null)
    }

    fun setReadOnlyMode(enabled: Boolean) = session.client.setReadOnlyMode(enabled)

    private fun appendInfo(message: String) = appendEntry(
        command = message,
        kind = ConsoleEntry.Kind.INFO,
        response = null,
        binarySummary = null,
        error = null,
        elapsed = 0,
    )

    @Suppress("LongParameterList")
    private fun appendEntry(
        command: String,
        kind: ConsoleEntry.Kind,
        response: String?,
        binarySummary: String?,
        error: ScopeError?,
        elapsed: Long,
    ) {
        val entry = ConsoleEntry(
            id = nextId++,
            command = command,
            kind = kind,
            response = response,
            binarySummary = binarySummary,
            error = error,
            elapsedMillis = elapsed,
            timestampEpochMillis = System.currentTimeMillis(),
        )
        // 長時間使ってもメモリを食い潰さないよう、保持件数に上限を設ける。
        val entries = (_uiState.value.entries + entry).takeLast(ConsoleUiState.MAX_ENTRIES)
        _uiState.value = _uiState.value.copy(entries = entries)
    }

    private fun rememberHistory(command: String) {
        val history = (listOf(command) + _uiState.value.history.filterNot { it == command })
            .take(ConsoleUiState.MAX_HISTORY)
        _uiState.value = _uiState.value.copy(history = history)
    }

    companion object {
        private const val COMMENT_PREFIX = "#"

        /** IEEE 488.2 ブロックで返る問い合わせ。 */
        private val BINARY_QUERY_HINTS = listOf("CURVE", "CURVe", "READFILE", "READFile")

        val ERROR_QUEUE_COMMANDS = listOf(
            ScpiErrorQueue.EVENT_STATUS_QUERY,
            ScpiErrorQueue.EVENT_MESSAGE_QUERY,
            ScpiErrorQueue.ALL_EVENTS_QUERY,
            ScpiErrorQueue.EVENT_QUANTITY_QUERY,
        )

        fun factory(session: InstrumentSession): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = ConsoleViewModel(session) as T
        }
    }
}

/** 送信するコマンドの種別を明示したい場合に使う。 */
fun ScpiCommand.describe(): String = when (this) {
    is ScpiCommand.Write -> "設定変更"
    is ScpiCommand.Query -> "問い合わせ"
    is ScpiCommand.BinaryQuery -> "バイナリ問い合わせ"
    is ScpiCommand.ProbeQuery -> "対応確認"
}
