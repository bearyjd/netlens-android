package com.ventouxlabs.netlens.baselineprofile

import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
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
 *
 * Scoped intentionally to the guaranteed-hot cold-start + home-grid path. Deeper
 * per-feature navigation was dropped: on this device UiAutomator text lookups for the
 * tool labels were unreliable, and a journey step that silently no-ops adds risk (an
 * empty profile) without adding captured classes. Cold start + grid scroll is a valid,
 * high-value startup+baseline profile on its own.
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

    /** Baseline-only pass: launch plus the home-grid scroll journey. */
    @Test
    fun generateJourney() {
        baselineProfileRule.collect(packageName = PACKAGE_NAME) {
            pressHome()
            startActivityAndWait()
            device.waitForIdle()

            scrollGrid()
        }
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
    }
}
