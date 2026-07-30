import org.jlleitschuh.gradle.ktlint.KtlintExtension

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.kotlin.kapt) apply false
    alias(libs.plugins.ktlint) apply false
}

// 静的解析は全モジュールへ一律に適用する。
subprojects {
    apply(plugin = "org.jlleitschuh.gradle.ktlint")
    extensions.configure<KtlintExtension> {
        version.set(rootProject.libs.versions.ktlintTool.get())
        android.set(true)
        ignoreFailures.set(false)
        filter {
            // 生成コード（Room / Compose / R クラス）は対象外。
            exclude { element -> element.file.path.contains("generated") }
            exclude { element -> element.file.path.contains("build${File.separator}") }
        }
    }
}
