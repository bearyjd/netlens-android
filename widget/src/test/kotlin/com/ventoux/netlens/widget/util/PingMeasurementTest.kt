package com.ventoux.netlens.widget.util

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class PingMeasurementTest {

    @BeforeEach
    fun setUp() {
        PingMeasurement.clear()
    }

    @Test
    fun `parsePingOutput extracts integer ms from standard ping output`() {
        val output = """
            PING 8.8.8.8 (8.8.8.8) 56(84) bytes of data.
            64 bytes from 8.8.8.8: icmp_seq=1 ttl=117 time=12.3 ms

            --- 8.8.8.8 ping statistics ---
            1 packets transmitted, 1 received, 0% packet loss, time 0ms
        """.trimIndent()
        assertEquals(12, PingMeasurement.parsePingOutput(output))
    }

    @Test
    fun `parsePingOutput returns null for no match`() {
        assertNull(PingMeasurement.parsePingOutput("Request timed out"))
    }

    @Test
    fun `parsePingOutput handles whole number time`() {
        val output = "64 bytes from 8.8.8.8: icmp_seq=1 ttl=117 time=5 ms"
        assertEquals(5, PingMeasurement.parsePingOutput(output))
    }

    @Test
    fun `parsePingOutput handles sub-millisecond time`() {
        val output = "64 bytes from 1.1.1.1: icmp_seq=1 ttl=57 time=0.8 ms"
        assertEquals(0, PingMeasurement.parsePingOutput(output))
    }

    @Test
    fun `record and smoothed returns average of recent pings`() {
        PingMeasurement.record(10)
        PingMeasurement.record(20)
        PingMeasurement.record(30)
        assertEquals(20, PingMeasurement.smoothed())
    }

    @Test
    fun `record evicts oldest when exceeding capacity`() {
        PingMeasurement.record(100)
        PingMeasurement.record(10)
        PingMeasurement.record(20)
        PingMeasurement.record(30)
        // oldest (100) evicted, remaining: 10, 20, 30
        assertEquals(20, PingMeasurement.smoothed())
    }

    @Test
    fun `smoothed returns null when empty`() {
        assertNull(PingMeasurement.smoothed())
    }

    @Test
    fun `clear resets state`() {
        PingMeasurement.record(50)
        PingMeasurement.clear()
        assertNull(PingMeasurement.smoothed())
    }
}
