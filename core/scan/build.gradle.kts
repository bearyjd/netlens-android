plugins {
    id("netlens.android.library")
    id("netlens.hilt")
}

android {
    namespace = "com.ventouxlabs.netlens.core.scan"
}

dependencies {
    implementation(project(":core:data"))
    implementation(project(":core:oui"))
    implementation(project(":core:network"))
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.core.ktx)
}
