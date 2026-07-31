package com.ventouxlabs.netlens.feature.wifi

import app.cash.paparazzi.Paparazzi
import com.ventouxlabs.netlens.core.data.model.WifiSurveyPointEntity
import com.ventouxlabs.netlens.core.data.model.WifiSurveySessionSummary
import com.ventouxlabs.netlens.feature.wifi.model.CaptureProgress
import com.ventouxlabs.netlens.feature.wifi.model.SurveyError
import com.ventouxlabs.netlens.feature.wifi.model.WifiSurveyUiState
import org.junit.Rule
import org.junit.Test

/**
 * Composition smoke tests — does this screen *render*, not does it look right.
 *
 * No golden images: nothing is recorded, no PNG is committed, `verifyPaparazzi` never runs in CI.
 * Duplicate keys, composition errors and measure/layout failures all throw at render time, so
 * simply composing the screen is the assertion. See `AndroidScreenshotConventionPlugin` for why
 * that is the whole point rather than a shortcut.
 *
 * The exception escapes through the JUnit rule rather than out of `snapshot { }`, so these tests
 * deliberately assert nothing — a render failure fails the test on its own. Wrapping them in
 * `try/catch` would silently pass.
 */
class WifiSurveyTabRenderTest {

    @get:Rule
    val paparazzi = Paparazzi()

    private fun point(id: Long, sessionId: Long, label: String, rssi: Int) = WifiSurveyPointEntity(
        id = id,
        sessionId = sessionId,
        label = label,
        capturedAt = 1_000L + id,
        avgRssi = rssi,
        minRssi = rssi - 4,
        maxRssi = rssi + 4,
        sampleCount = 8,
        bssid = "aa:bb:cc:dd:ee:0$id",
        frequency = 5180,
        channel = 36,
        linkSpeedMbps = 400,
    )

    private fun session(id: Long, name: String) = WifiSurveySessionSummary(
        id = id,
        name = name,
        ssid = "HomeNet",
        startedAt = 1_000L,
        endedAt = 2_000L,
        pointCount = 3,
        worstRssi = -78,
        bestRssi = -42,
    )

    private fun render(state: WifiSurveyUiState) = paparazzi.snapshot {
        WifiSurveyTab(
            state = state,
            onStartSurvey = {},
            onStopSurvey = {},
            onLabelChanged = {},
            onCapturePoint = {},
            onCancelCapture = {},
            onDeletePoint = {},
            onSelectSession = {},
            onDeleteSession = {},
            onToggleSort = {},
        )
    }

    @Test
    fun `a point and a session sharing row id 1 render together`() {
        // THE #116 REGRESSION. Points and sessions come from different tables with independent
        // autoincrement sequences, so id 1 exists in both. Two items() in one LazyColumn keyed on
        // the raw id therefore collide the moment a survey first holds both — which is the *first
        // capture*, the feature's primary path. It shipped through three review passes, an
        // adversarial round and 750 green unit tests, and a two-minute walk found it instantly.
        //
        // Fixed in dc03409 by namespacing through surveyPointKey/surveySessionKey. Reverting that
        // makes this test fail with the exact production message:
        //   IllegalArgumentException: Key "1" was already used.
        render(
            WifiSurveyUiState(
                viewedSessionId = 1,
                viewedSessionName = "Ground floor",
                points = listOf(point(id = 1, sessionId = 1, label = "Kitchen", rssi = -55)),
                sessions = listOf(session(id = 1, name = "Ground floor")),
            ),
        )
    }

    @Test
    fun `many points and many sessions with overlapping ids render`() {
        // The collision is not limited to id 1 — every overlapping pair is a candidate.
        render(
            WifiSurveyUiState(
                viewedSessionId = 2,
                viewedSessionName = "Whole house",
                points = (1L..5L).map { point(it, sessionId = 2, label = "Room $it", rssi = -50 - it.toInt() * 6) },
                sessions = (1L..5L).map { session(it, name = "Walk $it") },
            ),
        )
    }

    @Test
    fun `the idle empty state renders`() {
        render(WifiSurveyUiState())
    }

    @Test
    fun `a capture in flight renders`() {
        render(
            WifiSurveyUiState(
                isSurveying = true,
                activeSessionId = 1,
                viewedSessionId = 1,
                viewedSessionName = "Ground floor",
                capture = CaptureProgress(label = "Hallway", collected = 3, target = 8),
                points = listOf(point(id = 1, sessionId = 1, label = "Kitchen", rssi = -55)),
            ),
        )
    }

    @Test
    fun `an error banner renders alongside results`() {
        render(
            WifiSurveyUiState(
                viewedSessionId = 1,
                viewedSessionName = "Ground floor",
                points = listOf(point(id = 1, sessionId = 1, label = "Kitchen", rssi = -55)),
                sessions = listOf(session(id = 1, name = "Ground floor")),
                error = SurveyError.CAPTURE_INTERRUPTED,
            ),
        )
    }

    @Test
    fun `the worst-first sort renders`() {
        render(
            WifiSurveyUiState(
                viewedSessionId = 1,
                viewedSessionName = "Ground floor",
                points = (1L..4L).map { point(it, sessionId = 1, label = "Room $it", rssi = -45 - it.toInt() * 10) },
                sortWorstFirst = true,
            ),
        )
    }
}
