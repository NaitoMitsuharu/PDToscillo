package com.pdtoscillo

import android.app.Application

/**
 * アプリ全体の依存を組み立てる。
 * DI ライブラリを使わずコンストラクタ注入で完結させ、コード生成に伴うビルド不安定を避ける。
 */
class PDToscilloApplication : Application() {
    override fun onCreate() {
        super.onCreate()
    }
}
