package com.pdtoscillo.core.scpi

import com.pdtoscillo.core.common.CommunicationLogEntry
import com.pdtoscillo.core.common.CommunicationLogRecorder
import com.pdtoscillo.core.common.PdtLog
import com.pdtoscillo.core.model.ConnectionConfig
import com.pdtoscillo.core.model.InstrumentTransport
import com.pdtoscillo.core.model.ScopeError
import com.pdtoscillo.core.model.TimeoutAwareTransport
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

/**
 * 1 接続に 1 つだけ持つ直列キュー。
 *
 * オシロスコープは単一のコマンド解釈器を持つため、Query の応答を読み切る前に別の Query を送ると
 * 応答の対応関係が崩れる。ここで直列化してそれを防ぐ。
 *
 * 重要な設計点:
 * - **同時に複数の Query を送らない。** [Mutex] は FIFO で獲得されるため送信順も保たれる。
 * - **キャンセル時に受信ストリームを壊さない。** 読み取り中に中断された場合は同期喪失として記録し、
 *   次のコマンドの前に受信バッファを破棄する。破棄しても回復できない場合は上位が再接続する。
 * - **読み取り専用モードはここで強制する。** UI 側の確認漏れがあっても設定変更は通らない。
 * - ログにはバイナリ本体を残さず、サイズとハッシュだけを記録する。
 */
class ScpiCommandQueue(
    private val transport: InstrumentTransport,
    private val logRecorder: CommunicationLogRecorder,
    private val configProvider: () -> ConnectionConfig?,
) {
    private val mutex = Mutex()

    private val _pendingCount = MutableStateFlow(0)

    /** 実行待ち + 実行中のコマンド数。UI の「通信中」表示に使う。 */
    val pendingCount: StateFlow<Int> = _pendingCount.asStateFlow()

    private val _readOnlyMode = MutableStateFlow(true)

    /** 接続直後は必ず読み取り専用。明示的に解除するまで設定変更を通さない。 */
    val readOnlyMode: StateFlow<Boolean> = _readOnlyMode.asStateFlow()

    private val _needsResynchronization = MutableStateFlow(false)

    /** 応答を読み切れずストリーム同期を失った状態。上位はこれを見て再接続を判断できる。 */
    val needsResynchronization: StateFlow<Boolean> = _needsResynchronization.asStateFlow()

    fun setReadOnlyMode(enabled: Boolean) {
        _readOnlyMode.value = enabled
        PdtLog.i(TAG, if (enabled) "読み取り専用モードを有効化" else "設定変更を許可")
    }

    /** 設定変更を許可する。危険度が [DangerLevel.DANGEROUS] のコマンドは別途確認が必要。 */
    fun isWriteAllowed(command: ScpiCommand): Boolean = !_readOnlyMode.value || command.dangerLevel == DangerLevel.SAFE

    /** 応答を待たない設定変更を送る。 */
    suspend fun write(command: ScpiCommand.Write) {
        execute(command, CommunicationLogEntry.Kind.WRITE) {
            transport.write(command.text.toByteArray(Charsets.US_ASCII))
        }
    }

    /** テキスト応答を待つ。 */
    suspend fun query(command: ScpiCommand): String = execute(command, CommunicationLogEntry.Kind.QUERY) {
        transport.queryText(command.text)
    }

    /** バイナリ応答（IEEE 488.2 ブロック）を待つ。 */
    suspend fun queryBinary(command: ScpiCommand.BinaryQuery): ByteArray = execute(command, CommunicationLogEntry.Kind.QUERY_BINARY) {
        transport.queryBinary(command.text)
    }

    /**
     * 未対応の可能性がある問い合わせを実行する。
     *
     * 未定義ヘッダーには応答が返らないため、タイムアウトを「未対応の可能性」として
     * 例外ではなく戻り値で表す。呼び出し側はこの後エラーキューを確認して確定させる。
     */
    suspend fun probe(command: ScpiCommand.ProbeQuery): ProbeResult = try {
        ProbeResult.Responded(query(command))
    } catch (error: ScpiException) {
        when (error.error) {
            is ScopeError.ReadTimeout -> ProbeResult.NoResponse
            else -> ProbeResult.Failed(error.error)
        }
    }

    sealed interface ProbeResult {
        data class Responded(val response: String) : ProbeResult

        /** 応答が無かった。未定義ヘッダーの可能性が高いが、エラーキューで確定させる。 */
        data object NoResponse : ProbeResult

        data class Failed(val error: ScopeError) : ProbeResult
    }

    @Suppress("ThrowsCount")
    private suspend fun <T> execute(command: ScpiCommand, kind: CommunicationLogEntry.Kind, block: suspend () -> T): T {
        if (!isWriteAllowed(command)) {
            val error = ScopeError.ReadOnlyModeRejected(command.text)
            val logId = logRecorder.begin(command.text, kind)
            logRecorder.fail(logId, error)
            throw ScpiException(error)
        }

        _pendingCount.value += 1
        try {
            return mutex.withLock {
                resynchronizeIfNeeded()
                val logId = logRecorder.begin(command.text, kind)
                val timeout = resolveTimeout(command)
                // ソケット自身にも締め切りを与える。コルーチンのタイムアウトだけでは
                // ブロッキング読み取りが裏で走り続け、次のコマンドと衝突する。
                (transport as? TimeoutAwareTransport)?.applyOperationTimeout(timeout)
                try {
                    // withTimeout は保険。通常はソケット側の締め切りが先に効く。
                    val result = withTimeout(timeout + TIMEOUT_BACKSTOP_MARGIN_MILLIS) { block() }
                    recordSuccess(logId, kind, result)
                    result
                } catch (_: TimeoutCancellationException) {
                    // 応答を読み切れていない。次のコマンドの前に受信バッファを捨てる必要がある。
                    markDesynchronized()
                    val error = ScopeError.ReadTimeout(command.text, timeout)
                    logRecorder.fail(logId, error)
                    throw ScpiException(error)
                } catch (cancellation: CancellationException) {
                    markDesynchronized()
                    logRecorder.fail(logId, ScopeError.Cancelled)
                    throw cancellation
                } catch (error: ScpiException) {
                    // 応答を最後まで読めなかった種類のエラーは、遅れて届いた分が受信バッファに
                    // 残る可能性がある。次のコマンドがそれを自分の応答として読むと、値が 1 つ
                    // ずれた状態で静かに間違い続けるため、同期喪失として扱う。
                    if (leavesStreamUnread(error.error)) markDesynchronized()
                    logRecorder.fail(logId, error.error)
                    throw error
                } catch (error: Exception) {
                    val mapped = ScopeError.Unknown(error.message, error)
                    logRecorder.fail(logId, mapped)
                    throw ScpiException(mapped)
                }
            }
        } finally {
            _pendingCount.value = (_pendingCount.value - 1).coerceAtLeast(0)
        }
    }

    private fun <T> recordSuccess(logId: Long, kind: CommunicationLogEntry.Kind, result: T) {
        when (kind) {
            CommunicationLogEntry.Kind.WRITE -> logRecorder.completeWrite(logId)
            CommunicationLogEntry.Kind.QUERY -> logRecorder.completeText(logId, result as? String ?: "")
            CommunicationLogEntry.Kind.QUERY_BINARY ->
                logRecorder.completeBinary(logId, result as? ByteArray ?: ByteArray(0))
        }
    }

    private fun resolveTimeout(command: ScpiCommand): Long {
        command.timeoutMillis?.let { return it }
        val config = configProvider()
        return when (command) {
            is ScpiCommand.BinaryQuery ->
                config?.waveformTimeoutMillis ?: ConnectionConfig.DEFAULT_WAVEFORM_TIMEOUT_MILLIS

            is ScpiCommand.Write -> config?.readTimeoutMillis ?: ConnectionConfig.DEFAULT_READ_TIMEOUT_MILLIS
            else -> config?.queryTimeoutMillis ?: ConnectionConfig.DEFAULT_QUERY_TIMEOUT_MILLIS
        }
    }

    /** 応答を読み切れていない可能性があるエラーか。 */
    private fun leavesStreamUnread(error: ScopeError): Boolean = when (error) {
        is ScopeError.MalformedBinaryBlock,
        is ScopeError.ReadTimeout,
        is ScopeError.StreamDesynchronized,
        is ScopeError.MalformedResponse,
        -> true

        else -> false
    }

    private fun markDesynchronized() {
        _needsResynchronization.value = true
        PdtLog.w(TAG, "応答を読み切れませんでした。受信ストリームの同期を失った可能性があります。")
    }

    /**
     * 同期喪失後の復帰。
     *
     * 遅れて届いた前回の応答が次のコマンドの応答として読まれると、値が 1 つずれた状態で
     * 静かに間違い続ける。それを避けるため、コマンド送信前に受信済みバイトを捨てる。
     */
    private suspend fun resynchronizeIfNeeded() {
        if (!_needsResynchronization.value) return
        // キャンセルされていても後片付けは必ず実行する。
        val discarded = withContext(NonCancellable) {
            runCatching { transport.discardPendingInput() }.getOrDefault(0)
        }
        PdtLog.i(TAG, "受信バッファを破棄しました: $discarded バイト")
        _needsResynchronization.value = false
    }

    private companion object {
        const val TAG = "ScpiCommandQueue"

        /** ソケット側の締め切りが先に効くようにするための余裕。 */
        const val TIMEOUT_BACKSTOP_MARGIN_MILLIS = 500L
    }
}
