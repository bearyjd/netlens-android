plugins {
    id("netlens.android.feature")
}

android {
    namespace = "com.ventoux.netlens.feature.ipcalc"
}

dependencies {
    implementation(project(":core:network"))
    implementation(libs.compose.material.icons)
}
