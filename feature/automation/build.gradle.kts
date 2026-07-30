plugins {
    alias(libs.plugins.pdt.android.feature)
}

android {
    namespace = "com.pdtoscillo.feature.automation"
}

dependencies {
    implementation(project(":core:scpi"))
    implementation(project(":core:waveform"))
    implementation(project(":core:network"))
    implementation(project(":core:database"))
}
