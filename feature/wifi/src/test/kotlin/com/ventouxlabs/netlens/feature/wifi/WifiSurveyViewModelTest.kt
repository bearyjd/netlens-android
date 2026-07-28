package com.ventouxlabs.netlens.feature.wifi

import androidx.lifecycle.ViewModelStore
import com.ventouxlabs.netlens.feature.wifi.model.SurveyError
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class WifiSurveyViewModelTest {

    private lateinit var sampler: FakeWifiSignalSampler
    private lateinit var dao: InMemoryWifiSurveyDao
    private lateinit var viewModel: WifiSurveyViewModel

    private val captureTarget = WifiSurveyViewModel.CAPTURE_SAMPLE_TARGET

    private lateinit var appScope: CoroutineScope

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        sampler = FakeWifiSignalSampler()
        dao = InMemoryWifiSurveyDao()
        // Stands in for the process-lifetime scope, so onCleared's write still runs after
        // viewModelScope is cancelled — exactly as it does in production.
        appScope = CoroutineScope(UnconfinedTestDispatcher() + SupervisorJob())
        viewModel = WifiSurveyViewModel(sampler, dao, appScope)
    }

    @AfterEach
    fun tearDown() {
        appScope.cancel()
        Dispatchers.resetMain()
    }

    private suspend fun startSurvey(name: String = "Home") {
        // The ViewModel takes one reading up front to confirm the phone is associated, so the
        // fake has to have something buffered before start is called.
        sampler.emit(-50)
        viewModel.startSurvey(name)
    }

    @Test
    fun `initial state is idle`() {
        val state = viewModel.state.value
        assertFalse(state.isSurveying)
        assertNull(state.activeSessionId)
        assertTrue(state.points.isEmpty())
    }

    @Test
    fun `starting a survey opens a session and begins sampling`() = runTest {
        startSurvey("Ground floor")

        val state = viewModel.state.value
        assertTrue(state.isSurveying)
        assertNotNull(state.activeSessionId)
        assertEquals("Ground floor", state.viewedSessionName)
        assertEquals(1, dao.sessions.value.size)
        assertEquals("HomeWiFi", dao.sessions.value.first().ssid)
        assertEquals(
            WifiSurveyViewModel.SAMPLE_INTERVAL_MS,
            sampler.requestedIntervals.last(),
        )
    }

    @Test
    fun `a blank name falls back to the connected SSID`() = runTest {
        startSurvey("   ")
        assertEquals("HomeWiFi", viewModel.state.value.viewedSessionName)
    }

    @Test
    fun `starting while disconnected reports the error and opens no session`() = runTest {
        // The sampler keeps polling and emitting nulls, exactly as it does on a phone that is not
        // associated — so only START_SAMPLE_TIMEOUT_MS elapsing ends this. Deleting the timeout
        // makes this test hang rather than pass, which is the point.
        sampler.connected = false

        viewModel.startSurvey("Home")
        advanceUntilIdle()

        assertEquals(SurveyError.NOT_CONNECTED, viewModel.state.value.error)
        assertFalse(viewModel.state.value.isSurveying)
        assertTrue(dao.sessions.value.isEmpty())
    }

    @Test
    fun `losing the signal mid-capture abandons the burst instead of stalling`() = runTest {
        startSurvey()
        viewModel.onLabelChanged("Garage")
        viewModel.capturePoint()
        // Part of a burst lands, then the phone walks out of range.
        sampler.emitBurst(captureTarget - 3, rssi = -80)
        assertNotNull(viewModel.state.value.capture)

        sampler.emitDisconnected()

        val state = viewModel.state.value
        assertNull(state.capture, "capture should not sit half-collected waiting for the radio")
        assertEquals(SurveyError.SIGNAL_LOST, state.error)
        assertNull(state.liveSample, "the meter must not keep showing the last good reading")
        assertTrue(state.isSurveying, "the survey stays open so walking back into range resumes it")
        assertTrue(dao.points.value.isEmpty(), "a partial burst must not be stored as a spot")
    }

    @Test
    fun `a dropped tick outside a capture blanks the meter without raising an error`() = runTest {
        startSurvey()

        sampler.emitDisconnected()

        assertNull(viewModel.state.value.liveSample)
        assertNull(viewModel.state.value.error)
    }

    @Test
    fun `live samples update the meter and bound the trail`() = runTest {
        startSurvey()

        repeat(WifiSurveyViewModel.TRAIL_LENGTH + 10) { sampler.emit(-60) }

        val state = viewModel.state.value
        assertEquals(-60, state.liveSample?.rssi)
        assertEquals(WifiSurveyViewModel.TRAIL_LENGTH, state.trail.size)
    }

    @Test
    fun `capturing without a label is refused`() = runTest {
        startSurvey()

        viewModel.capturePoint()

        assertEquals(SurveyError.LABEL_REQUIRED, viewModel.state.value.error)
        assertNull(viewModel.state.value.capture)
    }

    @Test
    fun `a capture burst persists one aggregated point`() = runTest {
        startSurvey()
        viewModel.onLabelChanged("Kitchen")
        viewModel.capturePoint()

        sampler.emitBurst(captureTarget, rssi = -64)

        val points = dao.points.value
        assertEquals(1, points.size)
        assertEquals("Kitchen", points.first().label)
        assertEquals(-64, points.first().avgRssi)
        assertEquals(captureTarget, points.first().sampleCount)
        assertEquals(viewModel.state.value.activeSessionId, points.first().sessionId)
    }

    @Test
    fun `capture progress advances and clears when the burst completes`() = runTest {
        startSurvey()
        viewModel.onLabelChanged("Kitchen")
        viewModel.capturePoint()

        sampler.emit(-60)
        assertEquals(1, viewModel.state.value.capture?.collected)

        sampler.emitBurst(captureTarget - 1, rssi = -60)
        assertNull(viewModel.state.value.capture)
        // The label is cleared so a reflex second tap can't re-record the same room.
        assertEquals("", viewModel.state.value.pendingLabel)
    }

    @Test
    fun `samples taken outside a capture window are not stored as points`() = runTest {
        startSurvey()
        sampler.emitBurst(captureTarget * 2, rssi = -70)

        assertTrue(dao.points.value.isEmpty())
    }

    @Test
    fun `cancelling a capture discards its samples`() = runTest {
        startSurvey()
        viewModel.onLabelChanged("Kitchen")
        viewModel.capturePoint()
        sampler.emitBurst(captureTarget - 1, rssi = -60)

        viewModel.cancelCapture()
        sampler.emitBurst(captureTarget, rssi = -90)

        // The cancelled burst didn't land, and its leftovers didn't get folded into a later one.
        assertTrue(dao.points.value.isEmpty())
        assertNull(viewModel.state.value.capture)
    }

    @Test
    fun `captured points appear in state ordered by capture time`() = runTest {
        startSurvey()
        capture("Kitchen", -55)
        capture("Garage", -85)

        assertEquals(listOf("Kitchen", "Garage"), viewModel.state.value.points.map { it.label })
    }

    @Test
    fun `stopping the survey ends the session and halts sampling`() = runTest {
        startSurvey()

        viewModel.stopSurvey()

        assertFalse(viewModel.state.value.isSurveying)
        assertNull(viewModel.state.value.activeSessionId)
        assertNotNull(dao.sessions.value.first().endedAt)

        // Post-stop readings must not be banked into a capture.
        viewModel.onLabelChanged("Kitchen")
        viewModel.capturePoint()
        sampler.emitBurst(captureTarget, rssi = -60)
        assertTrue(dao.points.value.isEmpty())
    }

    @Test
    fun `deleting a point removes it from the map`() = runTest {
        startSurvey()
        capture("Kitchen", -55)
        val pointId = dao.points.value.first().id

        viewModel.deletePoint(pointId)

        assertTrue(viewModel.state.value.points.isEmpty())
    }

    @Test
    fun `deleting the active session stops the survey`() = runTest {
        startSurvey()
        val sessionId = viewModel.state.value.activeSessionId!!

        viewModel.deleteSession(sessionId)

        assertFalse(viewModel.state.value.isSurveying)
        assertNull(viewModel.state.value.viewedSessionId)
        assertTrue(dao.sessions.value.isEmpty())
    }

    @Test
    fun `past sessions can be viewed once the survey is stopped`() = runTest {
        startSurvey("First pass")
        capture("Kitchen", -55)
        val firstId = viewModel.state.value.activeSessionId!!
        viewModel.stopSurvey()

        startSurvey("Second pass")
        capture("Garage", -85)
        viewModel.stopSurvey()

        viewModel.selectSession(firstId)

        assertEquals("First pass", viewModel.state.value.viewedSessionName)
        assertEquals(listOf("Kitchen"), viewModel.state.value.points.map { it.label })
    }

    @Test
    fun `switching sessions mid-survey is refused so captures cannot be misfiled`() = runTest {
        startSurvey("First pass")
        viewModel.stopSurvey()
        val firstId = dao.sessions.value.first().id

        startSurvey("Second pass")
        viewModel.selectSession(firstId)

        assertEquals("Second pass", viewModel.state.value.viewedSessionName)
    }

    @Test
    fun `session summaries roll up point counts and the worst reading`() = runTest {
        startSurvey("Home")
        capture("Kitchen", -55)
        capture("Garage", -85)

        val summary = viewModel.state.value.sessions.first()
        assertEquals(2, summary.pointCount)
        assertEquals(-85, summary.worstRssi)
        assertEquals(-55, summary.bestRssi)
    }

    @Test
    fun `export lists points and calls out the weak ones`() = runTest {
        startSurvey("Home")
        capture("Kitchen", -55)
        capture("Garage", -85)

        val text = viewModel.buildExportText()
        assertTrue(text.contains("Wi-Fi coverage survey: Home"))
        assertTrue(text.contains("Points: 2"))
        assertTrue(text.contains("Weak spots (1): Garage"))
        assertTrue(text.contains("Kitchen  -55 dBm"))
    }

    @Test
    fun `export shortens the BSSID to its last two octets`() = runTest {
        startSurvey("Home")
        viewModel.onLabelChanged("Kitchen")
        viewModel.capturePoint()
        sampler.emitBurst(captureTarget, rssi = -55, bssid = "aa:bb:cc:dd:ee:01")

        val text = viewModel.buildExportText()
        // An export leaves the device, and a full BSSID resolves to a street address.
        assertTrue(text.contains("AP ee:01"), "expected the short form, got: $text")
        assertFalse(text.contains("aa:bb:cc:dd:ee:01"), "full BSSID leaked into the export")
    }

    @Test
    fun `a second start tap before the first reading opens only one session`() = runTest {
        // Nothing buffered, so the first call is parked inside firstSampleOrNull — the window
        // in which isSurveying is still false and used to let a second tap through.
        viewModel.startSurvey("Home")
        viewModel.startSurvey("Home")

        sampler.emit(-50)

        assertEquals(1, dao.sessions.value.size)
        assertTrue(viewModel.state.value.isSurveying)
    }

    @Test
    fun `a failed start releases the guard so the next tap can retry`() = runTest {
        sampler.connected = false
        viewModel.startSurvey("Home")
        advanceUntilIdle()
        assertEquals(SurveyError.NOT_CONNECTED, viewModel.state.value.error)

        sampler.connected = true
        startSurvey("Home")

        assertTrue(viewModel.state.value.isSurveying)
        assertEquals(1, dao.sessions.value.size)
    }

    @Test
    fun `navigating away closes the abandoned session`() = runTest {
        startSurvey("Home")
        val sessionId = requireNotNull(viewModel.state.value.activeSessionId)

        // Clearing the store is what the nav host does when the Wi-Fi entry leaves the back
        // stack; it cancels viewModelScope before onCleared runs.
        ViewModelStore().apply { put("survey", viewModel) }.clear()

        assertNotNull(
            dao.sessions.value.single { it.id == sessionId }.endedAt,
            "session was left open, so it can never be finished from the UI",
        )
    }

    @Test
    fun `backgrounding stops sampling but keeps the session open`() = runTest {
        startSurvey()
        val sessionId = requireNotNull(viewModel.state.value.activeSessionId)

        viewModel.onScreenStopped()
        sampler.emit(-70)

        assertNull(viewModel.state.value.liveSample, "the radio should no longer be feeding the UI")
        assertTrue(viewModel.state.value.isSurveying)
        assertNull(dao.sessions.value.single { it.id == sessionId }.endedAt)

        viewModel.onScreenStarted()
        sampler.emit(-65)
        assertEquals(-65, viewModel.state.value.liveSample?.rssi, "returning resumes the walk")
    }

    @Test
    fun `backgrounding mid-capture abandons the burst rather than splitting it`() = runTest {
        startSurvey()
        viewModel.onLabelChanged("Landing")
        viewModel.capturePoint()
        sampler.emitBurst(captureTarget - 2, rssi = -70)

        viewModel.onScreenStopped()

        assertNull(viewModel.state.value.capture)
        assertEquals(SurveyError.CAPTURE_INTERRUPTED, viewModel.state.value.error)
        assertTrue(dao.points.value.isEmpty(), "a burst spanning a background gap is not one spot")

        // And the abandoned samples must not be folded into the next capture.
        viewModel.onScreenStarted()
        viewModel.onLabelChanged("Landing")
        viewModel.capturePoint()
        sampler.emitBurst(captureTarget, rssi = -50)
        assertEquals(1, dao.points.value.size)
        assertEquals(-50, dao.points.value.single().avgRssi)
        assertEquals(captureTarget, dao.points.value.single().sampleCount)
    }

    @Test
    fun `a configuration change keeps the capture instead of blaming the background`() = runTest {
        startSurvey()
        viewModel.onLabelChanged("Landing")
        viewModel.capturePoint()
        sampler.emitBurst(captureTarget - 2, rssi = -70)

        // Rotation, fold or font-size change: ON_STOP fires, but the user never left.
        viewModel.onScreenStopped(isConfigurationChange = true)

        assertNotNull(viewModel.state.value.capture, "a fold must not destroy an in-progress burst")
        assertNull(viewModel.state.value.error)

        // The recreated screen resumes and the same burst completes into one point.
        viewModel.onScreenStarted()
        sampler.emitBurst(2, rssi = -70)
        assertEquals(1, dao.points.value.size)
        assertEquals("Landing", dao.points.value.single().label)
    }

    @Test
    fun `a capture held across a long stop is discarded rather than spanning two rooms`() = runTest {
        var clock = 10_000L
        viewModel.nowMs = { clock }
        startSurvey()
        viewModel.onLabelChanged("Landing")
        viewModel.capturePoint()
        sampler.emitBurst(captureTarget - 2, rssi = -70)

        // Folded — held, as a recreation is sub-second.
        viewModel.onScreenStopped(isConfigurationChange = true)
        assertNotNull(viewModel.state.value.capture)

        // ...but the phone went in a pocket and came back out somewhere else.
        clock += WifiSurveyViewModel.MAX_CAPTURE_GAP_MS + 1
        viewModel.onScreenStarted()

        assertNull(viewModel.state.value.capture, "a burst may not span an unbounded gap")
        assertEquals(SurveyError.CAPTURE_INTERRUPTED, viewModel.state.value.error)
        assertTrue(dao.points.value.isEmpty(), "no spot may be recorded from a split burst")

        // And the discarded samples are not folded into the next capture.
        viewModel.onLabelChanged("Landing")
        viewModel.capturePoint()
        sampler.emitBurst(captureTarget, rssi = -50)
        assertEquals(1, dao.points.value.size)
        assertEquals(-50, dao.points.value.single().avgRssi)
        assertEquals(captureTarget, dao.points.value.single().sampleCount)
    }

    @Test
    fun `backgrounding during the start window cancels the start instead of leaving it polling`() =
        runTest {
            // Nothing buffered: the start is parked waiting for its first reading.
            viewModel.startSurvey("Home")
            viewModel.onScreenStopped()

            // The reading the start was waiting for now arrives.
            sampler.emit(-50)
            advanceUntilIdle()

            assertFalse(
                viewModel.state.value.isSurveying,
                "a start cancelled by backgrounding must not come back and begin sampling",
            )
            // The defect was a *poll* that kept running, so assert on the collector itself and not
            // only on the state it would have produced.
            assertEquals(
                1,
                sampler.requestedIntervals.size,
                "a cancelled start must not have opened a second sampling collector",
            )
            assertNull(viewModel.state.value.liveSample)
            // And the guard is released, so returning and tapping Start still works.
            startSurvey("Home")
            assertTrue(viewModel.state.value.isSurveying)
        }

    @Test
    fun `a row written while the start is cancelled mid-insert is still closed`() = runTest {
        // Hold the coroutine *inside* insertSession. This is the window that orphaned rows:
        // activeSessionId is not published yet, so onCleared has nothing to close.
        val gate = CompletableDeferred<Unit>()
        dao.blockInsert = gate
        sampler.emit(-50)
        viewModel.startSurvey("Home")
        advanceUntilIdle()
        assertTrue(dao.sessions.value.isEmpty(), "insert should still be in flight")

        // The ViewModel dies while the insert is parked.
        ViewModelStore().apply { put("survey", viewModel) }.clear()
        gate.complete(Unit)
        advanceUntilIdle()

        val session = dao.sessions.value.singleOrNull()
        assertNotNull(session, "the insert completes even though the coroutine was cancelled")
        assertNotNull(
            session!!.endedAt,
            "a row written by a cancelled start must not be left with a null endedAt",
        )
    }

    private suspend fun capture(label: String, rssi: Int) {
        viewModel.onLabelChanged(label)
        viewModel.capturePoint()
        sampler.emitBurst(captureTarget, rssi = rssi)
    }
}
