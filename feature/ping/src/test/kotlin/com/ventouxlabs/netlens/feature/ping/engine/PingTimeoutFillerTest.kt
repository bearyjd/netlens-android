package com.ventouxlabs.netlens.feature.ping.engine

import com.ventouxlabs.netlens.feature.ping.model.PingResult
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class PingTimeoutFillerTest {

    private fun reply(seq: Int, ms: Float = 12.3f) =
        PingResult(sequenceNumber = seq, latencyMs = ms, ttl = 56, ip = "1.1.1.1")

    private fun timeout(seq: Int) = PingResult(sequenceNumber = seq, isTimeout = true)

    // --- reconcile: gap filling ---------------------------------------------------------------

    @Test
    fun `a skipped sequence number becomes a synthetic timeout`() {
        val out = PingTimeoutFiller.reconcile(listOf(reply(1)), reply(4))

        assertEquals(listOf(1, 2, 3, 4), out.results.map { it.sequenceNumber })
        assertEquals(listOf(false, true, true, false), out.results.map { it.isTimeout })
        // Three packets newly accounted for (2, 3, 4), one of them a reply.
        assertEquals(3, out.sentDelta)
        assertEquals(1, out.receivedDelta)
    }

    @Test
    fun `consecutive replies fill no gaps`() {
        val out = PingTimeoutFiller.reconcile(listOf(reply(1)), reply(2))

        assertEquals(listOf(1, 2), out.results.map { it.sequenceNumber })
        assertEquals(1, out.sentDelta)
        assertEquals(1, out.receivedDelta)
    }

    @Test
    fun `an appended timeout counts as sent but not received`() {
        val out = PingTimeoutFiller.reconcile(listOf(reply(1)), timeout(2))

        assertEquals(1, out.sentDelta)
        assertEquals(0, out.receivedDelta)
    }

    // The first result establishes the numbering base (toybox starts at 0, iputils at 1), so a
    // leading gap is indistinguishable from a different base and is deliberately NOT filled.
    @Test
    fun `the first result never back-fills a leading gap`() {
        val out = PingTimeoutFiller.reconcile(emptyList(), reply(3))

        assertEquals(listOf(3), out.results.map { it.sequenceNumber })
        assertEquals(1, out.sentDelta)
    }

    // --- reconcile: duplicates and late replies ------------------------------------------------

    // PingScreen keys its LazyColumn on sequenceNumber — a duplicate seq is a crash, so the
    // upgrade must REPLACE the synthetic row, never append alongside it.
    @Test
    fun `a late reply upgrades its synthetic timeout row in place`() {
        val existing = listOf(reply(1), timeout(2), timeout(3))

        val out = PingTimeoutFiller.reconcile(existing, reply(2, ms = 850f))

        assertEquals(listOf(1, 2, 3), out.results.map { it.sequenceNumber })
        assertEquals(850f, out.results[1].latencyMs)
        // No new packet — the timeout row already accounted for it — but one more reply.
        assertEquals(0, out.sentDelta)
        assertEquals(1, out.receivedDelta)
    }

    @Test
    fun `a duplicate reply is dropped, not appended`() {
        val existing = listOf(reply(1), reply(2))

        val out = PingTimeoutFiller.reconcile(existing, reply(2, ms = 99f))

        assertEquals(existing, out.results)
        assertEquals(0, out.sentDelta)
        assertEquals(0, out.receivedDelta)
    }

    @Test
    fun `a timeout never downgrades an already-received reply`() {
        val existing = listOf(reply(1), reply(2))

        val out = PingTimeoutFiller.reconcile(existing, timeout(2))

        assertEquals(existing, out.results)
        assertEquals(0, out.sentDelta)
        assertEquals(0, out.receivedDelta)
    }

    // In continuous mode the list is a rolling window: a seq below its first row may have had
    // its own row truncated away, so it is unplaceable — appending would break the monotonic
    // order the screen and latency chart assume, and re-count an already-counted packet.
    @Test
    fun `a seq below the rolling window is dropped, not appended`() {
        val window = listOf(reply(101), reply(102))

        val out = PingTimeoutFiller.reconcile(window, reply(3))

        assertEquals(window, out.results)
        assertEquals(0, out.sentDelta)
        assertEquals(0, out.receivedDelta)
    }

    // --- completionFill -----------------------------------------------------------------------

    @Test
    fun `a short fixed run is filled out with trailing timeouts`() {
        val out = PingTimeoutFiller.completionFill(listOf(reply(1), reply(2)), expectedCount = 4)

        assertEquals(listOf(1, 2, 3, 4), out.results.map { it.sequenceNumber })
        assertEquals(listOf(false, false, true, true), out.results.map { it.isTimeout })
        assertEquals(2, out.sentDelta)
        assertEquals(0, out.receivedDelta)
    }

    // The dead-host case this feature exists for: zero output, so the whole run is timeouts.
    @Test
    fun `a run with no replies at all becomes all timeouts`() {
        val out = PingTimeoutFiller.completionFill(emptyList(), expectedCount = 4)

        assertEquals(listOf(1, 2, 3, 4), out.results.map { it.sequenceNumber })
        assertEquals(4, out.results.count { it.isTimeout })
        assertEquals(4, out.sentDelta)
    }

    @Test
    fun `a complete run is left untouched`() {
        val existing = listOf(reply(1), reply(2))

        val out = PingTimeoutFiller.completionFill(existing, expectedCount = 2)

        assertEquals(existing, out.results)
        assertEquals(0, out.sentDelta)
    }

    // Numbering continues from the last row, so a 0-based toybox run stays 0-based.
    @Test
    fun `completion fill continues the run's own numbering base`() {
        val zeroBased = listOf(reply(0), reply(1))

        val out = PingTimeoutFiller.completionFill(zeroBased, expectedCount = 4)

        assertEquals(listOf(0, 1, 2, 3), out.results.map { it.sequenceNumber })
    }

    // --- watchdog seq -------------------------------------------------------------------------

    @Test
    fun `watchdog timeout continues after the last row, starting at 1 on silence`() {
        assertEquals(1, PingTimeoutFiller.nextWatchdogTimeout(emptyList()).sequenceNumber)
        assertEquals(6, PingTimeoutFiller.nextWatchdogTimeout(listOf(reply(5))).sequenceNumber)
    }
}
