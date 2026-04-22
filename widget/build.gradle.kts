plugins {
    id("netlens.android.library")
    id("netlens.android.compose")
    id("netlens.hilt")
}

android {
    namespace = "us.beary.netlens.widget"
}

dependencies {
    implementation(project(":feature:ipinfo"))
    implementation(project(":core:network"))
    implementation(libs.glance.appwidget)
    implementation(libs.glance.material3)
    implementation(libs.work.runtime)
    implementation(libs.datastore.preferences)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.core.ktx)
}
