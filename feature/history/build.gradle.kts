plugins {
    id("netlens.android.feature")
}

android {
    namespace = "us.beary.netlens.feature.history"
}

dependencies {
    implementation(project(":core:data"))
    implementation(libs.core.ktx)
    implementation(libs.compose.material.icons)
}
