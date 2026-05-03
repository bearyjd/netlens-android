plugins {
    id("netlens.android.feature")
}

android {
    namespace = "com.ventoux.netlens.feature.wifiaudit"
}

dependencies {
    implementation(project(":core:network"))
    implementation(project(":core:data"))
    implementation(libs.core.ktx)
    implementation(libs.compose.material.icons)
}
