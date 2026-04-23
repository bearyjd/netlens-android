package us.beary.netlens.feature.lanscan.engine

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import us.beary.netlens.feature.lanscan.model.LanDevice

class DeviceFingerprinterTest {

    private val fp = DeviceFingerprinter()

    private fun device(
        vendor: String? = null,
        hostname: String? = null,
    ) = LanDevice(
        ip = "192.168.1.100",
        mac = "AA:BB:CC:DD:EE:FF",
        vendor = vendor,
        hostname = hostname,
        isReachable = true,
        latencyMs = 0,
    )

    @Test
    fun `cisco vendor classified as Router`() {
        val result = fp.fingerprint(device(vendor = "Cisco Systems"))
        assertEquals("Router", result.deviceType)
    }

    @Test
    fun `hostname with gateway classified as Router`() {
        val result = fp.fingerprint(device(hostname = "my-gateway"))
        assertEquals("Router", result.deviceType)
    }

    @Test
    fun `hp vendor classified as Printer`() {
        val result = fp.fingerprint(device(vendor = "Hewlett Packard"))
        assertEquals("Printer", result.deviceType)
    }

    @Test
    fun `epson vendor classified as Printer`() {
        val result = fp.fingerprint(device(vendor = "Epson"))
        assertEquals("Printer", result.deviceType)
    }

    @Test
    fun `roku vendor classified as Smart TV`() {
        val result = fp.fingerprint(device(vendor = "Roku"))
        assertEquals("Smart TV", result.deviceType)
    }

    @Test
    fun `samsung with tv hostname classified as Smart TV`() {
        val result = fp.fingerprint(device(vendor = "Samsung Electronics", hostname = "Samsung-TV"))
        assertEquals("Smart TV", result.deviceType)
    }

    @Test
    fun `apple iphone classified as Phone`() {
        val result = fp.fingerprint(device(vendor = "Apple", hostname = "Johns-iPhone"))
        assertEquals("Phone", result.deviceType)
    }

    @Test
    fun `apple macbook classified as Computer`() {
        val result = fp.fingerprint(device(vendor = "Apple", hostname = "Johns-MacBook-Pro"))
        assertEquals("Computer", result.deviceType)
    }

    @Test
    fun `apple generic classified as Apple Device`() {
        val result = fp.fingerprint(device(vendor = "Apple", hostname = "unknown-host"))
        assertEquals("Apple Device", result.deviceType)
    }

    @Test
    fun `xiaomi vendor classified as Phone`() {
        val result = fp.fingerprint(device(vendor = "Xiaomi Communications"))
        assertEquals("Phone", result.deviceType)
    }

    @Test
    fun `espressif vendor classified as IoT`() {
        val result = fp.fingerprint(device(vendor = "Espressif"))
        assertEquals("IoT", result.deviceType)
    }

    @Test
    fun `nintendo vendor classified as Game Console`() {
        val result = fp.fingerprint(device(vendor = "Nintendo"))
        assertEquals("Game Console", result.deviceType)
    }

    @Test
    fun `dell vendor classified as Computer`() {
        val result = fp.fingerprint(device(vendor = "Dell"))
        assertEquals("Computer", result.deviceType)
    }

    @Test
    fun `asus without router hostname classified as Computer not Router`() {
        val result = fp.fingerprint(device(vendor = "ASUSTek Computer", hostname = "DESKTOP-ABC"))
        assertEquals("Computer", result.deviceType)
    }

    @Test
    fun `asus with router hostname classified as Router`() {
        val result = fp.fingerprint(device(vendor = "ASUSTek Computer", hostname = "router.asus.com"))
        assertEquals("Router", result.deviceType)
    }

    @Test
    fun `unknown vendor returns null deviceType`() {
        val result = fp.fingerprint(device(vendor = "Unknown Corp", hostname = "some-host"))
        assertNull(result.deviceType)
    }

    @Test
    fun `null vendor and hostname returns null deviceType`() {
        val result = fp.fingerprint(device())
        assertNull(result.deviceType)
    }

    @Test
    fun `apple iphone guesses iOS`() {
        val result = fp.fingerprint(device(vendor = "Apple", hostname = "Johns-iPhone"))
        assertEquals("iOS", result.osGuess)
    }

    @Test
    fun `apple vendor without phone hostname guesses macOS`() {
        val result = fp.fingerprint(device(vendor = "Apple", hostname = "some-mac"))
        assertEquals("macOS", result.osGuess)
    }

    @Test
    fun `android hostname guesses Android`() {
        val result = fp.fingerprint(device(hostname = "android-abc123"))
        assertEquals("Android", result.osGuess)
    }

    @Test
    fun `samsung without tv hostname guesses Android`() {
        val result = fp.fingerprint(device(vendor = "Samsung Electronics", hostname = "Galaxy-S24"))
        assertEquals("Android", result.osGuess)
    }

    @Test
    fun `samsung tv does not guess Android`() {
        val result = fp.fingerprint(device(vendor = "Samsung Electronics", hostname = "Samsung-TV"))
        assertNull(result.osGuess)
    }

    @Test
    fun `windows hostname guesses Windows`() {
        val result = fp.fingerprint(device(hostname = "DESKTOP-ABC123"))
        assertEquals("Windows", result.osGuess)
    }

    @Test
    fun `raspberry hostname guesses Linux`() {
        val result = fp.fingerprint(device(hostname = "raspberrypi"))
        assertEquals("Linux", result.osGuess)
    }

    @Test
    fun `unknown vendor and hostname returns null osGuess`() {
        val result = fp.fingerprint(device(vendor = "Unknown Corp", hostname = "generic-host"))
        assertNull(result.osGuess)
    }
}
