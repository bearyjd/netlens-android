plugins {
    id("netlens.android.feature")
}

android {
    namespace = "com.ventoux.netlens.feature.speedtest"
}

dependencies {
    implementation(project(":core:network"))
    implementation(project(":core:data"))
    implementation(libs.core.ktx)
    implementation(libs.compose.material.icons)
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.cio)
}
