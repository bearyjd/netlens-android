plugins {
    id("netlens.android.feature")
}

android {
    namespace = "us.beary.netlens.feature.portscan"
}

dependencies {
    implementation(project(":core:network"))
    implementation(libs.core.ktx)
    implementation(libs.compose.material.icons)
}
