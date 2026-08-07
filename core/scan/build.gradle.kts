plugins {
    alias(libs.plugins.kotlin.serialization)
    id("netlens.android.library")
    id("netlens.hilt")
}

android {
    namespace = "com.ventouxlabs.netlens.core.scan"
}

dependencies {
    implementation(project(":core:data"))
    implementation(project(":core:oui"))
    implementation(project(":core:network"))
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.core.ktx)
    implementation(libs.kotlinx.serialization.json)

    // The engine fakes live in :core:scan-testing so :feature:devices can share them instead of
    // keeping its own drifted copies. This module's own tests are just another consumer.
    testImplementation(project(":core:scan-testing"))
    testImplementation(project(":core:data-testing"))
}
