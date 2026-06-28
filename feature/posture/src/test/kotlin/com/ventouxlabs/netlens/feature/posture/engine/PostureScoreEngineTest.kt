package com.ventouxlabs.netlens.feature.posture.engine

import com.ventouxlabs.netlens.core.network.VpnState
import com.ventouxlabs.netlens.feature.posture.model.Severity
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class PostureScoreEngineTest {

    @Nested
    inner class GradeThresholds {
        @Test
        fun `score 90 or above is A`() {
            assertEquals("A", PostureScoreEngine.gradeFor(90))
            assertEquals("A", PostureScoreEngine.gradeFor(100))
        }

        @Test
        fun `score 75 to 89 is B`() {
            assertEquals("B", PostureScoreEngine.gradeFor(75))
            assertEquals("B", PostureScoreEngine.gradeFor(89))
        }

        @Test
        fun `score 60 to 74 is C`() {
            assertEquals("C", PostureScoreEngine.gradeFor(60))
            assertEquals("C", PostureScoreEngine.gradeFor(74))
        }

        @Test
        fun `score 40 to 59 is D`() {
            assertEquals("D", PostureScoreEngine.gradeFor(40))
            assertEquals("D", PostureScoreEngine.gradeFor(59))
        }

        @Test
        fun `score below 40 is F`() {
            assertEquals("F", PostureScoreEngine.gradeFor(39))
            assertEquals("F", PostureScoreEngine.gradeFor(0))
        }
    }

    @Nested
    inner class Encryption {
        @Test
        fun `WPA3 scores 100 with Good severity`() {
            val result = PostureScoreEngine.evaluateEncryption("WPA3")
            assertEquals(100, result.score)
            assertEquals(Severity.Good, result.severity)
        }

        @Test
        fun `WPA2 scores 70 with Moderate severity`() {
            val result = PostureScoreEngine.evaluateEncryption("WPA2")
            assertEquals(70, result.score)
            assertEquals(Severity.Moderate, result.severity)
        }

        @Test
        fun `WEP scores 20 with Critical severity`() {
            val result = PostureScoreEngine.evaluateEncryption("WEP")
            assertEquals(20, result.score)
            assertEquals(Severity.Critical, result.severity)
        }

        @Test
        fun `OWE scores 80 with Good severity`() {
            val result = PostureScoreEngine.evaluateEncryption("OWE")
            assertEquals(80, result.score)
            assertEquals(Severity.Good, result.severity)
        }

        @Test
        fun `ENHANCED_OPEN scores 80 with Good severity`() {
            val result = PostureScoreEngine.evaluateEncryption("ENHANCED_OPEN")
            assertEquals(80, result.score)
            assertEquals(Severity.Good, result.severity)
        }

        @Test
        fun `null encryption is Unavailable for non-WiFi`() {
            val result = PostureScoreEngine.evaluateEncryption(null)
            assertEquals(0, result.score)
            assertEquals(Severity.Unavailable, result.severity)
        }

        @Test
        fun `empty encryption scores 0 with Critical severity for open WiFi`() {
            val result = PostureScoreEngine.evaluateEncryption("")
            assertEquals(0, result.score)
            assertEquals(Severity.Critical, result.severity)
        }

        @Test
        fun `Open string scores 0 with Critical severity`() {
            val result = PostureScoreEngine.evaluateEncryption("Open")
            assertEquals(0, result.score)
            assertEquals(Severity.Critical, result.severity)
            assertEquals("Open / None", result.label)
        }

        @Test
        fun `case insensitive matching`() {
            assertEquals(100, PostureScoreEngine.evaluateEncryption("wpa3").score)
            assertEquals(70, PostureScoreEngine.evaluateEncryption("wpa2-psk").score)
            assertEquals(80, PostureScoreEngine.evaluateEncryption("owe").score)
            assertEquals(0, PostureScoreEngine.evaluateEncryption("open").score)
            assertEquals(0, PostureScoreEngine.evaluateEncryption("OPEN").score)
        }
    }

    @Nested
    inner class DeviceCount {
        @Test
        fun `0 to 5 devices scores 100`() {
            assertEquals(100, PostureScoreEngine.evaluateDeviceCount(0).score)
            assertEquals(100, PostureScoreEngine.evaluateDeviceCount(5).score)
        }

        @Test
        fun `6 to 15 devices scores 80`() {
            assertEquals(80, PostureScoreEngine.evaluateDeviceCount(6).score)
            assertEquals(80, PostureScoreEngine.evaluateDeviceCount(15).score)
        }

        @Test
        fun `16 to 30 devices scores 60`() {
            assertEquals(60, PostureScoreEngine.evaluateDeviceCount(16).score)
            assertEquals(60, PostureScoreEngine.evaluateDeviceCount(30).score)
        }

        @Test
        fun `over 30 devices scores 40`() {
            assertEquals(40, PostureScoreEngine.evaluateDeviceCount(31).score)
        }

        @Test
        fun `pluralization correct`() {
            assertEquals("1 device", PostureScoreEngine.evaluateDeviceCount(1).label)
            assertEquals("5 devices", PostureScoreEngine.evaluateDeviceCount(5).label)
        }
    }

    @Nested
    inner class Vpn {
        @Test
        fun `VPN FullTunnel scores 100`() {
            val result = PostureScoreEngine.evaluateVpn(VpnState.FullTunnel, untrustedNetwork = false)
            assertEquals(100, result.score)
            assertEquals(Severity.Good, result.severity)
        }

        @Test
        fun `VPN SplitTunnel on trusted scores 80`() {
            val result = PostureScoreEngine.evaluateVpn(VpnState.SplitTunnel, untrustedNetwork = false)
            assertEquals(80, result.score)
            assertEquals(Severity.Good, result.severity)
        }

        @Test
        fun `VPN SplitTunnel on untrusted scores 60`() {
            val result = PostureScoreEngine.evaluateVpn(VpnState.SplitTunnel, untrustedNetwork = true)
            assertEquals(60, result.score)
            assertEquals(Severity.Moderate, result.severity)
        }

        @Test
        fun `VPN None on trusted scores 60`() {
            val result = PostureScoreEngine.evaluateVpn(VpnState.None, untrustedNetwork = false)
            assertEquals(60, result.score)
            assertEquals(Severity.Moderate, result.severity)
        }

        @Test
        fun `VPN None on untrusted scores 30`() {
            val result = PostureScoreEngine.evaluateVpn(VpnState.None, untrustedNetwork = true)
            assertEquals(30, result.score)
            assertEquals(Severity.Poor, result.severity)
        }
    }

    @Nested
    inner class FullComputation {
        @Test
        fun `disconnected returns null`() {
            val result = PostureScoreEngine.compute(
                encryptionType = "WPA3",
                isConnected = false,
                deviceCount = 3,
                vpnState = VpnState.FullTunnel,
                untrustedNetwork = true,
            )
            assertNull(result)
        }

        @Test
        fun `best case WPA3 with VPN and few devices grades A`() {
            val result = PostureScoreEngine.compute(
                encryptionType = "WPA3",
                isConnected = true,
                deviceCount = 3,
                vpnState = VpnState.FullTunnel,
                untrustedNetwork = true,
            )
            assertEquals("A", result?.grade)
            assertEquals(3, result?.factors?.size)
        }

        @Test
        fun `WPA2 no VPN moderate devices grades C or D`() {
            val result = PostureScoreEngine.compute(
                encryptionType = "WPA2",
                isConnected = true,
                deviceCount = 20,
                vpnState = VpnState.None,
                untrustedNetwork = true,
            )
            assertTrue(result?.grade in listOf("C", "D"))
        }

        @Test
        fun `open network no VPN many devices grades F`() {
            val result = PostureScoreEngine.compute(
                encryptionType = "",
                isConnected = true,
                deviceCount = 50,
                vpnState = VpnState.None,
                untrustedNetwork = true,
            )
            assertEquals("F", result?.grade)
        }

        @Test
        fun `null device count excludes factor from calculation`() {
            val result = PostureScoreEngine.compute(
                encryptionType = "WPA3",
                isConnected = true,
                deviceCount = null,
                vpnState = VpnState.FullTunnel,
                untrustedNetwork = true,
            )
            assertEquals(2, result?.factors?.size)
            assertEquals("A", result?.grade)
        }

        @Test
        fun `score is clamped to 0 to 100`() {
            val result = PostureScoreEngine.compute(
                encryptionType = "WPA3",
                isConnected = true,
                deviceCount = 1,
                vpnState = VpnState.FullTunnel,
                untrustedNetwork = true,
            )
            assertTrue(result?.numericScore in 0..100)
        }

        @Test
        fun `null encryption on cellular excludes encryption factor`() {
            val result = PostureScoreEngine.compute(
                encryptionType = null,
                isConnected = true,
                deviceCount = 3,
                vpnState = VpnState.FullTunnel,
                untrustedNetwork = true,
            )
            val encFactor = result?.factors?.first { it.factor.displayName == "Wi-Fi Encryption" }
            assertEquals(Severity.Unavailable, encFactor?.severity)
        }
    }
}
