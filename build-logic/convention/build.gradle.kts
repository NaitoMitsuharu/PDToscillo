plugins {
    `kotlin-dsl`
}

group = "com.pdtoscillo.buildlogic"

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

dependencies {
    compileOnly(libs.android.gradlePlugin)
    compileOnly(libs.kotlin.gradlePlugin)
    compileOnly(libs.compose.gradlePlugin)
}

gradlePlugin {
    plugins {
        register("androidApplication") {
            id = "pdtoscillo.android.application"
            implementationClass = "PdtAndroidApplicationConventionPlugin"
        }
        register("androidLibrary") {
            id = "pdtoscillo.android.library"
            implementationClass = "PdtAndroidLibraryConventionPlugin"
        }
        register("androidCompose") {
            id = "pdtoscillo.android.compose"
            implementationClass = "PdtAndroidComposeConventionPlugin"
        }
        register("androidFeature") {
            id = "pdtoscillo.android.feature"
            implementationClass = "PdtAndroidFeatureConventionPlugin"
        }
        register("jvmLibrary") {
            id = "pdtoscillo.jvm.library"
            implementationClass = "PdtJvmLibraryConventionPlugin"
        }
    }
}
