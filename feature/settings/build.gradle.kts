plugins {
    alias(libs.plugins.pdt.android.feature)
}

android {
    namespace = "com.pdtoscillo.feature.settings"
}

dependencies {
    implementation(project(":core:database"))
    implementation(project(":core:network"))
}
