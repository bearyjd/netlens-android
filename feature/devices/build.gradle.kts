plugins {
    id("netlens.android.feature")
    id("netlens.android.screenshot")
}

android {
    namespace = "com.ventouxlabs.netlens.feature.devices"
}

dependencies {
    implementation(project(":core:scan"))
    implementation(project(":core:data"))
    implementation(project(":core:network"))
    implementation(project(":core:oui"))
    implementation(libs.work.runtime)
    implementation(libs.core.ktx)
    implementation(libs.compose.material.icons)
    testImplementation(libs.datastore.preferences)
    // Shared engine doubles. This module used to carry its own copies of FakeArpTableReader,
    // FakeOuiLookup and FakeSubnetScanner; they drifted weaker than :core:scan's originals.
    testImplementation(project(":core:scan-testing"))
}
