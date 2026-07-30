package com.pdtoscillo.core.common

import com.pdtoscillo.core.model.ScopeError
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicLong

/** 通信 1 件の記録。 */
data class CommunicationLogEntry(
    val id: Long,
    val startedAtEpochMillis: Long,
    val finishedAtEpochMillis: Long?,
    val command: String,
    val kind: Kind,
    val outcome: Outcome,
    /** テキスト応答の先頭。バイナリ応答の場合は null。 */
    val responsePreview: String?,
    /** バイナリ応答のバイト数。ログには本体を残さない。 */
    val responseByteCount: Long?,
    /** バイナリ応答のハッシュ。同一性の確認に使う。 */
    val responseSha256: String?,
    val error: ScopeError?,
) {
    enum class Kind {
        /** 応答を待たない設定変更。 */
        WRITE,

        /** テキスト応答を待つ問い合わせ。 */
        QUERY,

        /** バイナリ応答を待つ問い合わせ。 */
        QUERY_BINARY,
    }

    enum class Outcome { PENDING, SUCCESS, FAILURE, CANCELLED, REJECTED }

    val durationMillis: Long?
        get() = finishedAtEpochMillis?.let { it - startedAtEpochMillis }
}

/**
 * 通信ログの記録先。
 *
 * 長時間動作でメモリを食い潰さないよう、保持件数に上限を設けたリングバッファとして扱う。
 * 永続化は上位（`core:database`）が担う。
 */
class CommunicationLogRecorder(private val capacity: Int = DEFAULT_CAPACITY, private val clock: () -> Long = System::currentTimeMillis) {
    private val nextId = AtomicLong(1)
    private val _entries = MutableStateFlow<List<CommunicationLogEntry>>(emptyList())
    val entries: StateFlow<List<CommunicationLogEntry>> = _entries.asStateFlow()

    fun begin(command: String, kind: CommunicationLogEntry.Kind): Long {
        val entry = CommunicationLogEntry(
            id = nextId.getAndIncrement(),
            startedAtEpochMillis = clock(),
            finishedAtEpochMillis = null,
            command = command,
            kind = kind,
            outcome = CommunicationLogEntry.Outcome.PENDING,
            responsePreview = null,
            responseByteCount = null,
            responseSha256 = null,
            error = null,
        )
        append(entry)
        return entry.id
    }

    fun completeText(id: Long, response: String) {
        update(id) { entry ->
            entry.copy(
                finishedAtEpochMillis = clock(),
                outcome = CommunicationLogEntry.Outcome.SUCCESS,
                responsePreview = response.take(PREVIEW_LENGTH),
                responseByteCount = response.length.toLong(),
            )
        }
    }

    fun completeBinary(id: Long, payload: ByteArray) {
        update(id) { entry ->
            entry.copy(
                finishedAtEpochMillis = clock(),
                outcome = CommunicationLogEntry.Outcome.SUCCESS,
                responseByteCount = payload.size.toLong(),
                responseSha256 = Digest.sha256(payload),
            )
        }
    }

    fun completeWrite(id: Long) {
        update(id) { entry ->
            entry.copy(finishedAtEpochMillis = clock(), outcome = CommunicationLogEntry.Outcome.SUCCESS)
        }
    }

    fun fail(id: Long, error: ScopeError) {
        val outcome = when (error) {
            is ScopeError.Cancelled -> CommunicationLogEntry.Outcome.CANCELLED
            is ScopeError.ReadOnlyModeRejected -> CommunicationLogEntry.Outcome.REJECTED
            else -> CommunicationLogEntry.Outcome.FAILURE
        }
        update(id) { entry ->
            entry.copy(finishedAtEpochMillis = clock(), outcome = outcome, error = error)
        }
    }

    fun clear() {
        _entries.value = emptyList()
    }

    private fun append(entry: CommunicationLogEntry) {
        _entries.value = (_entries.value + entry).let { list ->
            if (list.size > capacity) list.subList(list.size - capacity, list.size).toList() else list
        }
    }

    private fun update(id: Long, transform: (CommunicationLogEntry) -> CommunicationLogEntry) {
        _entries.value = _entries.value.map { entry -> if (entry.id == id) transform(entry) else entry }
    }

    companion object {
        const val DEFAULT_CAPACITY: Int = 500
        private const val PREVIEW_LENGTH = 200
    }
}
