plugins {
    id("netlens.android.feature")
}

android {
    namespace = "com.ventouxlabs.netlens.feature.widgetsettings"
}

dependencies {
    implementation(project(":widget"))
    implementation(libs.compose.material.icons)
}
