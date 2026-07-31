import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies

/**
 * Composition smoke tests for Compose screens, via Paparazzi.
 *
 * **This is deliberately not screenshot testing.** No golden images are recorded, nothing is
 * committed as a PNG, and `verifyPaparazzi` is never run in CI. Pixel diffing is the expensive,
 * flaky, storage-hungry part; the bug class that has actually escaped this repo does not need it.
 *
 * A duplicate `LazyColumn` key, a composition error, a measure/layout failure — all of them throw
 * at *render* time. Calling `paparazzi.snapshot { … }` inside a plain unit test renders the
 * composable on the JVM and fails if it cannot compose, which is the whole check. The #116 crash
 * (`IllegalArgumentException: Key "1" was already used`) reproduces this way in seconds; it
 * previously survived three review passes, an adversarial round and 750 green unit tests, and was
 * found by a two-minute walk on a real phone.
 *
 * Two things worth knowing before writing one:
 * - Paparazzi's rule is JUnit4, so the vintage engine is added to run it on the JUnit 5 platform
 *   used everywhere else. A module can hold both styles.
 * - The render exception does **not** surface inside the `snapshot { }` lambda — it escapes via
 *   the rule during teardown. You cannot `try/catch` it at the call site and assert on it; let the
 *   test fail instead.
 *
 * Only useful for **state-driven** composables. A screen whose parameters default to
 * `hiltViewModel()` cannot be rendered here; test the stateless composable it delegates to.
 */
class AndroidScreenshotConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("app.cash.paparazzi")

            val libs = extensions.getByType(
                org.gradle.api.artifacts.VersionCatalogsExtension::class.java
            ).named("libs")

            dependencies {
                add("testImplementation", libs.findLibrary("junit4").get())
                add("testRuntimeOnly", libs.findLibrary("junit-vintage-engine").get())
            }
        }
    }
}
