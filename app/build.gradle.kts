plugins {
    alias(libs.plugins.kotlin.serialization)
    id("netlens.android.application")
    id("netlens.android.compose")
    id("netlens.hilt")
}

android {
    namespace = "us.beary.netlens"

    defaultConfig {
        applicationId = "us.beary.netlens"
        versionCode = 1
        versionName = "0.1.0"
    }
}

dependencies {
    // Feature modules
    implementation(project(":feature:ipinfo"))
    implementation(project(":feature:lanscan"))
    implementation(project(":feature:portscan"))
    implementation(project(":feature:dns"))
    implementation(project(":feature:ping"))
    implementation(project(":feature:wol"))

    // Widget
    implementation(project(":widget"))

    // Core modules
    implementation(project(":core:network"))
    implementation(project(":core:data"))
    implementation(project(":core:oui"))

    // AndroidX / Compose
    implementation(libs.activity.compose)
    implementation(libs.navigation.compose)
    implementation(libs.compose.material.icons)
    implementation(libs.core.ktx)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.hilt.navigation.compose)
}
