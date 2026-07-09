plugins {
    id("netlens.android.feature")
}

android {
    namespace = "com.ventouxlabs.netlens.feature.widgetsettings"
}

dependencies {
    implementation(project(":widget"))
    implementation(project(":core:data"))
    implementation(libs.compose.material.icons)

    testImplementation(libs.datastore.preferences)
}
