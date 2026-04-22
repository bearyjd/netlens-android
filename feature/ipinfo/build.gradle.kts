plugins {
    alias(libs.plugins.kotlin.serialization)
    id("netlens.android.feature")
}

android {
    namespace = "us.beary.netlens.feature.ipinfo"
}

dependencies {
    implementation(project(":core:network"))
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.cio)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.serialization.json)
    implementation(libs.kotlinx.serialization.json)
}
