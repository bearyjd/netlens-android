plugins {
    id("netlens.android.feature")
}

android {
    namespace = "us.beary.netlens.feature.whois"
}

dependencies {
    implementation(project(":core:network"))
}
