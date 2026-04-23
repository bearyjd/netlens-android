plugins {
    id("netlens.android.feature")
}

android {
    namespace = "us.beary.netlens.feature.widgetsettings"
}

dependencies {
    implementation(project(":widget"))
    implementation(libs.compose.material.icons)
}
