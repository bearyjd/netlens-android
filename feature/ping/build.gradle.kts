plugins {
    id("netlens.android.feature")
}

android {
    namespace = "us.beary.netlens.feature.ping"
}

dependencies {
    implementation(project(":core:network"))
    implementation(libs.core.ktx)
}
