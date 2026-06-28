plugins {
    alias(libs.plugins.kotlin.serialization)
    id("netlens.android.feature")
}

android {
    namespace = "com.ventouxlabs.netlens.feature.lanscan"
}

dependencies {
    implementation(project(":core:network"))
    implementation(project(":core:data"))
    implementation(project(":core:oui"))
    implementation(project(":feature:portscan"))
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.core.ktx)
    implementation(libs.compose.material.icons)
}
