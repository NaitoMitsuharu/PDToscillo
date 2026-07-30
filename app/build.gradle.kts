plugins {
    alias(libs.plugins.pdt.android.application)
    alias(libs.plugins.pdt.android.compose)
}

android {
    namespace = "com.pdtoscillo"

    defaultConfig {
        applicationId = "com.pdtoscillo"
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        // 既定は日本語。英語はアプリ内から切り替える。
        resourceConfigurations += setOf("ja", "en")
    }

    buildFeatures {
        buildConfig = true
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
        }
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:common"))
    implementation(project(":core:ui"))
    implementation(project(":core:network"))
    implementation(project(":core:scpi"))
    implementation(project(":core:waveform"))
    implementation(project(":core:database"))

    implementation(project(":feature:connection"))
    implementation(project(":feature:oscilloscope"))
    implementation(project(":feature:waveform"))
    implementation(project(":feature:measurement"))
    implementation(project(":feature:automation"))
    implementation(project(":feature:files"))
    implementation(project(":feature:settings"))
    implementation(project(":feature:console"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.compose.material.icons.extended)

    androidTestImplementation(libs.androidx.espresso.core)
}
