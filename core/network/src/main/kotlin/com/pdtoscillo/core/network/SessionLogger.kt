package com.pdtoscillo.core.network

import android.content.Context
import android.os.Build
import com.pdtoscillo.core.common.CommunicationLogEntry
import com.pdtoscillo.core.common.PdtLog
import com.pdtoscillo.core.common.SessionLogWriter
import com.pdtoscillo.core.model.ConnectionConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

/** 記録中のログの状態。UI へ出す。 */
data class SessionLogState(
    val recording: Boolean = false,
    val fileName: String? = null,
    val filePath: String? = null,
    val sizeBytes: Long = 0,
    val entryCount: Int = 0,
    val startedAtEpochMillis: Long? = null,
    val truncated: Boolean = false,
)

/**
 * 実機接続時の調査用ログ。
 *
 * 実機で最初に接続するときは、何が起きたかを後から追えることが何より重要になる。
 * メモリ上のリングバッファ（[com.pdtoscillo.core.common.CommunicationLogRecorder]）は
 * 件数上限があり、アプリを閉じると消えるため、それとは別にファイルへ残す。
 *
 * 記録する内容:
 * - 端末とアプリの情報（機種、Android バージョン）
 * - ネットワークの状態（Ethernet の有無、IP、サブネット、ゲートウェイ、DNS、
 *   同時に有効な経路）
 * - 接続設定（IP、ポート、バインド方式、終端）
 * - 経路検証の結果（モバイル回線へ出ていないか）
 * - **送信した SCPI コマンドと応答のすべて**（所要時間とエラー分類つき）
 * - アプリ内部の警告・エラー
 *
 * バイナリ応答の本体は書かない。サイズと SHA-256 だけを残す。
 * 波形 1 回分でも数 MB になり、ログとして読めなくなるため。
 */
class SessionLogger(
    private val context: Context,
    private val logRecorder: com.pdtoscillo.core.common.CommunicationLogRecorder,
    private val networkMonitor: EthernetNetworkMonitor,
) {
    private val scope = CoroutineScope(SupervisorJob())
    private var collectJob: Job? = null
    private var writer: SessionLogWriter? = null

    /** どこまでファイルへ書いたか。同じ行を二重に書かないために持つ。 */
    private var lastWrittenId: Long = 0

    private val _state = MutableStateFlow(SessionLogState())
    val state: StateFlow<SessionLogState> = _state.asStateFlow()

    /** ログの保存先。アプリ専用ディレクトリ。 */
    val logDirectory: File
        get() = File(context.filesDir, LOG_DIRECTORY).apply { mkdirs() }

    /**
     * 記録を開始する。
     *
     * 既に記録中なら何もしない。開始時点のネットワーク状態をヘッダへ書く。
     */
    fun start(config: ConnectionConfig? = null) {
        if (_state.value.recording) return

        val startedAt = System.currentTimeMillis()
        val file = File(logDirectory, SessionLogWriter.buildFileName(startedAt))
        val newWriter = SessionLogWriter(file)
        writer = newWriter
        lastWrittenId = 0

        newWriter.begin(buildHeader(startedAt, config))
        writeNetworkSnapshot(newWriter)

        _state.value = SessionLogState(
            recording = true,
            fileName = file.name,
            filePath = file.absolutePath,
            sizeBytes = newWriter.writtenBytes,
            entryCount = 0,
            startedAtEpochMillis = startedAt,
        )

        // 通信ログの流れをファイルへ写す。完了した行だけを書く。
        collectJob = scope.launch {
            logRecorder.entries.collect { entries ->
                appendNewEntries(entries)
            }
        }

        // アプリ内部の警告・エラーもファイルへ残す。
        PdtLog.install { level, tag, message, throwable ->
            androidLogSink(level, tag, message, throwable)
            if (level == PdtLog.Level.WARN || level == PdtLog.Level.ERROR) {
                writer?.append("[$level] $tag: $message${throwable?.let { " (${it.message})" } ?: ""}")
                refreshState()
            }
        }

        PdtLog.i(TAG, "ログの記録を開始しました: ${file.absolutePath}")
    }

    /** 記録を停止する。ファイルはそのまま残る。 */
    fun stop() {
        if (!_state.value.recording) return
        collectJob?.cancel()
        collectJob = null

        writer?.appendSection("記録終了")
        refreshState()
        _state.value = _state.value.copy(recording = false)
        writer = null

        // 通常のログ出力へ戻す。
        PdtLog.install(::androidLogSink)
    }

    /**
     * 任意のメモを書く。
     *
     * 接続診断の結果など、SCPI 通信ではない情報を残すために使う。
     */
    fun note(text: String) {
        val target = writer ?: return
        target.append(text)
        refreshState()
    }

    fun section(title: String) {
        val target = writer ?: return
        target.appendSection(title)
        refreshState()
    }

    /** 複数行をそのまま書く。診断結果の一覧などに使う。 */
    fun block(text: String) {
        val target = writer ?: return
        target.appendBlock(text)
        refreshState()
    }

    /** 保存済みのログファイル。新しい順。 */
    fun listLogs(): List<File> = logDirectory.listFiles()?.filter { it.isFile }?.sortedByDescending { it.lastModified() } ?: emptyList()

    /** ログファイルを削除する。保存先の外は触らない。 */
    fun delete(fileName: String): Boolean = runCatching {
        val base = logDirectory.canonicalFile
        val target = File(base, File(fileName).name).canonicalFile
        if (target.parentFile != base) return@runCatching false

        // 記録中のファイルは消さない。
        // Android では filesDir がシンボリックリンク（/data/user/0/... と /data/data/...）
        // になっているため、絶対パス同士の比較では一致しない。必ず正規化して比べる。
        val recording = _state.value.filePath?.let { File(it).canonicalFile }
        if (recording != null && target == recording) return@runCatching false

        target.delete()
    }.getOrDefault(false)

    /** 記録中のファイル。共有や表示に使う。 */
    fun currentFile(): File? = _state.value.filePath?.let(::File)

    private fun appendNewEntries(entries: List<CommunicationLogEntry>) {
        val target = writer ?: return
        var written = 0
        entries
            .filter { it.id > lastWrittenId && it.outcome != CommunicationLogEntry.Outcome.PENDING }
            .sortedBy { it.id }
            .forEach { entry ->
                target.append(formatEntry(entry))
                lastWrittenId = entry.id
                written++
            }
        if (written > 0) refreshState()
    }

    /**
     * 1 件を 1 行にする。
     *
     * 送ったコマンドと返ってきた内容、所要時間、エラー分類が 1 行で読めるようにする。
     */
    private fun formatEntry(entry: CommunicationLogEntry): String = buildString {
        append(
            when (entry.outcome) {
                CommunicationLogEntry.Outcome.SUCCESS -> "OK  "
                CommunicationLogEntry.Outcome.FAILURE -> "NG  "
                CommunicationLogEntry.Outcome.CANCELLED -> "CANCEL"
                CommunicationLogEntry.Outcome.REJECTED -> "REJECT"
                CommunicationLogEntry.Outcome.PENDING -> "...."
            },
        )
        append(" [${entry.kind}] -> ${entry.command}")
        entry.durationMillis?.let { append(" ($it ms)") }
        entry.responsePreview?.let { append("\n     <- $it") }
        if (entry.responseByteCount != null && entry.responsePreview == null) {
            append("\n     <- (バイナリ) ${entry.responseByteCount} バイト")
            entry.responseSha256?.let { append(" SHA-256 ${it.take(SHA_PREVIEW)}") }
        }
        entry.error?.let {
            append("\n     !! ${it::class.simpleName}: ${it.detail ?: ""}")
        }
    }

    private fun buildHeader(startedAt: Long, config: ConnectionConfig?): String = buildString {
        appendLine("PDToscillo セッションログ")
        appendLine("=".repeat(HEADER_RULE_LENGTH))
        appendLine("開始時刻      : ${java.util.Date(startedAt)}")
        appendLine("端末          : ${Build.MANUFACTURER} ${Build.MODEL}")
        appendLine("Android       : ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
        appendLine("端末の製品名  : ${Build.PRODUCT} / ${Build.DEVICE}")
        if (config != null) {
            appendLine("接続先        : ${config.host}:${config.port}")
            appendLine("通信方式      : ${config.transportType}")
            appendLine("バインド方式  : ${config.bindStrategy}")
            appendLine("コマンド終端  : ${config.terminator}")
            appendLine("自動再接続    : ${config.autoReconnect}")
        }
        appendLine("=".repeat(HEADER_RULE_LENGTH))
        appendLine()
        appendLine("このファイルには送受信した SCPI コマンドと応答がすべて記録されます。")
        appendLine("バイナリ応答の本体は含まず、サイズと SHA-256 のみ記録します。")
        appendLine()
    }

    /**
     * ネットワークの状態を書く。
     *
     * 「繋がらない」ときに最初に見る情報。Ethernet が見えているか、
     * 同時にモバイル回線が有効になっていないかがここで分かる。
     */
    private fun writeNetworkSnapshot(target: SessionLogWriter) {
        networkMonitor.refresh()
        val status = networkMonitor.status.value

        target.appendSection("ネットワークの状態")
        target.append("Ethernet 検出 : ${if (status.ethernetAvailable) "あり" else "なし"}")
        target.append("有効な経路    : ${status.activeTransports.joinToString().ifEmpty { "不明" }}")

        status.ethernetLink?.let { link ->
            target.append("インターフェース: ${link.interfaceName ?: "不明"}")
            link.addresses.forEach { address ->
                target.append("  アドレス    : ${address.address}/${address.prefixLength}" + (address.subnetMask?.let { " (mask $it)" } ?: ""))
            }
            target.append("  ゲートウェイ: ${link.gateways.joinToString().ifEmpty { "なし（直結では正常）" }}")
            target.append("  DNS         : ${link.dnsServers.joinToString().ifEmpty { "なし" }}")
            target.append("  MTU         : ${link.mtu ?: "不明"}")
        }

        // ConnectivityManager が Ethernet を報告しない機種の切り分けに使う。
        target.append("OS が見せているインターフェース一覧:")
        status.systemInterfaces.forEach { nic ->
            target.append(
                "  ${nic.name} up=${nic.isUp} loopback=${nic.isLoopback} " +
                    "ethernetらしい=${nic.looksLikeEthernet} " +
                    "addr=${nic.addresses.joinToString { it.toString() }.ifEmpty { "なし" }}",
            )
        }
        target.appendSection("通信ログ")
    }

    private fun refreshState() {
        val target = writer ?: return
        _state.value = _state.value.copy(
            sizeBytes = target.writtenBytes,
            entryCount = lastWrittenId.toInt(),
            truncated = target.isTruncated,
        )
    }

    private fun androidLogSink(level: PdtLog.Level, tag: String, message: String, throwable: Throwable?) {
        when (level) {
            PdtLog.Level.VERBOSE -> android.util.Log.v(tag, message, throwable)
            PdtLog.Level.DEBUG -> android.util.Log.d(tag, message, throwable)
            PdtLog.Level.INFO -> android.util.Log.i(tag, message, throwable)
            PdtLog.Level.WARN -> android.util.Log.w(tag, message, throwable)
            PdtLog.Level.ERROR -> android.util.Log.e(tag, message, throwable)
        }
    }

    private companion object {
        const val TAG = "SessionLogger"
        const val LOG_DIRECTORY = "logs"
        const val SHA_PREVIEW = 12
        const val HEADER_RULE_LENGTH = 60
    }
}
