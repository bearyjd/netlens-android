plugins {
    id("netlens.android.feature")
}

android {
    namespace = "com.ventoux.netlens.feature.wifi"
}

dependencies {
    implementation(project(":core:network"))
    implementation(libs.core.ktx)
    implementation(libs.compose.material.icons)
}
