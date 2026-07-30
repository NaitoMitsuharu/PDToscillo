plugins {
    alias(libs.plugins.pdt.android.feature)
}

android {
    namespace = "com.pdtoscillo.feature.connection"
}

dependencies {
    implementation(project(":core:network"))
    implementation(project(":core:scpi"))
    implementation(project(":core:database"))
}
