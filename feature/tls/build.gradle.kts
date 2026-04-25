plugins {
    id("netlens.android.feature")
}

android {
    namespace = "com.ventoux.netlens.feature.tls"
}

dependencies {
    implementation(project(":core:network"))
}
