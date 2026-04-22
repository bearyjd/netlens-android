plugins {
    id("netlens.android.feature")
}

android {
    namespace = "us.beary.netlens.feature.wol"
}

dependencies {
    implementation(project(":core:network"))
    implementation(project(":core:data"))
    implementation(libs.core.ktx)
}
