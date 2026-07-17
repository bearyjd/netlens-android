package com.ventouxlabs.netlens.core.scan.engine

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import com.ventouxlabs.netlens.core.scan.model.SsdpDevice

class SsdpScannerTest {

    @Test
    fun `parse LOCATION from valid response`() {
        val response = buildString {
            appendLine("HTTP/1.1 200 OK")
            appendLine("CACHE-CONTROL: max-age=1800")
            appendLine("LOCATION: http://192.168.1.100:49152/desc.xml")
            appendLine("SERVER: Linux UPnP/1.0")
            appendLine("ST: ssdp:all")
        }
        val location = SsdpScannerImpl.parseLocation(response)
        assertEquals("http://192.168.1.100:49152/desc.xml", location)
    }

    @Test
    fun `parse LOCATION case insensitive`() {
        val response = "location: http://10.0.0.1:80/rootDesc.xml\r\n"
        val location = SsdpScannerImpl.parseLocation(response)
        assertEquals("http://10.0.0.1:80/rootDesc.xml", location)
    }

    @Test
    fun `parse LOCATION returns null when missing`() {
        val response = "HTTP/1.1 200 OK\r\nST: ssdp:all\r\n"
        val location = SsdpScannerImpl.parseLocation(response)
        assertNull(location)
    }

    @Test
    fun `parse device XML extracts all fields`() {
        val xml = """
            <?xml version="1.0"?>
            <root>
                <device>
                    <friendlyName>Living Room TV</friendlyName>
                    <manufacturer>Samsung</manufacturer>
                    <modelName>UN55KS8000</modelName>
                    <deviceType>urn:schemas-upnp-org:device:MediaRenderer:1</deviceType>
                </device>
            </root>
        """.trimIndent()
        val device = SsdpScannerImpl.parseDeviceXml("192.168.1.100", xml)
        assertEquals("192.168.1.100", device.ip)
        assertEquals("Living Room TV", device.friendlyName)
        assertEquals("Samsung", device.manufacturer)
        assertEquals("UN55KS8000", device.modelName)
        assertEquals("urn:schemas-upnp-org:device:MediaRenderer:1", device.deviceType)
    }

    @Test
    fun `parse device XML handles missing tags`() {
        val xml = """
            <?xml version="1.0"?>
            <root>
                <device>
                    <friendlyName>Router</friendlyName>
                </device>
            </root>
        """.trimIndent()
        val device = SsdpScannerImpl.parseDeviceXml("10.0.0.1", xml)
        assertEquals("10.0.0.1", device.ip)
        assertEquals("Router", device.friendlyName)
        assertNull(device.manufacturer)
        assertNull(device.modelName)
        assertNull(device.deviceType)
    }

    @Test
    fun `parse device XML handles empty XML`() {
        val device = SsdpScannerImpl.parseDeviceXml("10.0.0.1", "")
        assertEquals("10.0.0.1", device.ip)
        assertNull(device.friendlyName)
    }

    @Test
    fun `readCapped returns full content when below cap`() {
        val content = "<root><friendlyName>Router</friendlyName></root>"
        val result = SsdpScannerImpl.readCapped(content.reader().buffered(), 1024)
        assertEquals(content, result)
    }

    @Test
    fun `readCapped truncates content that exceeds cap`() {
        val content = "a".repeat(5000)
        val result = SsdpScannerImpl.readCapped(content.reader().buffered(), 1000)
        assertEquals(1000, result.length)
    }
}
