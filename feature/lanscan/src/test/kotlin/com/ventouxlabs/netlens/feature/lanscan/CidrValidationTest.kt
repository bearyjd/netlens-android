package com.ventouxlabs.netlens.feature.lanscan

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import com.ventouxlabs.netlens.core.scan.engine.SubnetScannerImpl

class CidrValidationTest {

    @Test
    fun `valid 24 produces 254 hosts`() {
        val ips = SubnetScannerImpl.calculateIpRange("192.168.1.0", 24)
        assertEquals(254, ips.size)
        assertEquals("192.168.1.1", ips.first())
        assertEquals("192.168.1.254", ips.last())
    }

    @Test
    fun `valid 28 produces 14 hosts`() {
        val ips = SubnetScannerImpl.calculateIpRange("10.0.0.0", 28)
        assertEquals(14, ips.size)
    }

    @Test
    fun `non-dotted-quad returns empty`() {
        val ips = SubnetScannerImpl.calculateIpRange("not-an-ip", 24)
        assertTrue(ips.isEmpty())
    }

    @Test
    fun `prefix 32 returns empty`() {
        val ips = SubnetScannerImpl.calculateIpRange("192.168.1.1", 32)
        assertTrue(ips.isEmpty())
    }

    @Test
    fun `prefix 8 exceeds max hosts returns empty`() {
        val ips = SubnetScannerImpl.calculateIpRange("10.0.0.0", 8)
        assertTrue(ips.isEmpty())
    }

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
