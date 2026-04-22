plugins {
    id("netlens.android.feature")
}

android {
    namespace = "us.beary.netlens.feature.netlog"
}

dependencies {
    implementation(project(":core:network"))
    implementation(project(":core:data"))
    implementation(libs.core.ktx)
}
