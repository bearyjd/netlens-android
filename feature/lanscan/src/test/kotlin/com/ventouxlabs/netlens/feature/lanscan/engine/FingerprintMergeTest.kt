package com.ventouxlabs.netlens.feature.lanscan.engine

import com.ventouxlabs.netlens.core.scan.model.DiscoveryMethod
import com.ventouxlabs.netlens.core.scan.model.LanDevice
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class FingerprintMergeTest {

    private fun device(
        deviceType: String? = null,
        osGuess: String? = null,
        confidence: Int = 0,
        evidence: List<String> = emptyList(),
        services: List<String> = emptyList(),
        discoveryMethod: DiscoveryMethod = DiscoveryMethod.PING,
    ) = LanDevice(
        ip = "192.168.1.100",
        deviceType = deviceType,
        osGuess = osGuess,
        fingerprintConfidence = confidence,
        fingerprintEvidence = evidence,
        services = services,
        discoveryMethod = discoveryMethod,
    )

    @Test
    fun `higher confidence candidate overwrites deviceType and osGuess`() {
        val existing = device(deviceType = "Phone", osGuess = "iOS", confidence = 40)
        val candidate = device(deviceType = "Chromecast", osGuess = "Android", confidence = 90)

        val result = strongerFingerprint(existing, candidate)

        assertEquals("Chromecast", result.deviceType)
        assertEquals("Android", result.osGuess)
        assertEquals(90, result.fingerprintConfidence)
    }

    @Test
    fun `lower confidence candidate is discarded`() {
        val existing = device(deviceType = "Chromecast", osGuess = "Android", confidence = 90)
        val candidate = device(deviceType = "Phone", osGuess = "iOS", confidence = 40)

        val result = strongerFingerprint(existing, candidate)

        assertEquals("Chromecast", result.deviceType)
        assertEquals("Android", result.osGuess)
        assertEquals(90, result.fingerprintConfidence)
    }

    @Test
    fun `equal confidence keeps existing, including the common zero-zero case`() {
        val existing = device(deviceType = null, osGuess = null, confidence = 0)
        val candidate = device(deviceType = "Router", osGuess = "Linux", confidence = 0)

        val result = strongerFingerprint(existing, candidate)

        assertEquals(null, result.deviceType)
        assertEquals(null, result.osGuess)
        assertEquals(0, result.fingerprintConfidence)
    }

    @Test
    fun `evidence always unions regardless of which type or os won`() {
        val existing = device(confidence = 90, evidence = listOf("mDNS: googlecast"))
        val candidate = device(confidence = 40, evidence = listOf("hostname: android-abc123"))

        val result = strongerFingerprint(existing, candidate)

        assertEquals(listOf("mDNS: googlecast", "hostname: android-abc123"), result.fingerprintEvidence)
    }

    @Test
    fun `services still union and de-duplicate regardless of confidence outcome`() {
        val existing = device(confidence = 90, services = listOf("_googlecast._tcp"))
        val candidate = device(confidence = 40, services = listOf("_googlecast._tcp", "_http._tcp"))

        val result = strongerFingerprint(existing, candidate)

        assertEquals(listOf("_googlecast._tcp", "_http._tcp"), result.services)
    }

    @Test
    fun `resulting confidence is always the max of the two inputs`() {
        val existing = device(deviceType = "Router", confidence = 90)
        val candidate = device(deviceType = "Printer", confidence = 40)

        val result = strongerFingerprint(existing, candidate)

        assertEquals(90, result.fingerprintConfidence)
        assertEquals("Router", result.deviceType)
    }

    // Regression: discoveryMethod used to be stamped MULTIPLE unconditionally, which was
    // correct for the original two-source merge (LanScanViewModel.mergeDevice's ping/mDNS/SSDP
    // jobs) but wrong when the same function reconciles a single device's own pre- and
    // post-fingerprint() classification — same discovery event, not multiple sources.
    @Test
    fun `same discoveryMethod on both inputs is not promoted to MULTIPLE`() {
        val existing = device(confidence = 40, discoveryMethod = DiscoveryMethod.SSDP)
        val candidate = device(confidence = 90, discoveryMethod = DiscoveryMethod.SSDP)

        val result = strongerFingerprint(existing, candidate)

        assertEquals(DiscoveryMethod.SSDP, result.discoveryMethod)
    }

    @Test
    fun `different discoveryMethod on the two inputs is promoted to MULTIPLE`() {
        val existing = device(confidence = 40, discoveryMethod = DiscoveryMethod.PING)
        val candidate = device(confidence = 90, discoveryMethod = DiscoveryMethod.MDNS)

        val result = strongerFingerprint(existing, candidate)

        assertEquals(DiscoveryMethod.MULTIPLE, result.discoveryMethod)
    }
}
