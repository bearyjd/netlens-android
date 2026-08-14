import com.android.build.api.dsl.LibraryExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies

/**
 * Robolectric-backed Room testing. **Deliberately opt-in, not part of `netlens.android.library`.**
 *
 * There is zero Robolectric elsewhere in this repo, and a 2026-08-08 audit pass
 * (`.agent_native/agent_roadmap.md`, item 3) found that every prior motivating case
 * (celltower, wifiaudit, history, widgetsettings) was solved with an interface seam + hand-written
 * fake instead — that's the repo's default and should stay the default.
 *
 * The one case that isn't solvable that way: real Room `@Query` execution and `Migration`
 * validation need a real `Context` and real SQLite. Room's Kotlin-Multiplatform JVM testing API
 * (`Room.inMemoryDatabaseBuilder<T>()`, no Context) was tried first and rejected — it only ships in
 * Room's `-jvm`-target artifact, which a plain (non-KMP) `com.android.library` module can't safely
 * consume alongside its production `-android` artifact (confirmed by decompiling
 * `room-runtime-android-2.7.2.jar`: the reified no-Context builder isn't there). Do not re-attempt
 * a "just add the jvm artifact" fix without re-verifying that — see `.agent_native/agent_roadmap.md`
 * for the recorded dead end.
 *
 * So this module uses the classic, pre-KMP `MigrationTestHelper` constructor
 * (`Instrumentation, Class<out RoomDatabase>, ...`), which Robolectric has supported for years via
 * `InstrumentationRegistry` — long-standing, not experimental. Note this constructor is exactly
 * what Room 2.7's KMP release removed, so any module applying this plugin must stay on the `room`
 * version this repo pins today; do not bump Room alongside adopting this.
 *
 * `isIncludeAndroidResources = true` is set for Robolectric's asset/resource access. Confirmed
 * (2026-08-14) that `sourceSets.test.assets.srcDir(...)` on a module applying this plugin does get
 * merged into the unit-test classpath — `mergeDebugUnitTestAssets` runs, not skipped — so
 * `MigrationTestHelper` can read exported schema JSONs from `schemas/` without a `main.assets`
 * fallback.
 *
 * Like [AndroidScreenshotConventionPlugin], Robolectric's `RobolectricTestRunner` is JUnit4, so the
 * vintage engine runs it on this repo's JUnit5 platform. A module can hold both styles.
 */
class AndroidRobolectricConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            extensions.configure<LibraryExtension> {
                testOptions {
                    unitTests.isIncludeAndroidResources = true
                }
            }

            val libs = extensions.getByType(
                org.gradle.api.artifacts.VersionCatalogsExtension::class.java,
            ).named("libs")

            dependencies {
                add("testImplementation", libs.findLibrary("junit4").get())
                add("testRuntimeOnly", libs.findLibrary("junit-vintage-engine").get())
                add("testImplementation", libs.findLibrary("robolectric").get())
                add("testImplementation", libs.findLibrary("androidx-test-core-ktx").get())
            }
        }
    }
}
