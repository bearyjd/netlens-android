plugins {
    id("netlens.android.feature")
}

android {
    namespace = "us.beary.netlens.feature.mdns"
}

dependencies {
    implementation(project(":core:network"))
    implementation(libs.core.ktx)
}
