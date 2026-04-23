plugins {
    id("netlens.android.feature")
}

android {
    namespace = "us.beary.netlens.feature.lanscan"
}

dependencies {
    implementation(project(":core:network"))
    implementation(project(":core:data"))
    implementation(project(":core:oui"))
    implementation(libs.core.ktx)
    implementation(libs.compose.material.icons)
}
