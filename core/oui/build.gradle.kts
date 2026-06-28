plugins {
    id("netlens.android.library")
    id("netlens.hilt")
}

android {
    namespace = "com.ventouxlabs.netlens.core.oui"
}

dependencies {
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.core.ktx)
}
