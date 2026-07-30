package com.pdtoscillo.feature.files

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.pdtoscillo.core.common.Digest
import com.pdtoscillo.core.model.ScopeError
import com.pdtoscillo.core.network.ConnectionDiagnostics
import com.pdtoscillo.core.network.InstrumentSession
import com.pdtoscillo.core.scpi.InstrumentFile
import com.pdtoscillo.core.scpi.InstrumentFileController
import com.pdtoscillo.core.scpi.ScpiException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

/** アプリへ取り込んだファイル。 */
data class DownloadedFile(val name: String, val path: String, val sizeBytes: Long, val sha256: String, val downloadedAtEpochMillis: Long)

/** 確認が必要な操作。 */
sealed interface FileConfirmation {
    data class Delete(val name: String) : FileConfirmation

    data class RecallSetup(val name: String) : FileConfirmation
}

data class FilesUiState(
    val currentDirectory: String? = null,
    val files: List<InstrumentFile> = emptyList(),
    val downloads: List<DownloadedFile> = emptyList(),
    val freeSpaceBytes: Long? = null,
    val busy: Boolean = false,
    val busyLabel: String = "",
    val readOnlyMode: Boolean = true,
    val error: ScopeError? = null,
    val errorRemedy: String? = null,
    val notice: String? = null,
    val saveNameInput: String = "",
    val pendingConfirmation: FileConfirmation? = null,
)

/**
 * 本体のファイル操作。
 *
 * 本体から返る名前をそのまま操作コマンドへ差し込まない。
 * [InstrumentFileController] が検証し、危険な名前は拒否する。
 */
class FilesViewModel(private val session: InstrumentSession, private val downloadDirectory: File) : ViewModel() {

    private val controller = InstrumentFileController(session.client)

    private val _uiState = MutableStateFlow(FilesUiState())
    val uiState: StateFlow<FilesUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            session.client.readOnlyMode.collect { readOnly ->
                _uiState.value = _uiState.value.copy(readOnlyMode = readOnly)
            }
        }
    }

    fun onVisible() = refresh()

    fun refresh() = launchBusy("一覧取得") {
        _uiState.value = _uiState.value.copy(
            currentDirectory = controller.currentDirectory(),
            files = controller.listFiles(),
            freeSpaceBytes = controller.freeSpaceBytes(),
        )
    }

    fun onSaveNameChange(value: String) {
        _uiState.value = _uiState.value.copy(saveNameInput = value)
    }

    fun saveSetup() = withValidatedName { name ->
        launchBusy("設定を保存") {
            controller.saveSetup(name).fold(
                onSuccess = { notify("設定を $name へ保存しました") },
                onFailure = { reportFailure(it) },
            )
            refreshQuietly()
        }
    }

    fun saveImage() = withValidatedName { name ->
        launchBusy("画面イメージを保存") {
            controller.saveImage(name).fold(
                onSuccess = { notify("画面イメージを $name へ保存しました") },
                onFailure = { reportFailure(it) },
            )
            refreshQuietly()
        }
    }

    fun saveWaveform(source: String) = withValidatedName { name ->
        launchBusy("波形を保存") {
            controller.saveWaveform(source, name).fold(
                onSuccess = { notify("$source の波形を $name へ保存しました") },
                onFailure = { reportFailure(it) },
            )
            refreshQuietly()
        }
    }

    /** 呼び出しは現在の設定を失うため確認する。 */
    fun requestRecallSetup(name: String) {
        _uiState.value = _uiState.value.copy(pendingConfirmation = FileConfirmation.RecallSetup(name))
    }

    /** 削除は取り消せないため確認する。 */
    fun requestDelete(name: String) {
        _uiState.value = _uiState.value.copy(pendingConfirmation = FileConfirmation.Delete(name))
    }

    fun dismissConfirmation() {
        _uiState.value = _uiState.value.copy(pendingConfirmation = null)
    }

    fun confirmPending() {
        val confirmation = _uiState.value.pendingConfirmation ?: return
        _uiState.value = _uiState.value.copy(pendingConfirmation = null)
        when (confirmation) {
            is FileConfirmation.Delete -> launchBusy("削除") {
                controller.delete(confirmation.name).fold(
                    onSuccess = { notify("${confirmation.name} を削除しました") },
                    onFailure = { reportFailure(it) },
                )
                refreshQuietly()
            }

            is FileConfirmation.RecallSetup -> launchBusy("設定を呼び出し") {
                controller.recallSetup(confirmation.name).fold(
                    onSuccess = { notify("${confirmation.name} を呼び出しました") },
                    onFailure = { reportFailure(it) },
                )
            }
        }
    }

    /**
     * 本体のファイルをアプリへ取り込む。
     *
     * 保存先はアプリ専用ディレクトリ。本体側の名前をそのままパスに使わず、
     * 危険な文字を取り除いてから連結する。
     */
    fun download(name: String) = launchBusy("ダウンロード") {
        controller.readFile(name).fold(
            onSuccess = { payload ->
                val safeName = name
                    .replace('\\', '_')
                    .replace('/', '_')
                    .replace("..", "_")
                    .takeIf { it.isNotBlank() } ?: "downloaded.bin"
                downloadDirectory.mkdirs()
                val target = File(downloadDirectory, safeName)
                target.writeBytes(payload)

                val entry = DownloadedFile(
                    name = safeName,
                    path = target.absolutePath,
                    sizeBytes = payload.size.toLong(),
                    sha256 = Digest.sha256(payload),
                    downloadedAtEpochMillis = System.currentTimeMillis(),
                )
                _uiState.value = _uiState.value.copy(
                    downloads = (listOf(entry) + _uiState.value.downloads).take(MAX_DOWNLOADS),
                    notice = "$safeName を取り込みました（${payload.size} バイト）",
                )
            },
            onFailure = { reportFailure(it) },
        )
    }

    fun clearNotice() {
        _uiState.value = _uiState.value.copy(notice = null)
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null, errorRemedy = null)
    }

    /** 名前を検証してから操作する。拒否理由はそのまま利用者へ見せる。 */
    private fun withValidatedName(block: (String) -> Unit) {
        val name = _uiState.value.saveNameInput
        controller.validateName(name).fold(
            onSuccess = { block(it) },
            onFailure = { reportFailure(it) },
        )
    }

    private suspend fun refreshQuietly() {
        _uiState.value = _uiState.value.copy(files = controller.listFiles())
    }

    private fun notify(message: String) {
        _uiState.value = _uiState.value.copy(notice = message)
    }

    private fun reportFailure(throwable: Throwable) {
        val error = InstrumentFileController.toScopeError(throwable)
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
                reportFailure(exception)
            } catch (exception: Exception) {
                _uiState.value = _uiState.value.copy(error = ScopeError.Unknown(exception.message, exception))
            } finally {
                _uiState.value = _uiState.value.copy(busy = false, busyLabel = "")
            }
        }
    }

    companion object {
        private const val MAX_DOWNLOADS = 20

        fun factory(session: InstrumentSession, downloadDirectory: File): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = FilesViewModel(session, downloadDirectory) as T
        }
    }
}
