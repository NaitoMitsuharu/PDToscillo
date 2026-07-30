import org.gradle.api.JavaVersion
import org.gradle.api.Project
import org.gradle.api.artifacts.MinimalExternalModuleDependency
import org.gradle.api.artifacts.VersionCatalog
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.api.provider.Provider
import org.gradle.kotlin.dsl.getByType
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinAndroidProjectExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension

/** バージョンカタログ。Convention Plugin から利用元プロジェクトの `libs` を引く。 */
internal val Project.libs: VersionCatalog
    get() = extensions.getByType<VersionCatalogsExtension>().named("libs")

internal fun VersionCatalog.lib(alias: String): Provider<MinimalExternalModuleDependency> =
    findLibrary(alias).orElseThrow { IllegalStateException("libs.versions.toml に $alias がありません") }

internal fun VersionCatalog.intVersion(alias: String): Int =
    findVersion(alias)
        .orElseThrow { IllegalStateException("libs.versions.toml に version $alias がありません") }
        .requiredVersion
        .toInt()

internal val JAVA_VERSION: JavaVersion = JavaVersion.VERSION_17

/**
 * Kotlin / Android モジュール共通のコンパイラ設定。
 * ここに集約することで 15 を超える Android モジュールで設定が重複しない。
 */
internal fun Project.configureKotlinAndroidCompiler() {
    extensions.getByType<KotlinAndroidProjectExtension>().compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
        // Compose や coroutines を跨いだ warning をビルド失敗にはしないが、警告は残す。
        allWarningsAsErrors.set(false)
    }
}

internal fun Project.configureKotlinJvmCompiler() {
    extensions.getByType<KotlinJvmProjectExtension>().compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
        allWarningsAsErrors.set(false)
    }
}
