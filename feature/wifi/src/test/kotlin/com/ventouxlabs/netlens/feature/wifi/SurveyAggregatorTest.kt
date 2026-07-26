package com.ventouxlabs.netlens.feature.wifi

import com.ventouxlabs.netlens.core.data.model.WifiSurveyPointEntity
import com.ventouxlabs.netlens.feature.wifi.engine.SurveyAggregator
import com.ventouxlabs.netlens.feature.wifi.model.SignalQuality
import com.ventouxlabs.netlens.feature.wifi.model.WifiSignalSample
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SurveyAggregatorTest {

    private fun sample(
        rssi: Int,
        bssid: String? = "aa:bb:cc:dd:ee:01",
        frequency: Int = 5180,
        linkSpeed: Int = 400,
        timestamp: Long = 1_000L,
    ) = WifiSignalSample(
        timestampMs = timestamp,
        rssi = rssi,
        ssid = "HomeWiFi",
        bssid = bssid,
        frequency = frequency,
        linkSpeedMbps = linkSpeed,
    )

    @Test
    fun `aggregate returns null for an empty burst`() {
        assertNull(SurveyAggregator.aggregate(1, "Kitchen", emptyList(), 0))
    }

    @Test
    fun `aggregate averages and records the spread`() {
        val point = SurveyAggregator.aggregate(
            sessionId = 7,
            label = "Kitchen",
            samples = listOf(sample(-60), sample(-70), sample(-65)),
            capturedAt = 5_000L,
        )!!

        assertEquals(7, point.sessionId)
        assertEquals("Kitchen", point.label)
        assertEquals(-65, point.avgRssi)
        assertEquals(-70, point.minRssi)
        assertEquals(-60, point.maxRssi)
        assertEquals(3, point.sampleCount)
        assertEquals(5_000L, point.capturedAt)
    }

    @Test
    fun `aggregate rounds the average rather than truncating toward zero`() {
        // -60, -65 and -66 average to -63.67. Truncating would report -63 — a better signal
        // than was actually measured. (An exact .5 average breaks upward, per roundToInt;
        // half a dB either way is well inside the noise of a real reading.)
        val samples = listOf(sample(-60), sample(-65), sample(-66))
        assertEquals(-64, SurveyAggregator.aggregate(1, "Hall", samples, 0)!!.avgRssi)
    }

    @Test
    fun `aggregate derives the channel from the last sample frequency`() {
        val point = SurveyAggregator.aggregate(1, "Den", listOf(sample(-50, frequency = 2437)), 0)!!
        assertEquals(6, point.channel)
        assertEquals(2437, point.frequency)
    }

    @Test
    fun `aggregate keeps the AP that served most of the burst`() {
        val samples = listOf(
            sample(-60, bssid = "aa:bb:cc:dd:ee:01"),
            sample(-62, bssid = "aa:bb:cc:dd:ee:01"),
            sample(-80, bssid = "aa:bb:cc:dd:ee:02"),
        )
        assertEquals("aa:bb:cc:dd:ee:01", SurveyAggregator.aggregate(1, "Stairs", samples, 0)?.bssid)
    }

    @Test
    fun `aggregate tolerates samples with no BSSID`() {
        val point = SurveyAggregator.aggregate(1, "Attic", listOf(sample(-70, bssid = null)), 0)!!
        assertNull(point.bssid)
    }

    @Test
    fun `roam detection flags a BSSID change mid-capture`() {
        assertFalse(SurveyAggregator.roamedDuringCapture(listOf(sample(-60), sample(-61))))
        assertTrue(
            SurveyAggregator.roamedDuringCapture(
                listOf(sample(-60, bssid = "a"), sample(-61, bssid = "b")),
            ),
        )
    }

    @Test
    fun `weak spots are the weak-or-worse points, worst first`() {
        val points = listOf(
            point("Kitchen", -55),
            point("Garage", -88),
            point("Landing", -72),
            point("Shed", -80),
        )
        assertEquals(listOf("Garage", "Shed"), SurveyAggregator.weakSpots(points).map { it.label })
    }

    @Test
    fun `fair signal is not reported as a weak spot`() {
        // -72 dBm still works; flagging it would bury the rooms that genuinely need attention.
        assertFalse(SignalQuality.forRssi(-72).isWeakSpot)
        assertTrue(SurveyAggregator.weakSpots(listOf(point("Landing", -72))).isEmpty())
    }

    private fun point(label: String, avg: Int) = WifiSurveyPointEntity(
        sessionId = 1,
        label = label,
        capturedAt = 0,
        avgRssi = avg,
        minRssi = avg - 2,
        maxRssi = avg + 2,
        sampleCount = 8,
        bssid = null,
        frequency = 5180,
        channel = 36,
        linkSpeedMbps = 400,
    )
}
