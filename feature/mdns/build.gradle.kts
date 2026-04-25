plugins {
    id("netlens.android.feature")
}

android {
    namespace = "com.ventoux.netlens.feature.mdns"
}

dependencies {
    implementation(project(":core:network"))
    implementation(libs.core.ktx)
}
