package com.ventoux.netlens.feature.lanscan.engine

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import com.ventoux.netlens.feature.lanscan.model.NetBiosInfo

class NetBiosProberTest {

    @Test
    fun `build query packet has correct length`() {
        val packet = NetBiosProberImpl.buildNameQuery()
        assertEquals(50, packet.size)
    }

    @Test
    fun `build query packet has NBSTAT type`() {
        val packet = NetBiosProberImpl.buildNameQuery()
        // Type NBSTAT = 0x0021 at offset 46-47
        assertEquals(0x00.toByte(), packet[46])
        assertEquals(0x21.toByte(), packet[47])
    }

    @Test
    fun `build query packet has IN class`() {
        val packet = NetBiosProberImpl.buildNameQuery()
        // Class IN = 0x0001 at offset 48-49
        assertEquals(0x00.toByte(), packet[48])
        assertEquals(0x01.toByte(), packet[49])
    }

    @Test
    fun `build query packet has zero flags for NBSTAT`() {
        val packet = NetBiosProberImpl.buildNameQuery()
        assertEquals(0x00.toByte(), packet[2])
        assertEquals(0x00.toByte(), packet[3])
    }

    @Test
    fun `build query packet has one question`() {
        val packet = NetBiosProberImpl.buildNameQuery()
        assertEquals(0x00.toByte(), packet[4])
        assertEquals(0x01.toByte(), packet[5])
    }

    @Test
    fun `parse response returns null for short data`() {
        val data = ByteArray(10)
        val result = NetBiosProberImpl.parseResponse(data, 10)
        assertNull(result)
    }

    @Test
    fun `parse response extracts computer name`() {
        val response = buildNetBiosResponse("DESKTOP-ABC", "WORKGROUP")
        val result = NetBiosProberImpl.parseResponse(response, response.size)
        assertNotNull(result)
        assertEquals("DESKTOP-ABC", result?.name)
        assertEquals("WORKGROUP", result?.workgroup)
    }

    private fun buildNetBiosResponse(computerName: String, workgroup: String): ByteArray {
        val buf = ByteArray(200)
        // Header (12 bytes) — response flags
        buf[0] = 0x00; buf[1] = 0x01 // Transaction ID
        buf[2] = 0x84.toByte(); buf[3] = 0x00 // Flags: response
        buf[4] = 0x00; buf[5] = 0x00 // Questions
        buf[6] = 0x00; buf[7] = 0x01 // Answers

        // Name section — skip with a short encoded name
        var offset = 12
        buf[offset++] = 0x20 // Name length
        for (i in 0 until 32) buf[offset++] = 'A'.code.toByte()
        buf[offset++] = 0x00 // Null terminator
        // Type + Class
        buf[offset++] = 0x00; buf[offset++] = 0x21 // NBSTAT
        buf[offset++] = 0x00; buf[offset++] = 0x01 // IN
        // TTL (4 bytes)
        for (i in 0 until 4) buf[offset++] = 0x00
        // Data length (2 bytes) — placeholder
        buf[offset++] = 0x00; buf[offset++] = 0x00

        // Name count
        buf[offset++] = 0x02

        // Entry 1: computer name (unique, type 0x00)
        val nameBytes = computerName.padEnd(15, ' ').toByteArray()
        System.arraycopy(nameBytes, 0, buf, offset, 15)
        offset += 15
        buf[offset++] = 0x00 // Type: Workstation
        buf[offset++] = 0x04 // Flags high byte (unique - no group bit)
        buf[offset++] = 0x00 // Flags low byte

        // Entry 2: workgroup (group, type 0x00)
        val wgBytes = workgroup.padEnd(15, ' ').toByteArray()
        System.arraycopy(wgBytes, 0, buf, offset, 15)
        offset += 15
        buf[offset++] = 0x00 // Type: Workstation
        buf[offset++] = 0x80.toByte() // Flags high byte (group bit set)
        buf[offset++] = 0x00 // Flags low byte

        return buf.copyOf(offset)
    }
}
