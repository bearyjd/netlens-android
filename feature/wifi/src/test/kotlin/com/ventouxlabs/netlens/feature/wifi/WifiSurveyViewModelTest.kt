package com.ventouxlabs.netlens.feature.wifi

import com.ventouxlabs.netlens.feature.wifi.model.SurveyError
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
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

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        sampler = FakeWifiSignalSampler()
        dao = InMemoryWifiSurveyDao()
        viewModel = WifiSurveyViewModel(sampler, dao)
    }

    @AfterEach
    fun tearDown() = Dispatchers.resetMain()

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
        sampler.connected = false

        viewModel.startSurvey("Home")

        assertEquals(SurveyError.NOT_CONNECTED, viewModel.state.value.error)
        assertFalse(viewModel.state.value.isSurveying)
        assertTrue(dao.sessions.value.isEmpty())
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

    private suspend fun capture(label: String, rssi: Int) {
        viewModel.onLabelChanged(label)
        viewModel.capturePoint()
        sampler.emitBurst(captureTarget, rssi = rssi)
    }
}
