plugins {
    alias(libs.plugins.kotlin.serialization)
    id("netlens.android.library")
    id("netlens.android.compose")
    id("netlens.hilt")
    id("netlens.android.robolectric")
}

android {
    namespace = "com.ventouxlabs.netlens.widget"
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}

dependencies {
    implementation(project(":core:network"))
    implementation(project(":core:data"))
    implementation(project(":core:ui"))
    implementation(libs.glance.appwidget)
    implementation(libs.glance.material3)
    implementation(libs.work.runtime)
    implementation(libs.datastore.preferences)
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.cio)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.serialization.json)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.core.ktx)

    testImplementation(libs.junit5.api)
    testRuntimeOnly(libs.junit5.engine)

    // Robolectric-backed receiver lifecycle / WorkManager enqueue tests — see
    // netlens.android.robolectric.
    testImplementation(libs.work.testing)
}
