plugins {
    alias(libs.plugins.kotlin.serialization)
    id("netlens.android.feature")
}

android {
    namespace = "com.ventouxlabs.netlens.feature.history"
}

dependencies {
    implementation(project(":core:data"))
    implementation(libs.core.ktx)
    implementation(libs.compose.material.icons)
    implementation(libs.kotlinx.serialization.json)
}
