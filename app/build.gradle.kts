import java.util.Properties

plugins {
    alias(libs.plugins.kotlin.serialization)
    id("netlens.android.application")
    id("netlens.android.compose")
    id("netlens.hilt")
}

android {
    namespace = "com.ventoux.netlens"

    defaultConfig {
        applicationId = "com.ventoux.netlens"
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
            val props = rootProject.file("local.properties")
            if (props.exists()) {
                val localProps = Properties().apply {
                    props.inputStream().use { load(it) }
                }
                storeFile = localProps.getProperty("release.storeFile")?.let(::file)
                storePassword = localProps.getProperty("release.storePassword")
                keyAlias = localProps.getProperty("release.keyAlias")
                keyPassword = localProps.getProperty("release.keyPassword")
            } else {
                storeFile = System.getenv("RELEASE_STORE_FILE")?.let(::file)
                storePassword = System.getenv("RELEASE_STORE_PASSWORD")
                keyAlias = System.getenv("RELEASE_KEY_ALIAS")
                keyPassword = System.getenv("RELEASE_KEY_PASSWORD")
            }
        }
    }

    packaging {
        resources {
            excludes += setOf(
                "META-INF/*.kotlin_module",
                "META-INF/LICENSE.md",
                "META-INF/LICENSE-notice.md",
            )
        }
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            val releaseSigning = signingConfigs.getByName("release")
            if (releaseSigning.storeFile != null) {
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

    // Widget
    implementation(project(":widget"))

    // Core modules
    implementation(project(":core:network"))
    implementation(project(":core:data"))
    implementation(project(":core:oui"))
    implementation(project(":core:billing"))

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
