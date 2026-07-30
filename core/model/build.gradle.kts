plugins {
    alias(libs.plugins.pdt.jvm.library)
}

// ドメインモデルのみ。Android にも計測器通信の実装にも依存しない最下層。
