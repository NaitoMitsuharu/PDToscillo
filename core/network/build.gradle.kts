plugins {
    alias(libs.plugins.pdt.android.library)
}

android {
    namespace = "com.pdtoscillo.core.network"
}

dependencies {
    api(project(":core:model"))
    implementation(project(":core:common"))

    // SCPI のワイヤ形式（テキスト行と IEEE 488.2 ブロックの切り出し）は core:scpi が持つ。
    // Transport はソケットと Android のネットワーク選択に責務を絞り、フレーミングを再実装しない。
    api(project(":core:scpi"))
    implementation(libs.okio)

    // Raw socket の統合テストは疑似オシロスコープへ実際に TCP 接続する。
    testImplementation(project(":simulator"))
}
