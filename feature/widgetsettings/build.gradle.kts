plugins {
    id("netlens.android.feature")
    id("netlens.android.robolectric")
}

android {
    namespace = "com.ventouxlabs.netlens.feature.widgetsettings"
}

dependencies {
    implementation(project(":widget"))
    implementation(project(":core:data"))
    implementation(libs.compose.material.icons)

    testImplementation(libs.datastore.preferences)

    testImplementation(project(":core:data-testing"))

    // Robolectric-backed WorkManager enqueue test for refreshWidgets() — see
    // netlens.android.robolectric.
    testImplementation(libs.work.testing)
}
