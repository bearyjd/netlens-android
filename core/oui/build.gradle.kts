plugins {
    id("netlens.android.library")
    id("netlens.hilt")
}

android {
    namespace = "us.beary.netlens.core.oui"
}

dependencies {
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.core.ktx)
}
