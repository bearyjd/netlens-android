plugins {
    alias(libs.plugins.kotlin.serialization)
    id("netlens.android.library")
    id("netlens.android.compose")
    id("netlens.hilt")
}

android {
    namespace = "us.beary.netlens.widget"
}

dependencies {
    implementation(project(":core:network"))
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
}
