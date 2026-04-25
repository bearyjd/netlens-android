plugins {
    id("netlens.android.feature")
}

android {
    namespace = "com.ventoux.netlens.feature.widgetsettings"
}

dependencies {
    implementation(project(":widget"))
    implementation(libs.compose.material.icons)
}
