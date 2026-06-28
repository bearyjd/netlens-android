package com.ventouxlabs.netlens.feature.ping.engine

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PingOutputParserTest {

    @Test
    fun `parseReplyLine with standard reply extracts all fields`() {
        val line = "64 bytes from 8.8.8.8: icmp_seq=1 ttl=118 time=12.3 ms"
        val result = PingOutputParser.parseReplyLine(line)
        assertNotNull(result)
        assertEquals(1, result!!.sequenceNumber)
        assertEquals(12.3f, result.latencyMs)
        assertEquals(118, result.ttl)
        // REPLY_REGEX captures trailing colon as part of (\S+) group
        assertEquals("8.8.8.8:", result.ip)
        assertEquals(false, result.isTimeout)
    }

    @Test
    fun `parseReplyLine with integer time`() {
        val line = "64 bytes from 1.1.1.1: icmp_seq=5 ttl=57 time=4 ms"
        val result = PingOutputParser.parseReplyLine(line)
        assertNotNull(result)
        assertEquals(5, result!!.sequenceNumber)
        assertEquals(4f, result.latencyMs)
    }

    @Test
    fun `parseReplyLine with hostname reply`() {
        val line = "64 bytes from dns.google (8.8.8.8): icmp_seq=2 ttl=118 time=10.5 ms"
        val result = PingOutputParser.parseReplyLine(line)
        assertNotNull(result)
        assertEquals(2, result!!.sequenceNumber)
    }

    @Test
    fun `parseReplyLine with destination host unreachable`() {
        val line = "From 192.168.1.1 icmp_seq=2 Destination Host Unreachable"
        val result = PingOutputParser.parseReplyLine(line)
        assertNotNull(result)
        assertEquals(2, result!!.sequenceNumber)
        assertTrue(result.isTimeout)
    }

    @Test
    fun `parseReplyLine with timed out after icmp_seq`() {
        val line = "icmp_seq=3 timed out"
        val result = PingOutputParser.parseReplyLine(line)
        assertNotNull(result)
        assertEquals(3, result!!.sequenceNumber)
        assertTrue(result.isTimeout)
    }

    @Test
    fun `parseReplyLine with no answer after icmp_seq`() {
        val line = "icmp_seq=4 no answer"
        val result = PingOutputParser.parseReplyLine(line)
        assertNotNull(result)
        assertEquals(4, result!!.sequenceNumber)
        assertTrue(result.isTimeout)
    }

    @Test
    fun `parseReplyLine with reversed timeout format returns null`() {
        assertNull(PingOutputParser.parseReplyLine("Request timed out for icmp_seq=3"))
    }

    @Test
    fun `parseReplyLine with irrelevant line returns null`() {
        assertNull(PingOutputParser.parseReplyLine("PING 8.8.8.8 (8.8.8.8) 56(84) bytes of data."))
        assertNull(PingOutputParser.parseReplyLine(""))
        assertNull(PingOutputParser.parseReplyLine("--- 8.8.8.8 ping statistics ---"))
    }

    @Test
    fun `parseSummary with full stats`() {
        val lines = listOf(
            "--- 8.8.8.8 ping statistics ---",
            "4 packets transmitted, 4 received, 0% packet loss, time 3003ms",
            "rtt min/avg/max/mdev = 10.1/12.3/15.2/1.8 ms",
        )
        val summary = PingOutputParser.parseSummary(lines)
        assertNotNull(summary)
        assertEquals(4, summary!!.transmitted)
        assertEquals(4, summary.received)
        assertEquals(0f, summary.lossPercent)
        assertEquals(10.1f, summary.minMs)
        assertEquals(12.3f, summary.avgMs)
        assertEquals(15.2f, summary.maxMs)
    }

    @Test
    fun `parseSummary with partial loss`() {
        val lines = listOf(
            "3 packets transmitted, 1 received, 66.7% packet loss, time 2002ms",
        )
        val summary = PingOutputParser.parseSummary(lines)
        assertNotNull(summary)
        assertEquals(3, summary!!.transmitted)
        assertEquals(1, summary.received)
        assertEquals(66.7f, summary.lossPercent)
        assertEquals(0f, summary.minMs)
    }

    @Test
    fun `parseSummary with no matching lines returns null`() {
        val lines = listOf("some random output", "not ping stats")
        assertNull(PingOutputParser.parseSummary(lines))
    }

    @Test
    fun `parseSummary with empty list returns null`() {
        assertNull(PingOutputParser.parseSummary(emptyList()))
    }
}
