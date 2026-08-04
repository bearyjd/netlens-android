import com.android.build.api.dsl.LibraryExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.testing.Test
import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.gradle.api.tasks.testing.logging.TestLogEvent
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.dsl.KotlinAndroidProjectExtension
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

class AndroidLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("com.android.library")
            pluginManager.apply("org.jetbrains.kotlin.android")

            extensions.configure<LibraryExtension> {
                compileSdk = 35
                defaultConfig {
                    minSdk = 29
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

            val libs = extensions.getByType(
                org.gradle.api.artifacts.VersionCatalogsExtension::class.java
            ).named("libs")

            dependencies {
                add("testImplementation", libs.findLibrary("junit5-api").get())
                add("testRuntimeOnly", libs.findLibrary("junit5-engine").get())
                add("testImplementation", libs.findLibrary("junit5-params").get())
                add("testImplementation", libs.findLibrary("kotlinx-coroutines-test").get())
                add("testImplementation", libs.findLibrary("turbine").get())
            }

            val hasTestSources = !project.fileTree("src/test").isEmpty
            tasks.withType<Test>().configureEach {
                useJUnitPlatform()
                filter {
                    isFailOnNoMatchingTests = false
                }
                enabled = hasTestSources

                // Without this Gradle prints only the failing test's NAME. A rare flake in
                // :feature:devices cost a whole investigation that ended in "cannot reproduce",
                // because neither CI nor a local run ever showed the actual exception — the only
                // way to see it was hand-parsing build/test-results/**/TEST-*.xml after the fact,
                // which is impossible for a failure that does not reproduce on demand.
                // Failures only, so green runs stay quiet.
                testLogging {
                    events(TestLogEvent.FAILED)
                    exceptionFormat = TestExceptionFormat.FULL
                    showExceptions = true
                    showCauses = true
                    showStackTraces = true
                }
            }
        }
    }
}
