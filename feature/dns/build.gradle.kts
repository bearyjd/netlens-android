plugins {
    id("netlens.android.feature")
}

android {
    namespace = "us.beary.netlens.feature.dns"
}

dependencies {
    implementation(project(":core:network"))
    implementation(libs.dnsjava)
    implementation(libs.core.ktx)
    implementation(libs.compose.material.icons)
}
