plugins {
    id("netlens.android.feature")
}

android {
    namespace = "com.ventouxlabs.netlens.feature.ipcalc"
}

dependencies {
    implementation(project(":core:network"))
    implementation(libs.compose.material.icons)
}
