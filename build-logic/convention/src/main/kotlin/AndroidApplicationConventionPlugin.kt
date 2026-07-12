import com.android.build.api.dsl.ApplicationExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.jetbrains.kotlin.gradle.dsl.KotlinAndroidProjectExtension
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

class AndroidApplicationConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("com.android.application")
            pluginManager.apply("org.jetbrains.kotlin.android")

            extensions.configure<ApplicationExtension> {
                compileSdk = 35
                defaultConfig {
                    minSdk = 29
                    targetSdk = 35
                }
                compileOptions {
                    sourceCompatibility = org.gradle.api.JavaVersion.VERSION_17
                    targetCompatibility = org.gradle.api.JavaVersion.VERSION_17
                }
            }

            // No jvmToolchain() pin here: it would force Gradle to require an
            // exact-match JDK and refuse to build otherwise. F-Droid's
            // buildserver only has JDK 21 installed and disables toolchain
            // auto-provisioning, so pinning 17 fails there even though our own
            // CI/local dev use JDK 17. Compiling under whichever JDK launched
            // Gradle while still targeting Java 17 bytecode (via
            // compileOptions/jvmTarget above) works under both.
            extensions.configure<KotlinAndroidProjectExtension> {
                compilerOptions {
                    jvmTarget.set(JvmTarget.JVM_17)
                }
            }
        }
    }
}
