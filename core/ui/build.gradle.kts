plugins {
    id("netlens.android.library")
    id("netlens.android.compose")
    id("netlens.android.screenshot")
}

android {
    namespace = "com.ventouxlabs.netlens.core.ui"
}

dependencies {
    implementation(libs.compose.material.icons)
}
