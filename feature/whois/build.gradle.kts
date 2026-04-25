plugins {
    id("netlens.android.feature")
}

android {
    namespace = "com.ventoux.netlens.feature.whois"
}

dependencies {
    implementation(project(":core:network"))
    implementation(project(":core:data"))
}
