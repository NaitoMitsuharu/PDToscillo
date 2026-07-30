import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies

/**
 * Android に依存しない Kotlin/JVM モジュール（`core:model`, `core:scpi`, `core:waveform`, `simulator`）。
 * SCPI 解析と波形演算を JVM 側へ寄せることで、実機やエミュレータ無しで高速に単体テストできる。
 */
class PdtJvmLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("org.jetbrains.kotlin.jvm")
            pluginManager.apply("java-library")

            extensions.configure<JavaPluginExtension> {
                sourceCompatibility = JAVA_VERSION
                targetCompatibility = JAVA_VERSION
            }
            configureKotlinJvmCompiler()

            dependencies {
                // StateFlow などが公開 API に現れるため api で公開する。
                add("api", libs.lib("kotlinx-coroutines-core"))
                add("testImplementation", libs.lib("junit4"))
                add("testImplementation", libs.lib("kotlinx-coroutines-test"))
                add("testImplementation", libs.lib("turbine"))
            }
        }
    }
}
