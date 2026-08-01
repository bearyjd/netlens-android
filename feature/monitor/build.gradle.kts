plugins {
    id("netlens.android.feature")
    id("netlens.android.screenshot")
}

android {
    namespace = "com.ventouxlabs.netlens.feature.monitor"
}

dependencies {
    implementation(project(":core:network"))
    implementation(project(":core:data"))
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.cio)
    implementation(libs.core.ktx)

    testImplementation(libs.ktor.client.mock)
    // Shared SSRF-redirect probe; both modules used to hand-roll the same engine.
    testImplementation(project(":core:network-testing"))
}
