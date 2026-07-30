plugins {
    alias(libs.plugins.pdt.android.feature)
}

android {
    namespace = "com.pdtoscillo.feature.measurement"
}

dependencies {
    implementation(project(":core:scpi"))
    implementation(project(":core:network"))
    implementation(project(":core:database"))
}
