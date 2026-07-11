import java.util.Properties

plugins {
    alias(libs.plugins.kotlin.serialization)
    id("netlens.android.application")
    id("netlens.android.compose")
    id("netlens.hilt")
}

android {
    namespace = "com.ventouxlabs.netlens"

    defaultConfig {
        applicationId = "com.ventouxlabs.netlens"
        versionCode = property("netlens.versionCode").toString().toInt()
        versionName = property("netlens.versionName").toString()
    }

    flavorDimensions += "distribution"
    productFlavors {
        create("foss") { dimension = "distribution" }
        create("gplay") { dimension = "distribution" }
    }

    signingConfigs {
        create("release") {
            // Per-field fallback: prefer local.properties when a key is present and
            // non-blank, otherwise fall through to the corresponding RELEASE_* env
            // var. Avoids the trap where local.properties exists with only sdk.dir
            // and silently shadows valid env vars, producing an unsigned APK.
            val localProps = rootProject.file("local.properties")
                .takeIf { it.exists() }
                ?.let { f -> Properties().apply { f.inputStream().use { load(it) } } }

            fun pick(propKey: String, envKey: String): String? =
                localProps?.getProperty(propKey)?.takeIf { it.isNotBlank() }
                    ?: System.getenv(envKey)?.takeIf { it.isNotBlank() }

            pick("release.storeFile", "RELEASE_STORE_FILE")?.let { storeFile = file(it) }
            storePassword = pick("release.storePassword", "RELEASE_STORE_PASSWORD")
            keyAlias = pick("release.keyAlias", "RELEASE_KEY_ALIAS")
            keyPassword = pick("release.keyPassword", "RELEASE_KEY_PASSWORD")
        }
    }

    packaging {
        resources {
            excludes += setOf(
                "META-INF/*.kotlin_module",
                "META-INF/LICENSE.md",
                "META-INF/LICENSE-notice.md",
                // dnsjava ships JDK name-service SPI descriptors that have no effect
                // on Android (InetAddress uses Bionic, not java.net.spi). Stripping
                // them removes dead bytes and silences R8 missing-service warnings.
                "META-INF/services/java.net.spi.InetAddressResolverProvider",
                "META-INF/services/sun.net.spi.nameservice.NameServiceDescriptor",
            )
        }
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }

    buildTypes {
        release {
            isDebuggable = false
            isJniDebuggable = false
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            // findByName, not getByName: F-Droid's reproducible-build tooling strips the
            // entire signingConfigs block before building from source, so "release" may
            // not exist at all (not just be unconfigured) on that build path.
            val releaseSigning = signingConfigs.findByName("release")
            if (releaseSigning?.storeFile != null) {
                signingConfig = releaseSigning
            }
        }
    }
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}

dependencies {
    // Feature modules
    implementation(project(":feature:ipinfo"))
    implementation(project(":feature:lanscan"))
    implementation(project(":feature:portscan"))
    implementation(project(":feature:dns"))
    implementation(project(":feature:ping"))
    implementation(project(":feature:traceroute"))
    implementation(project(":feature:wol"))
    implementation(project(":feature:tls"))
    implementation(project(":feature:whois"))
    implementation(project(":feature:httptester"))
    implementation(project(":feature:mdns"))
    implementation(project(":feature:netlog"))
    implementation(project(":feature:monitor"))
    implementation(project(":feature:history"))
    implementation(project(":feature:widgetsettings"))
    implementation(project(":feature:posture"))
    implementation(project(":feature:ipcalc"))
    implementation(project(":feature:speedtest"))
    implementation(project(":feature:wifi"))
    implementation(project(":feature:dnsleak"))
    implementation(project(":feature:wifiaudit"))
    implementation(project(":feature:celltower"))
    implementation(project(":feature:vpnstatus"))

    // Widget
    implementation(project(":widget"))

    // Core modules
    implementation(project(":core:network"))
    implementation(project(":core:data"))
    implementation(project(":core:oui"))
    implementation(project(":core:billing"))
    implementation(project(":core:ui"))

    // Billing (gplay only)
    "gplayImplementation"(libs.billing)
    "gplayImplementation"(libs.security.crypto)

    // Test
    testImplementation(libs.junit5.api)
    testRuntimeOnly(libs.junit5.engine)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
    testImplementation("org.json:json:20231013")

    // AndroidX / Compose
    implementation(libs.activity.compose)
    implementation(libs.navigation.compose)
    implementation(libs.compose.material.icons)
    implementation(libs.core.ktx)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.hilt.navigation.compose)
}
