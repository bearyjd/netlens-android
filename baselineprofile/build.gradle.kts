import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    // Deliberately NOT alias(libs.plugins.android.test): AGP is already on the
    // classpath via build-logic, and a version-carrying alias fails with
    // "plugin is already on the classpath with an unknown version".
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

// Run generation on whatever device adb sees rather than a Gradle Managed Virtual
// Device. In practice that's the API 34 emulator booted by the baseline-profile.yml
// CI workflow — the test phones are Android 17 (ART returns an empty profile) and
// the dev machine can't run the emulator (QEMU segfaults on its kernel).
baselineProfile {
    useConnectedDevices = true
}

dependencies {
    implementation(libs.androidx.test.ext.junit)
    implementation(libs.espresso.core)
    implementation(libs.uiautomator)
    implementation(libs.benchmark.macro.junit4)
}
