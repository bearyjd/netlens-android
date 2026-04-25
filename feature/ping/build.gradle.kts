plugins {
    id("netlens.android.feature")
}

android {
    namespace = "us.beary.netlens.feature.ping"
}

dependencies {
    implementation(project(":core:network"))
    implementation(project(":core:data"))
    implementation(libs.core.ktx)
    implementation(libs.compose.material.icons)
}
