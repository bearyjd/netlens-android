plugins {
    id("netlens.android.feature")
}

android {
    namespace = "com.ventouxlabs.netlens.feature.devices"
}

dependencies {
    implementation(project(":core:scan"))
    implementation(project(":core:data"))
    implementation(project(":core:network"))
    implementation(project(":core:oui"))
    implementation(libs.work.runtime)
    implementation(libs.core.ktx)
    implementation(libs.compose.material.icons)
}
