plugins {
    id("netlens.android.feature")
}

android {
    namespace = "com.ventouxlabs.netlens.feature.netlog"
}

dependencies {
    implementation(project(":core:network"))
    implementation(project(":core:data"))
    implementation(libs.core.ktx)
    implementation(libs.compose.material.icons)
    // Shared DAO doubles; this module used to keep its own FakeNetworkEventDao.
    testImplementation(project(":core:data-testing"))
}
