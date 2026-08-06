package com.ventouxlabs.netlens.baselineprofile

import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Generates a Baseline Profile for NetLens.
 *
 * The journey exercises the hot paths that matter for perceived performance:
 *  1. Cold start into HomeScreen via [MacrobenchmarkScope.startActivityAndWait].
 *  2. Scroll the tool grid (LazyVerticalGrid) down and back up so grid composition,
 *     DI, and the per-tile classes are captured.
 *  3. Navigate into LAN Scan and Devices so their Compose trees, ViewModels and Hilt
 *     graphs are captured too.
 *
 * **Why step 3 exists now, having been dropped before.** The earlier version said deeper
 * navigation was dropped because "UiAutomator text lookups for the tool labels were
 * unreliable". Two things changed:
 *
 *  - Generation moved off the Android 17 phone onto an **API 34 emulator in CI**
 *    (see `.github/workflows/baseline-profile.yml`); the flakiness was device-specific.
 *  - Navigation no longer *finds* anything by text. It fires a **deep link**
 *    (`netlens://feature/<path>`), so the tool label never has to be located — text is
 *    used only to *confirm arrival*, which is a far weaker requirement than search.
 *
 * The original concern was right and still is: **a journey step that silently no-ops is
 * worse than no step**, because the workflow's non-empty guard cannot tell a thin profile
 * from a full one. Every navigation therefore fails loudly rather than degrading.
 *
 * Only paths present in `DeepLinkRouter.PATH_TO_ROUTE` can be used. An unlisted path
 * resolves to `null` and the app silently stays on Home — `wifi` is NOT in that map
 * (`wifiaudit` is a different screen), so the Wi-Fi analyser and its coverage survey are
 * not reachable this way and are deliberately absent below.
 */
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {

    @get:Rule
    val baselineProfileRule = BaselineProfileRule()

    /**
     * Startup-only pass. Kept separate from [generateJourney] so the startup profile —
     * which drives dex layout — contains only genuine cold-start methods, not
     * scroll-warm ones.
     */
    @Test
    fun generateStartup() {
        baselineProfileRule.collect(
            packageName = PACKAGE_NAME,
            includeInStartupProfile = true,
        ) {
            // Standard launch: integrates with the profiling capture window and, on
            // androidx.benchmark 1.4.0+, no longer hits the API-37 launch-confirmation
            // regression that a manual launcher-intent workaround was previously needed for.
            pressHome()
            startActivityAndWait()
            device.waitForIdle()
        }
    }

    /**
     * Baseline-only pass: launch, the home-grid scroll, then the two feature screens.
     *
     * Deliberately NOT added to the startup profile — see [generateStartup]. Feature-screen
     * methods are not cold-start methods and would pollute dex layout.
     */
    @Test
    fun generateJourney() {
        baselineProfileRule.collect(packageName = PACKAGE_NAME) {
            pressHome()
            startActivityAndWait()
            device.waitForIdle()

            scrollGrid()

            // Both paths are in DeepLinkRouter.PATH_TO_ROUTE; the marker is each screen's
            // TopAppBar title. Adding a screen here means adding its path there first.
            navigateTo(route = "lanscan", arrivalMarker = "LAN Scan")
            navigateTo(route = "devices", arrivalMarker = "Devices")
        }
    }

    /**
     * Deep-links to [route] and blocks until [arrivalMarker] is on screen.
     *
     * Fails loudly on a miss. An unresolvable path leaves the app on Home, which would
     * otherwise capture Home's classes a second time and quietly ship a profile missing
     * the screen this step claims to cover.
     */
    private fun MacrobenchmarkScope.navigateTo(route: String, arrivalMarker: String) {
        device.executeShellCommand(
            "am start -a android.intent.action.VIEW " +
                "-d netlens://feature/$route -p $PACKAGE_NAME",
        )
        val arrived = device.wait(Until.hasObject(By.text(arrivalMarker)), NAV_TIMEOUT_MS)
        check(arrived) {
            "Deep link netlens://feature/$route never showed \"$arrivalMarker\" within " +
                "${NAV_TIMEOUT_MS}ms. Either the path is missing from " +
                "DeepLinkRouter.PATH_TO_ROUTE (unlisted paths silently stay on Home), " +
                "or the screen's title changed. Refusing to emit a profile that omits it."
        }
        device.waitForIdle()
    }

    /** Flings the home grid down a few times to warm grid composition, then back to top. */
    private fun MacrobenchmarkScope.scrollGrid() {
        // Fail loudly: a silent selector miss would quietly ship a launch-only profile
        // (the workflow's non-empty guard can't tell the difference).
        val grid = checkNotNull(device.findObject(By.scrollable(true))) {
            "Home grid not found — journey degraded to launch-only; refusing to emit a thin profile"
        }
        grid.setGestureMargin(device.displayWidth / 5)
        repeat(3) {
            grid.fling(Direction.DOWN)
            device.waitForIdle()
        }
        repeat(3) {
            grid.fling(Direction.UP)
            device.waitForIdle()
        }
    }

    private companion object {
        const val PACKAGE_NAME = "com.ventouxlabs.netlens"

        /** Generous: an emulator under CI load is slow, and a false negative fails the build. */
        const val NAV_TIMEOUT_MS = 10_000L
    }
}
