package com.ventouxlabs.netlens.core.scan.engine

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SubnetScannerImplTest {

    @Test
    fun `calculateIpRange returns correct range for slash 24 subnet`() {
        val range = SubnetScannerImpl.calculateIpRange("192.168.1.0", 24)
        assertEquals(254, range.size)
        assertEquals("192.168.1.1", range.first())
        assertEquals("192.168.1.254", range.last())
    }

    @Test
    fun `calculateIpRange returns empty for invalid subnet`() {
        val range = SubnetScannerImpl.calculateIpRange("invalid", 24)
        assertTrue(range.isEmpty())
    }

    @Test
    fun `calculateIpRange returns empty when host count exceeds MAX_HOSTS`() {
        val range = SubnetScannerImpl.calculateIpRange("10.0.0.0", 8)
        assertTrue(range.isEmpty())
    }

    @Test
    fun `calculateIpRange handles slash 22 within limit`() {
        val range = SubnetScannerImpl.calculateIpRange("192.168.0.0", 22)
        assertEquals(1022, range.size)
        assertEquals("192.168.0.1", range.first())
    }

    @Test
    fun `calculateIpRange returns empty for zero prefix length`() {
        val range = SubnetScannerImpl.calculateIpRange("0.0.0.0", 0)
        assertTrue(range.isEmpty())
    }

    @Test
    fun `calculateIpRange handles non-zero network address`() {
        val range = SubnetScannerImpl.calculateIpRange("10.0.1.0", 24)
        assertEquals(254, range.size)
        assertEquals("10.0.1.1", range.first())
        assertEquals("10.0.1.254", range.last())
    }

    // A PTR record is shaped by whoever answers reverse DNS on this network — flatten at
    // ingestion so every sink inherits it.
    @Test
    fun `sanitizeResolvedHostname flattens a hostile reverse-dns result`() {
        val name = SubnetScannerImpl.sanitizeResolvedHostname("10.0.0.9", "nas)\n10.0.0.1 (Router)")

        assertEquals("nas) 10.0.0.1 (Router)", name)
    }

    @Test
    fun `sanitizeResolvedHostname drops a resolution that merely echoes the ip`() {
        assertNull(SubnetScannerImpl.sanitizeResolvedHostname("10.0.0.9", "10.0.0.9"))
    }

    @Test
    fun `sanitizeResolvedHostname keeps an ordinary hostname`() {
        assertEquals("printer.lan", SubnetScannerImpl.sanitizeResolvedHostname("10.0.0.9", "printer.lan"))
    }
}
