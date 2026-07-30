import com.android.build.api.dsl.ApplicationExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies

/** `:app` 用の共通設定。 */
class PdtAndroidApplicationConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("com.android.application")
            pluginManager.apply("org.jetbrains.kotlin.android")

            extensions.configure<ApplicationExtension> {
                compileSdk = libs.intVersion("compileSdk")
                defaultConfig {
                    minSdk = libs.intVersion("minSdk")
                    targetSdk = libs.intVersion("targetSdk")
                }
                compileOptions {
                    sourceCompatibility = JAVA_VERSION
                    targetCompatibility = JAVA_VERSION
                }
                testOptions {
                    unitTests {
                        isReturnDefaultValues = true
                        isIncludeAndroidResources = true
                    }
                }
            }
            configureKotlinAndroidCompiler()

            dependencies {
                add("implementation", libs.lib("kotlinx-coroutines-android"))
                add("testImplementation", libs.lib("junit4"))
                add("testImplementation", libs.lib("kotlinx-coroutines-test"))
                add("testImplementation", libs.lib("mockk"))
                add("testImplementation", libs.lib("turbine"))
                add("androidTestImplementation", libs.lib("androidx-test-junit"))
                add("androidTestImplementation", libs.lib("androidx-test-runner"))
            }
        }
    }
}
