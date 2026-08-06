package com.ventouxlabs.netlens.baselineprofile

import android.os.SystemClock
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
            dismissRuntimePermissionDialog()

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
        awaitScreen(arrivalMarker, route)
    }

    /**
     * Polls until [marker] is on screen, clearing runtime-permission dialogs as they appear.
     *
     * **Why a poll and not a single `wait`.** The app requests POST_NOTIFICATIONS shortly
     * after launch (Devices' "Background new-device alerts"), and on a fresh emulator — which
     * has never been asked — the dialog covers whatever is on screen. *When* it lands is
     * non-deterministic: it blocked `devices` on two runs and `lanscan` on the next. Anything
     * that dismisses once, at a fixed point, races it. Re-checking each iteration removes the
     * ordering assumption entirely.
     *
     * A phone that has already answered never shows it, and `pm revoke` does NOT recreate the
     * condition — revoking after a grant sets `USER_SET`, which Android reads as "already
     * declined" and suppresses the re-prompt. Hardware testing will wrongly clear this.
     */
    private fun MacrobenchmarkScope.awaitScreen(marker: String, route: String) {
        val deadline = SystemClock.uptimeMillis() + NAV_TIMEOUT_MS
        while (SystemClock.uptimeMillis() < deadline) {
            dismissRuntimePermissionDialog()
            if (device.hasObject(By.text(marker))) return
            SystemClock.sleep(POLL_INTERVAL_MS)
        }
        val visible = device.findObjects(By.clazz("android.widget.TextView"))
            .mapNotNull { it.text?.takeIf(String::isNotBlank) }
            .distinct()
            .take(12)
        error(
            "Deep link netlens://feature/$route never showed \"$marker\" within " +
                "${NAV_TIMEOUT_MS}ms. Visible text was: $visible. " +
                "A permission dialog here means dismissal failed — check the " +
                "permissioncontroller package id. Text that looks like Home means the path " +
                "is missing from DeepLinkRouter.PATH_TO_ROUTE (unlisted paths resolve to " +
                "null and stay on Home). The right screen means the title changed. " +
                "Refusing to emit a profile that omits it.",
        )
    }

    /**
     * Clicks "deny" on a runtime-permission dialog if one is up. No-op otherwise.
     *
     * Tries **both** permissioncontroller package names: AOSP images use
     * `com.android.permissioncontroller`, but the CI emulator runs a `google_apis` image where
     * it is `com.google.android.permissioncontroller`. A previous version checked only the
     * AOSP id, found nothing, and returned silently — so the dialog stayed up and the run
     * failed 30s later blaming navigation. Resource ids are matched before text because
     * button wording is localized.
     *
     * Denies rather than allows: the profile should capture the app, and granting would let a
     * notification channel and WorkManager scheduling run inside the capture window.
     */
    private fun MacrobenchmarkScope.dismissRuntimePermissionDialog() {
        val button = PERMISSION_CONTROLLER_PKGS
            .firstNotNullOfOrNull { pkg -> device.findObject(By.res(pkg, "permission_deny_button")) }
            ?: device.findObject(By.textStartsWith("Don't allow"))
            ?: device.findObject(By.text("Deny"))
            ?: return
        button.click()
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

        /**
         * A CI emulator runs on a software GPU (swiftshader) under shared load, so screen
         * transitions are far slower than on a phone. 10s was measured to be too short: the
         * SECOND navigation timed out on Devices even though the same deep link lands in
         * ~5s on hardware and UiAutomator does see the "Devices" title there. Cost of being
         * generous is a slower failure; cost of being tight is a red build and 20 wasted
         * CI minutes.
         */
        const val NAV_TIMEOUT_MS = 30_000L

        /**
         * AOSP images use the first; the CI emulator's `google_apis` image uses the second.
         * Checking only one is why an earlier dismissal silently did nothing.
         */
        val PERMISSION_CONTROLLER_PKGS = listOf(
            "com.android.permissioncontroller",
            "com.google.android.permissioncontroller",
        )

        const val POLL_INTERVAL_MS = 250L
    }
}
