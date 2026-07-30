plugins {
    alias(libs.plugins.pdt.android.library)
}

android {
    namespace = "com.pdtoscillo.core.network"
}

dependencies {
    api(project(":core:model"))
    implementation(project(":core:common"))
    implementation(libs.okio)

    // Raw socket の統合テストは疑似オシロスコープへ実際に TCP 接続する。
    testImplementation(project(":simulator"))
    testImplementation(project(":core:scpi"))
}
