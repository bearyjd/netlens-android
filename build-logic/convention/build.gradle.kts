plugins {
    `kotlin-dsl`
}

dependencies {
    compileOnly(libs.android.gradlePlugin)
    compileOnly(libs.kotlin.gradlePlugin)
    compileOnly(libs.compose.gradlePlugin)
    compileOnly(libs.ksp.gradlePlugin)
    compileOnly(libs.room.gradlePlugin)
}

gradlePlugin {
    plugins {
        register("androidApplication") {
            id = "netlens.android.application"
            implementationClass = "AndroidApplicationConventionPlugin"
        }
        register("androidLibrary") {
            id = "netlens.android.library"
            implementationClass = "AndroidLibraryConventionPlugin"
        }
        register("androidCompose") {
            id = "netlens.android.compose"
            implementationClass = "AndroidComposeConventionPlugin"
        }
        register("hilt") {
            id = "netlens.hilt"
            implementationClass = "HiltConventionPlugin"
        }
        register("androidFeature") {
            id = "netlens.android.feature"
            implementationClass = "AndroidFeatureConventionPlugin"
        }
    }
}
