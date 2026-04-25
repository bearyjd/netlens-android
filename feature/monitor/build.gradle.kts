plugins {
    id("netlens.android.feature")
}

android {
    namespace = "com.ventoux.netlens.feature.monitor"
}

dependencies {
    implementation(project(":core:network"))
    implementation(project(":core:data"))
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.cio)
    implementation(libs.core.ktx)
}
