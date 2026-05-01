plugins {
    id("netlens.android.library")
    id("netlens.android.compose")
}

android {
    namespace = "com.ventoux.netlens.core.billing"
}

dependencies {
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.lifecycle.runtime.compose)
}
