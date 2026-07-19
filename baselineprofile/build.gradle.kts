import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.test")
    id("org.jetbrains.kotlin.android")
    alias(libs.plugins.baselineprofile)
}

android {
    namespace = "com.ventouxlabs.netlens.baselineprofile"
    compileSdk = 35

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }

    defaultConfig {
        // Macrobenchmark requires API 23+; the app's minSdk is 29.
        minSdk = 29
        targetSdk = 35
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // :app is flavored (foss/gplay on the "distribution" dimension). We only
        // generate a profile for the FOSS flavor — that's what ships on F-Droid and
        // is what we build locally — so resolve every dependency on :app against foss.
        missingDimensionStrategy("distribution", "foss")
    }

    targetProjectPath = ":app"
}

// Run generation on the connected physical device (Pixel 10 Pro Fold, API 37) rather
// than a Gradle Managed Virtual Device.
baselineProfile {
    useConnectedDevices = true
}

dependencies {
    implementation(libs.androidx.test.ext.junit)
    implementation(libs.espresso.core)
    implementation(libs.uiautomator)
    implementation(libs.benchmark.macro.junit4)
}
