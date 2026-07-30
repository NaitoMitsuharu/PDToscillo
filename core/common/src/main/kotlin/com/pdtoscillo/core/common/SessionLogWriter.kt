package com.pdtoscillo.core.common

import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * セッションログをファイルへ書き出す。
 *
 * 実機で初めて接続するときは、その場で画面を見ていられるとは限らない。
 * アプリを閉じても消えないよう、メモリ上のリングバッファとは別にファイルへ残す。
 *
 * 設計上の注意:
 * - **サイズ上限を設ける。** 連続取得を回したまま放置するとログだけで数百 MB になる。
 *   上限に達したら追記を止め、その旨を最後に書く（古い行を捨てると、
 *   一番知りたい「最初の接続でのやり取り」が消えるため）。
 * - 追記は同期を取る。通信は IO スレッド、UI 操作は主スレッドから来る。
 * - バイナリ応答の本体は書かない。サイズとハッシュだけ残す。
 */
class SessionLogWriter(
    private val file: File,
    private val maxBytes: Long = DEFAULT_MAX_BYTES,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val lock = Any()
    private var truncated = false

    /** 書き込んだバイト数。 */
    var writtenBytes: Long = 0
        private set

    val target: File get() = file

    /** 上限に達して打ち切られたか。 */
    val isTruncated: Boolean get() = synchronized(lock) { truncated }

    /** ファイルを作り直してヘッダを書く。 */
    fun begin(header: String) {
        synchronized(lock) {
            file.parentFile?.mkdirs()
            file.writeText("")
            writtenBytes = 0
            truncated = false
        }
        appendRaw(header)
    }

    /** 時刻付きで 1 行追記する。 */
    fun append(line: String) {
        appendRaw("${timestamp()} $line\n")
    }

    /** 見出しを追記する。区切りが分かるようにする。 */
    fun appendSection(title: String) {
        appendRaw("\n${timestamp()} ===== $title =====\n")
    }

    /** 複数行をそのまま追記する。 */
    fun appendBlock(text: String) {
        appendRaw(text.trimEnd() + "\n")
    }

    private fun appendRaw(text: String) {
        synchronized(lock) {
            if (truncated) return
            val bytes = text.toByteArray(Charsets.UTF_8)
            if (writtenBytes + bytes.size > maxBytes) {
                // 上限に達した。ここで打ち切り、理由を残す。
                val notice = "\n${timestamp()} !! ログが上限 $maxBytes バイトに達したため記録を停止しました\n"
                runCatching { file.appendBytes(notice.toByteArray(Charsets.UTF_8)) }
                truncated = true
                return
            }
            runCatching {
                file.appendBytes(bytes)
                writtenBytes += bytes.size
            }
        }
    }

    private fun timestamp(): String = TIMESTAMP_FORMAT.get()!!.format(Date(clock()))

    companion object {
        /** 既定の上限。10 MiB あれば初回接続の調査には十分足りる。 */
        const val DEFAULT_MAX_BYTES: Long = 10L * 1024 * 1024

        /** ファイル名に使う時刻書式。 */
        private const val FILE_TIMESTAMP_PATTERN = "yyyyMMdd_HHmmss"

        /** `SimpleDateFormat` はスレッド安全ではないためスレッドごとに持つ。 */
        private val TIMESTAMP_FORMAT = ThreadLocal.withInitial {
            SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)
        }

        /** 記録開始時刻からファイル名を作る。 */
        fun buildFileName(startedAtEpochMillis: Long = System.currentTimeMillis()): String {
            val stamp = SimpleDateFormat(FILE_TIMESTAMP_PATTERN, Locale.US).format(Date(startedAtEpochMillis))
            return "pdtoscillo_$stamp.log"
        }
    }
}
