plugins {
    alias(libs.plugins.kotlin.serialization)
    id("netlens.android.feature")
    id("netlens.android.screenshot")
}

android {
    namespace = "com.ventouxlabs.netlens.feature.lanscan"
}

dependencies {
    implementation(project(":core:scan"))
    implementation(project(":core:network"))
    implementation(project(":core:data"))
    implementation(project(":core:oui"))
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.core.ktx)
    implementation(libs.compose.material.icons)

    testImplementation(project(":core:scan-testing"))
    testImplementation(project(":core:data-testing"))
}
