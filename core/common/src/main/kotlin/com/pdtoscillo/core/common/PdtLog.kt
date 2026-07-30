package com.pdtoscillo.core.common

/**
 * ログ出力の抽象。
 *
 * `core:model` / `core:scpi` / `core:waveform` は Android に依存しないため、`android.util.Log` を
 * 直接呼ばない。Android 側で [PdtLog.install] してシンクを差し替える。
 */
object PdtLog {
    enum class Level { VERBOSE, DEBUG, INFO, WARN, ERROR }

    fun interface Sink {
        fun log(level: Level, tag: String, message: String, throwable: Throwable?)
    }

    /** 何も出力しないシンク。単体テストの既定。 */
    val NoOp: Sink = Sink { _, _, _, _ -> }

    /** 標準出力へ出すシンク。疑似サーバーなど JVM 実行時の既定。 */
    val Stdout: Sink = Sink { level, tag, message, throwable ->
        println("[$level] $tag: $message")
        throwable?.printStackTrace()
    }

    @Volatile
    private var sink: Sink = NoOp

    fun install(newSink: Sink) {
        sink = newSink
    }

    fun v(tag: String, message: String) = sink.log(Level.VERBOSE, tag, message, null)

    fun d(tag: String, message: String) = sink.log(Level.DEBUG, tag, message, null)

    fun i(tag: String, message: String) = sink.log(Level.INFO, tag, message, null)

    fun w(tag: String, message: String, throwable: Throwable? = null) = sink.log(Level.WARN, tag, message, throwable)

    fun e(tag: String, message: String, throwable: Throwable? = null) = sink.log(Level.ERROR, tag, message, throwable)
}
