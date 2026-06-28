plugins {
    id("netlens.android.feature")
}

android {
    namespace = "com.ventouxlabs.netlens.feature.tls"
}

dependencies {
    implementation(project(":core:network"))
    implementation(project(":core:data"))
    implementation(libs.compose.material.icons)
}
