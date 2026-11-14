plugins {
    id("netlens.android.library")
    id("netlens.android.compose")
    id("netlens.android.screenshot")
    id("netlens.android.robolectric")
}

android {
    namespace = "com.ventouxlabs.netlens.core.ui"
}

dependencies {
    implementation(libs.compose.material.icons)

    testImplementation(libs.compose.ui.test.junit4)
    debugImplementation(libs.compose.ui.test.manifest)
}
