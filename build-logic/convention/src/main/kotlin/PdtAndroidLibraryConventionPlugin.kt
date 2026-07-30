import com.android.build.api.dsl.LibraryExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies

/**
 * Android ライブラリモジュール共通設定。
 * core 配下と feature 配下の Android モジュール共通の土台。
 */
class PdtAndroidLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("com.android.library")
            pluginManager.apply("org.jetbrains.kotlin.android")

            extensions.configure<LibraryExtension> {
                compileSdk = libs.intVersion("compileSdk")
                defaultConfig {
                    minSdk = libs.intVersion("minSdk")
                }
                compileOptions {
                    sourceCompatibility = JAVA_VERSION
                    targetCompatibility = JAVA_VERSION
                }
                buildFeatures {
                    buildConfig = false
                }
                testOptions {
                    unitTests {
                        // android.util.Log などを単体テストで呼んでも例外にしない。
                        isReturnDefaultValues = true
                        isIncludeAndroidResources = true
                    }
                }
            }
            configureKotlinAndroidCompiler()

            dependencies {
                add("implementation", libs.lib("kotlinx-coroutines-core"))
                add("testImplementation", libs.lib("junit4"))
                add("testImplementation", libs.lib("kotlinx-coroutines-test"))
                add("testImplementation", libs.lib("mockk"))
                add("testImplementation", libs.lib("turbine"))
            }
        }
    }
}
