package com.ventoux.netlens.feature.lanscan.engine

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import com.ventoux.netlens.feature.lanscan.model.LanDevice
import com.ventoux.netlens.feature.lanscan.model.NetBiosInfo
import com.ventoux.netlens.feature.lanscan.model.SsdpDevice

class DeviceFingerprinterTest {

    private val fakeOui = FakeOuiLookup()
    private val fp = DeviceFingerprinterImpl(fakeOui)

    private fun device(hostname: String? = null, mac: String? = null) = LanDevice(
        ip = "192.168.1.100",
        hostname = hostname,
        isReachable = true,
        latencyMs = 0,
        macAddress = mac,
    )

    // --- hostname-based fingerprint tests ---

    @Test
    fun `hostname with gateway classified as Router`() = runTest {
        val result = fp.fingerprint(device(hostname = "my-gateway"))
        assertEquals("Router", result.deviceType)
    }

    @Test
    fun `hostname with router classified as Router`() = runTest {
        val result = fp.fingerprint(device(hostname = "router.asus.com"))
        assertEquals("Router", result.deviceType)
    }

    @Test
    fun `hostname with printer classified as Printer`() = runTest {
        val result = fp.fingerprint(device(hostname = "office-printer"))
        assertEquals("Printer", result.deviceType)
    }

    @Test
    fun `hostname with tv classified as Smart TV`() = runTest {
        val result = fp.fingerprint(device(hostname = "Samsung-TV"))
        assertEquals("Smart TV", result.deviceType)
    }

    @Test
    fun `hostname with appletv classified as Smart TV`() = runTest {
        val result = fp.fingerprint(device(hostname = "appletv-living"))
        assertEquals("Smart TV", result.deviceType)
    }

    @Test
    fun `iphone hostname classified as Phone`() = runTest {
        val result = fp.fingerprint(device(hostname = "Johns-iPhone"))
        assertEquals("Phone", result.deviceType)
    }

    @Test
    fun `ipad hostname classified as Tablet`() = runTest {
        val result = fp.fingerprint(device(hostname = "Johns-iPad"))
        assertEquals("Tablet", result.deviceType)
    }

    @Test
    fun `macbook hostname classified as Computer`() = runTest {
        val result = fp.fingerprint(device(hostname = "Johns-MacBook-Pro"))
        assertEquals("Computer", result.deviceType)
    }

    @Test
    fun `android hostname classified as Phone`() = runTest {
        val result = fp.fingerprint(device(hostname = "android-abc123"))
        assertEquals("Phone", result.deviceType)
    }

    @Test
    fun `iot hostname classified as IoT`() = runTest {
        val result = fp.fingerprint(device(hostname = "iot-sensor-01"))
        assertEquals("IoT", result.deviceType)
    }

    @Test
    fun `xbox hostname classified as Game Console`() = runTest {
        val result = fp.fingerprint(device(hostname = "xbox-living-room"))
        assertEquals("Game Console", result.deviceType)
    }

    @Test
    fun `desktop hostname classified as Computer`() = runTest {
        val result = fp.fingerprint(device(hostname = "DESKTOP-ABC123"))
        assertEquals("Computer", result.deviceType)
    }

    @Test
    fun `null hostname returns null deviceType`() = runTest {
        val result = fp.fingerprint(device())
        assertNull(result.deviceType)
    }

    @Test
    fun `unknown hostname returns null deviceType`() = runTest {
        val result = fp.fingerprint(device(hostname = "generic-host"))
        assertNull(result.deviceType)
    }

    // --- OS guessing ---

    @Test
    fun `iphone hostname guesses iOS`() = runTest {
        val result = fp.fingerprint(device(hostname = "Johns-iPhone"))
        assertEquals("iOS", result.osGuess)
    }

    @Test
    fun `ipad hostname guesses iOS`() = runTest {
        val result = fp.fingerprint(device(hostname = "Johns-iPad"))
        assertEquals("iOS", result.osGuess)
    }

    @Test
    fun `macbook hostname guesses macOS`() = runTest {
        val result = fp.fingerprint(device(hostname = "Johns-MacBook-Pro"))
        assertEquals("macOS", result.osGuess)
    }

    @Test
    fun `android hostname guesses Android`() = runTest {
        val result = fp.fingerprint(device(hostname = "android-abc123"))
        assertEquals("Android", result.osGuess)
    }

    @Test
    fun `windows hostname guesses Windows`() = runTest {
        val result = fp.fingerprint(device(hostname = "DESKTOP-ABC123"))
        assertEquals("Windows", result.osGuess)
    }

    @Test
    fun `raspberry hostname guesses Linux`() = runTest {
        val result = fp.fingerprint(device(hostname = "raspberrypi-4"))
        assertEquals("Linux", result.osGuess)
    }

    @Test
    fun `unknown hostname returns null osGuess`() = runTest {
        val result = fp.fingerprint(device(hostname = "generic-host"))
        assertNull(result.osGuess)
    }

    // --- mDNS service-type classification ---

    @Test
    fun `airplay service classified as Smart TV`() {
        val (type, os) = fp.classifyFromServices(listOf("_airplay._tcp"))
        assertEquals("Smart TV", type)
        assertEquals("iOS", os)
    }

    @Test
    fun `googlecast service classified as Chromecast`() {
        val (type, _) = fp.classifyFromServices(listOf("_googlecast._tcp"))
        assertEquals("Chromecast", type)
    }

    @Test
    fun `raop service classified as AirPlay Speaker`() {
        val (type, _) = fp.classifyFromServices(listOf("_raop._tcp"))
        assertEquals("AirPlay Speaker", type)
    }

    @Test
    fun `ipp service classified as Printer`() {
        val (type, _) = fp.classifyFromServices(listOf("_ipp._tcp"))
        assertEquals("Printer", type)
    }

    @Test
    fun `homekit service classified as IoT`() {
        val (type, _) = fp.classifyFromServices(listOf("_homekit._tcp"))
        assertEquals("IoT", type)
    }

    @Test
    fun `companion-link service classified as iOS Phone`() {
        val (type, os) = fp.classifyFromServices(listOf("_companion-link._tcp"))
        assertEquals("Phone", type)
        assertEquals("iOS", os)
    }

    @Test
    fun `service type takes priority over hostname`() = runTest {
        val dev = device(hostname = "generic-host").copy(services = listOf("_googlecast._tcp"))
        val result = fp.fingerprint(dev)
        assertEquals("Chromecast", result.deviceType)
    }

    @Test
    fun `unknown services return null`() {
        val (type, os) = fp.classifyFromServices(listOf("_http._tcp"))
        assertNull(type)
        assertNull(os)
    }

    // --- SSDP classification ---

    @Test
    fun `SSDP MediaRenderer classified as Smart TV`() {
        val ssdp = SsdpDevice(
            ip = "192.168.1.100",
            deviceType = "urn:schemas-upnp-org:device:MediaRenderer:1",
        )
        val (type, _) = fp.classifyFromSsdp(ssdp)
        assertEquals("Smart TV", type)
    }

    @Test
    fun `SSDP InternetGateway classified as Router`() {
        val ssdp = SsdpDevice(
            ip = "192.168.1.1",
            deviceType = "urn:schemas-upnp-org:device:InternetGatewayDevice:1",
        )
        val (type, _) = fp.classifyFromSsdp(ssdp)
        assertEquals("Router", type)
    }

    @Test
    fun `SSDP Samsung manufacturer with TV type guesses Tizen`() {
        val ssdp = SsdpDevice(
            ip = "192.168.1.100",
            manufacturer = "Samsung Electronics",
            deviceType = "urn:schemas-upnp-org:device:tv:1",
        )
        val (type, os) = fp.classifyFromSsdp(ssdp)
        assertEquals("Smart TV", type)
        assertEquals("Tizen", os)
    }

    @Test
    fun `SSDP Chromecast in friendly name classified as Smart Speaker`() {
        val ssdp = SsdpDevice(
            ip = "192.168.1.50",
            friendlyName = "Chromecast Audio",
            manufacturer = "Google Inc.",
        )
        val (type, _) = fp.classifyFromSsdp(ssdp)
        assertEquals("Smart Speaker", type)
    }

    // --- NetBIOS classification ---

    @Test
    fun `NetBIOS with Windows hostname pattern classifies as Windows`() {
        val result = fp.classifyFromNetBios(NetBiosInfo(name = "DESKTOP-ABC"))
        assertEquals("Windows", result)
    }

    @Test
    fun `NetBIOS with WORKGROUP classifies as Windows`() {
        val result = fp.classifyFromNetBios(NetBiosInfo(name = "SERVER01", workgroup = "WORKGROUP"))
        assertEquals("Windows", result)
    }

    @Test
    fun `NetBIOS with unknown name returns null`() {
        val result = fp.classifyFromNetBios(NetBiosInfo(name = "nas-server"))
        assertNull(result)
    }

    // --- OUI vendor lookup ---

    @Test
    fun `fingerprint with MAC sets vendor from OUI`() = runTest {
        fakeOui.table["AA:BB:CC"] = "Apple Inc."
        val result = fp.fingerprint(device(mac = "AA:BB:CC:DD:EE:FF"))
        assertEquals("Apple Inc.", result.vendor)
    }

    @Test
    fun `fingerprint without MAC has null vendor`() = runTest {
        val result = fp.fingerprint(device())
        assertNull(result.vendor)
    }

    @Test
    fun `fingerprint with unknown MAC prefix has null vendor`() = runTest {
        val result = fp.fingerprint(device(mac = "XX:YY:ZZ:11:22:33"))
        assertNull(result.vendor)
    }

    // --- fingerprintWithPorts tests ---

    @Test
    fun `port 631 detects Printer`() {
        val result = fp.fingerprintWithPorts(device(), listOf(631))
        assertEquals("Printer", result.deviceType)
        assertTrue(result.evidence.any { "631" in it })
    }

    @Test
    fun `port 9100 detects Printer`() {
        val result = fp.fingerprintWithPorts(device(), listOf(9100))
        assertEquals("Printer", result.deviceType)
        assertTrue(result.evidence.any { "9100" in it })
    }

    @Test
    fun `port 3389 detects Windows`() {
        val result = fp.fingerprintWithPorts(device(), listOf(3389))
        assertEquals("Windows", result.osGuess)
        assertTrue(result.evidence.any { "3389" in it })
    }

    @Test
    fun `port 548 detects macOS`() {
        val result = fp.fingerprintWithPorts(device(), listOf(548))
        assertEquals("macOS", result.osGuess)
        assertTrue(result.evidence.any { "548" in it })
    }

    @Test
    fun `ports 22 and 53 detect Router`() {
        val result = fp.fingerprintWithPorts(device(), listOf(22, 53))
        assertEquals("Router", result.deviceType)
        assertTrue(result.evidence.any { "22+53" in it })
    }

    @Test
    fun `web ports detect Web Server when no prior type`() {
        val result = fp.fingerprintWithPorts(device(), listOf(80))
        assertEquals("Web Server", result.deviceType)
        assertTrue(result.evidence.any { "web" in it.lowercase() })
    }

    @Test
    fun `web ports do not override existing device type`() {
        val printerDevice = device().copy(deviceType = "Printer")
        val result = fp.fingerprintWithPorts(printerDevice, listOf(80, 443))
        assertEquals("Printer", result.deviceType)
    }

    @Test
    fun `existing type preserved over port-based detection`() {
        val routerDevice = device().copy(deviceType = "Router")
        val result = fp.fingerprintWithPorts(routerDevice, listOf(631))
        assertEquals("Router", result.deviceType)
        assertTrue(result.evidence.any { "631" in it })
    }

    @Test
    fun `existing OS preserved over port-based detection`() {
        val linuxDevice = device().copy(osGuess = "Linux")
        val result = fp.fingerprintWithPorts(linuxDevice, listOf(3389))
        assertEquals("Linux", result.osGuess)
        assertTrue(result.evidence.any { "3389" in it })
    }

    @Test
    fun `hostname added to evidence`() {
        val result = fp.fingerprintWithPorts(device(hostname = "my-host"), emptyList())
        assertTrue(result.evidence.any { "hostname: my-host" in it })
    }

    @Test
    fun `mDNS services added to evidence`() {
        val dev = device().copy(services = listOf("_http._tcp."))
        val result = fp.fingerprintWithPorts(dev, emptyList())
        assertTrue(result.evidence.any { "mDNS: http" in it })
    }

    @Test
    fun `vendor added to evidence`() {
        val dev = device().copy(vendor = "ASUS")
        val result = fp.fingerprintWithPorts(dev, emptyList())
        assertTrue(result.evidence.any { "vendor: ASUS" in it })
    }

    @Test
    fun `empty ports and no hostname returns no type or OS`() {
        val result = fp.fingerprintWithPorts(device(), emptyList())
        assertNull(result.deviceType)
        assertNull(result.osGuess)
        assertTrue(result.evidence.isEmpty())
    }
}
