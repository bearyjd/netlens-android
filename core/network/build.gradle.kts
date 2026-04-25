plugins {
    id("netlens.android.library")
    id("netlens.hilt")
}

android {
    namespace = "com.ventoux.netlens.core.network"
}

dependencies {
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.core.ktx)
}
