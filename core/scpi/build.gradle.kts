plugins {
    alias(libs.plugins.pdt.jvm.library)
}

dependencies {
    api(project(":core:model"))
    implementation(project(":core:common"))
    implementation(project(":core:waveform"))

    // 疑似オシロスコープを相手にした統合テスト（実機不要）
    testImplementation(project(":simulator"))
}
