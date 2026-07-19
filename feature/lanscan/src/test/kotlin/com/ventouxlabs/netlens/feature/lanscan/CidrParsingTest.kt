package com.ventouxlabs.netlens.feature.lanscan

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class CidrParsingTest {

    @Test
    fun `parseCidr accepts valid cidr`() {
        val result = LanScanViewModel.parseCidr("192.168.1.0/24")
        assertEquals("192.168.1.0" to 24, result)
    }

    @Test
    fun `parseCidr trims whitespace`() {
        val result = LanScanViewModel.parseCidr("  192.168.1.0/24  ")
        assertEquals("192.168.1.0" to 24, result)
    }

    @Test
    fun `parseCidr rejects missing slash`() {
        assertNull(LanScanViewModel.parseCidr("192.168.1.0"))
    }

    @Test
    fun `parseCidr rejects prefix greater than 30`() {
        assertNull(LanScanViewModel.parseCidr("192.168.1.0/31"))
    }

    @Test
    fun `parseCidr rejects prefix less than 16`() {
        assertNull(LanScanViewModel.parseCidr("10.0.0.0/15"))
    }

    @Test
    fun `parseCidr rejects octet greater than 255`() {
        assertNull(LanScanViewModel.parseCidr("192.168.1.256/24"))
    }

    @Test
    fun `parseCidr rejects non-numeric octet`() {
        assertNull(LanScanViewModel.parseCidr("192.168.abc.1/24"))
    }

    @Test
    fun `parseCidr rejects empty string`() {
        assertNull(LanScanViewModel.parseCidr(""))
    }

    @Test
    fun `parseCidr rejects negative prefix`() {
        assertNull(LanScanViewModel.parseCidr("192.168.1.0/-1"))
    }

    @Test
    fun `parseCidr accepts boundary prefix 16`() {
        val result = LanScanViewModel.parseCidr("172.16.0.0/16")
        assertEquals("172.16.0.0" to 16, result)
    }

    @Test
    fun `parseCidr accepts boundary prefix 30`() {
        val result = LanScanViewModel.parseCidr("192.168.1.0/30")
        assertEquals("192.168.1.0" to 30, result)
    }
}
