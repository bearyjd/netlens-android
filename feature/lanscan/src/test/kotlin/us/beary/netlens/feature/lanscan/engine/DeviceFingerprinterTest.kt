package us.beary.netlens.feature.lanscan.engine

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import us.beary.netlens.feature.lanscan.model.LanDevice

class DeviceFingerprinterTest {

    private val fp = DeviceFingerprinter()

    private fun device(hostname: String? = null) = LanDevice(
        ip = "192.168.1.100",
        hostname = hostname,
        isReachable = true,
        latencyMs = 0,
    )

    @Test
    fun `hostname with gateway classified as Router`() {
        val result = fp.fingerprint(device(hostname = "my-gateway"))
        assertEquals("Router", result.deviceType)
    }

    @Test
    fun `hostname with router classified as Router`() {
        val result = fp.fingerprint(device(hostname = "router.asus.com"))
        assertEquals("Router", result.deviceType)
    }

    @Test
    fun `hostname with printer classified as Printer`() {
        val result = fp.fingerprint(device(hostname = "office-printer"))
        assertEquals("Printer", result.deviceType)
    }

    @Test
    fun `hostname with tv classified as Smart TV`() {
        val result = fp.fingerprint(device(hostname = "Samsung-TV"))
        assertEquals("Smart TV", result.deviceType)
    }

    @Test
    fun `hostname with appletv classified as Smart TV`() {
        val result = fp.fingerprint(device(hostname = "appletv-living"))
        assertEquals("Smart TV", result.deviceType)
    }

    @Test
    fun `iphone hostname classified as Phone`() {
        val result = fp.fingerprint(device(hostname = "Johns-iPhone"))
        assertEquals("Phone", result.deviceType)
    }

    @Test
    fun `ipad hostname classified as Tablet`() {
        val result = fp.fingerprint(device(hostname = "Johns-iPad"))
        assertEquals("Tablet", result.deviceType)
    }

    @Test
    fun `macbook hostname classified as Computer`() {
        val result = fp.fingerprint(device(hostname = "Johns-MacBook-Pro"))
        assertEquals("Computer", result.deviceType)
    }

    @Test
    fun `android hostname classified as Phone`() {
        val result = fp.fingerprint(device(hostname = "android-abc123"))
        assertEquals("Phone", result.deviceType)
    }

    @Test
    fun `iot hostname classified as IoT`() {
        val result = fp.fingerprint(device(hostname = "iot-sensor-01"))
        assertEquals("IoT", result.deviceType)
    }

    @Test
    fun `xbox hostname classified as Game Console`() {
        val result = fp.fingerprint(device(hostname = "xbox-living-room"))
        assertEquals("Game Console", result.deviceType)
    }

    @Test
    fun `desktop hostname classified as Computer`() {
        val result = fp.fingerprint(device(hostname = "DESKTOP-ABC123"))
        assertEquals("Computer", result.deviceType)
    }

    @Test
    fun `null hostname returns null deviceType`() {
        val result = fp.fingerprint(device())
        assertNull(result.deviceType)
    }

    @Test
    fun `unknown hostname returns null deviceType`() {
        val result = fp.fingerprint(device(hostname = "generic-host"))
        assertNull(result.deviceType)
    }

    @Test
    fun `iphone hostname guesses iOS`() {
        val result = fp.fingerprint(device(hostname = "Johns-iPhone"))
        assertEquals("iOS", result.osGuess)
    }

    @Test
    fun `ipad hostname guesses iOS`() {
        val result = fp.fingerprint(device(hostname = "Johns-iPad"))
        assertEquals("iOS", result.osGuess)
    }

    @Test
    fun `macbook hostname guesses macOS`() {
        val result = fp.fingerprint(device(hostname = "Johns-MacBook-Pro"))
        assertEquals("macOS", result.osGuess)
    }

    @Test
    fun `android hostname guesses Android`() {
        val result = fp.fingerprint(device(hostname = "android-abc123"))
        assertEquals("Android", result.osGuess)
    }

    @Test
    fun `windows hostname guesses Windows`() {
        val result = fp.fingerprint(device(hostname = "DESKTOP-ABC123"))
        assertEquals("Windows", result.osGuess)
    }

    @Test
    fun `raspberry hostname guesses Linux`() {
        val result = fp.fingerprint(device(hostname = "raspberrypi-4"))
        assertEquals("Linux", result.osGuess)
    }

    @Test
    fun `unknown hostname returns null osGuess`() {
        val result = fp.fingerprint(device(hostname = "generic-host"))
        assertNull(result.osGuess)
    }
}
