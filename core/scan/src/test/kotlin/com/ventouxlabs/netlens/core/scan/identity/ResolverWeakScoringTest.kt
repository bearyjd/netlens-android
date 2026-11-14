package com.ventouxlabs.netlens.core.scan.identity

import com.ventouxlabs.netlens.core.scan.model.LanDevice
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

/** Step 6. Weak scoring only suggests; it never merges and only runs for New devices. */
class ResolverWeakScoringTest {
    private val h = IdentityHarness()
    private val day = 86_400_000L

    private suspend fun seedTv(lastSeen: Long = NOW - day, hostname: String = "living-room-tv") =
        h.seedRow(101, "10.0.9.9", ids = listOf(universalMac("aabbcc0000ff")), mac = "AA:BB:CC:00:00:FF", hostname = hostname, model = "XR-55", vendor = "Sony", deviceType = "tv", lastSeen = lastSeen)

    private fun newTv(hostname: String = "Living-Room-TV-2.local", model: String? = "XR-55", vendor: String? = null, type: String? = null) =
        LanDevice(ip = "192.168.5.5", macAddress = MAC1, hostname = hostname, model = model, vendor = vendor, deviceType = type)

    @Test
    fun `model plus normalised hostname scores 70 and is suggested, not merged`() = runTest {
        seedTv()

        val result = h.resolve(newTv())

        assertEquals(Resolution.New, result.devices.single().resolution)
        val s = result.suggestions.single() as MergeSuggestion.ForNewDevice
        assertEquals(101L, s.existingDeviceId)
        assertEquals(70, s.score)
        assertEquals(listOf("model", "hostname"), s.reasons)
        assertEquals(1, h.known.allDevices.size)
    }

    @Test
    fun `every term adds up and vendor and type each add ten`() = runTest {
        seedTv()

        val s = h.resolve(newTv(vendor = "sony", type = "tv")).suggestions.single() as MergeSuggestion.ForNewDevice

        assertEquals(90, s.score)
        assertEquals(listOf("model", "hostname", "vendor", "device_type"), s.reasons)
    }

    @Test
    fun `a score of exactly 60 is suggested and 50 is not`() = runTest {
        seedTv()

        // model 40 + vendor 10 + type 10 = 60
        assertEquals(60, (h.resolve(newTv(hostname = "different", vendor = "Sony", type = "tv")).suggestions.single() as MergeSuggestion.ForNewDevice).score)
        // model 40 + vendor 10 = 50
        assertTrue(h.resolve(newTv(hostname = "different", vendor = "Sony")).suggestions.isEmpty())
    }

    @Test
    fun `rows not seen for more than 180 days are ignored`() = runTest {
        seedTv(lastSeen = NOW - 181 * day)

        assertTrue(h.resolve(newTv()).suggestions.isEmpty())
    }

    @Test
    fun `an Existing result is never scored`() = runTest {
        seedTv()
        h.seedRow(102, "10.0.0.2", ids = listOf(universalMac(MAC1_ID)), mac = MAC1)

        assertTrue(h.resolve(newTv()).suggestions.isEmpty())
    }

    @Test
    fun `persisting a suggestion writes SUGGESTED with the lower id first`() = runTest {
        seedTv()
        val s = h.resolve(newTv()).suggestions.single() as MergeSuggestion.ForNewDevice

        h.resolver.persistSuggestion(newDeviceId = 7, suggestion = s, now = NOW)

        val row = h.decisions.get(7, 101)!!
        assertEquals("SUGGESTED", row.decision)
        assertEquals(70, row.score)
        assertEquals("model,hostname", row.reasons)
    }

    @Test
    fun `persisting never overrides a DISTINCT decision`() = runTest {
        seedTv()
        val s = h.resolve(newTv()).suggestions.single() as MergeSuggestion.ForNewDevice
        h.decisions.setDistinct(7, 101, 1)

        h.resolver.persistSuggestion(7, s, NOW)

        assertEquals("DISTINCT", h.decisions.get(7, 101)?.decision)
    }

    @ParameterizedTest
    @CsvSource(
        "Living-Room-TV-2.local, living-room-tv",
        "LIVING-ROOM-TV, living-room-tv",
        "printer-12, printer",
        "printer.local, printer",
        "iPhone, iphone",
        "host-2-b, host-2-b",
    )
    fun `hostname normalisation strips a numeric suffix, the local domain and case`(raw: String, expected: String) {
        assertEquals(expected, IdentityPolicy.normalizeHostname(raw))
    }
}
