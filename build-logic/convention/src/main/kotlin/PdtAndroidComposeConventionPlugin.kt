import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.dsl.LibraryExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies

/**
 * Jetpack Compose を有効化する。
 * `:app` と Android ライブラリの両方に適用できるよう、実際に存在する拡張だけを触る。
 */
class PdtAndroidComposeConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("org.jetbrains.kotlin.plugin.compose")

            extensions.findByType(LibraryExtension::class.java)?.buildFeatures?.compose = true
            extensions.findByType(ApplicationExtension::class.java)?.buildFeatures?.compose = true

            dependencies {
                val bom = libs.lib("androidx-compose-bom")
                add("implementation", platform(bom))
                add("androidTestImplementation", platform(bom))

                add("implementation", libs.lib("androidx-compose-ui"))
                add("implementation", libs.lib("androidx-compose-ui-graphics"))
                add("implementation", libs.lib("androidx-compose-ui-tooling-preview"))
                add("implementation", libs.lib("androidx-compose-material3"))
                add("implementation", libs.lib("androidx-lifecycle-runtime-compose"))
                add("debugImplementation", libs.lib("androidx-compose-ui-tooling"))
                add("debugImplementation", libs.lib("androidx-compose-ui-test-manifest"))
                add("androidTestImplementation", libs.lib("androidx-compose-ui-test-junit4"))
            }
        }
    }
}
