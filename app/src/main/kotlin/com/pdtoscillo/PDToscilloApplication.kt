package com.pdtoscillo

import android.app.Application
import android.util.Log
import com.pdtoscillo.core.common.PdtLog
import com.pdtoscillo.core.network.InstrumentSession

/**
 * アプリ全体の依存を組み立てる。
 *
 * DI ライブラリを使わずコンストラクタ注入で完結させ、コード生成に伴うビルド不安定を避ける。
 *
 * [InstrumentSession] は 1 台のオシロスコープに対する 1 本の接続と 1 本のコマンドキューを持つ。
 * Activity ではなく Application が保持するため、画面回転で通信セッションを作り直さない。
 */
class PDToscilloApplication : Application() {

    lateinit var session: InstrumentSession
        private set

    override fun onCreate() {
        super.onCreate()

        PdtLog.install { level, tag, message, throwable ->
            when (level) {
                PdtLog.Level.VERBOSE -> Log.v(tag, message, throwable)
                PdtLog.Level.DEBUG -> Log.d(tag, message, throwable)
                PdtLog.Level.INFO -> Log.i(tag, message, throwable)
                PdtLog.Level.WARN -> Log.w(tag, message, throwable)
                PdtLog.Level.ERROR -> Log.e(tag, message, throwable)
            }
        }

        session = InstrumentSession(this)
        session.start()
    }

    override fun onTerminate() {
        session.stop()
        super.onTerminate()
    }
}
