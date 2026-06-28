package com.ventouxlabs.netlens.feature.lanscan.engine

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ArpTableReaderTest {

    @Test
    fun `parse valid ARP table`() {
        val lines = listOf(
            "IP address       HW type     Flags       HW address            Mask     Device",
            "192.168.1.1      0x1         0x2         AA:BB:CC:DD:EE:FF     *        wlan0",
            "192.168.1.50     0x1         0x2         11:22:33:44:55:66     *        wlan0",
        )
        val result = ArpTableReaderImpl.parseArpTable(lines)
        assertEquals(2, result.size)
        assertEquals("AA:BB:CC:DD:EE:FF", result["192.168.1.1"])
        assertEquals("11:22:33:44:55:66", result["192.168.1.50"])
    }

    @Test
    fun `skip incomplete ARP entries`() {
        val lines = listOf(
            "IP address       HW type     Flags       HW address            Mask     Device",
            "192.168.1.2      0x1         0x0         00:00:00:00:00:00     *        wlan0",
        )
        val result = ArpTableReaderImpl.parseArpTable(lines)
        assertTrue(result.isEmpty())
    }

    @Test
    fun `parse empty ARP table`() {
        val lines = listOf(
            "IP address       HW type     Flags       HW address            Mask     Device",
        )
        val result = ArpTableReaderImpl.parseArpTable(lines)
        assertTrue(result.isEmpty())
    }

    @Test
    fun `skip malformed lines`() {
        val lines = listOf(
            "IP address       HW type     Flags       HW address            Mask     Device",
            "garbage",
            "",
            "192.168.1.1      0x1         0x2         AA:BB:CC:DD:EE:FF     *        wlan0",
        )
        val result = ArpTableReaderImpl.parseArpTable(lines)
        assertEquals(1, result.size)
        assertEquals("AA:BB:CC:DD:EE:FF", result["192.168.1.1"])
    }

    @Test
    fun `MAC addresses are uppercased`() {
        val lines = listOf(
            "IP address       HW type     Flags       HW address            Mask     Device",
            "192.168.1.1      0x1         0x2         aa:bb:cc:dd:ee:ff     *        wlan0",
        )
        val result = ArpTableReaderImpl.parseArpTable(lines)
        assertEquals("AA:BB:CC:DD:EE:FF", result["192.168.1.1"])
    }
}
