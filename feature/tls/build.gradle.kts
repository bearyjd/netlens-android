plugins {
    id("netlens.android.feature")
}

android {
    namespace = "us.beary.netlens.feature.tls"
}

dependencies {
    implementation(project(":core:network"))
}
