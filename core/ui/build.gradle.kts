plugins {
    alias(libs.plugins.pdt.android.library)
    alias(libs.plugins.pdt.android.compose)
}

android {
    namespace = "com.pdtoscillo.core.ui"
}

dependencies {
    api(project(":core:model"))
    api(project(":core:common"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.compose.material.icons.extended)
    api(libs.androidx.lifecycle.runtime.compose)
}
