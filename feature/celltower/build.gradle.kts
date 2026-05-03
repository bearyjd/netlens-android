plugins {
    alias(libs.plugins.kotlin.serialization)
    id("netlens.android.feature")
}

android {
    namespace = "com.ventoux.netlens.feature.celltower"
}

dependencies {
    implementation(project(":core:network"))
    implementation(libs.core.ktx)
    implementation(libs.compose.material.icons)
    implementation(libs.kotlinx.serialization.json)
}
