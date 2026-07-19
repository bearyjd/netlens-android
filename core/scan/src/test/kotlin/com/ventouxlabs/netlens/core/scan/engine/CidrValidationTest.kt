package com.ventouxlabs.netlens.core.scan.engine

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

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
}
