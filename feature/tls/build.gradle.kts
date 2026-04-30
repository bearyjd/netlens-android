plugins {
    id("netlens.android.feature")
}

android {
    namespace = "com.ventoux.netlens.feature.tls"
}

dependencies {
    implementation(project(":core:network"))
    implementation(project(":core:data"))
    implementation(libs.compose.material.icons)
}
