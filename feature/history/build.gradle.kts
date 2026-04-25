plugins {
    id("netlens.android.feature")
}

android {
    namespace = "com.ventoux.netlens.feature.history"
}

dependencies {
    implementation(project(":core:data"))
    implementation(libs.core.ktx)
    implementation(libs.compose.material.icons)
}
