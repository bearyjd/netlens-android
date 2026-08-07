package com.ventouxlabs.netlens.core.scan.engine

import com.ventouxlabs.netlens.core.scan.model.NetBiosInfo
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.random.Random

/**
 * Adversarial inputs for the NetBIOS name-service response parser.
 *
 * `parseResponse` reads a UDP datagram from port 137 — every byte chosen by whoever answers, who
 * is anyone on the LAN. It walks the buffer with hand-rolled offset arithmetic (`offset += 4`,
 * `offset += 6`, a `nameCount` byte that drives a loop), which is the shape that produces
 * index-out-of-bounds bugs. [NetBiosProberTest] covers well-formed responses; this file covers
 * responses built to break the walk.
 *
 * **The contract is "return null or a value, never throw".** `probe` wraps the call in a
 * catch-all, so a throw would be silently swallowed into "no NetBIOS info" — present in
 * production, invisible in tests. These assert on the parser directly.
 */
class NetBiosHostileInputTest {

    /** Asserts no throw, returning the result. Failure message names the input. */
    private fun parse(data: ByteArray, length: Int = data.size): NetBiosInfo? {
        val result = runCatching { NetBiosProberImpl.parseResponse(data, length) }
        assertTrue(
            result.isSuccess,
            "parseResponse threw on ${data.size} bytes / length=$length: ${result.exceptionOrNull()}",
        )
        return result.getOrNull()
    }

    @Test
    fun `undersized packets are rejected without reading past the end`() {
        for (size in 0..56) {
            assertNull(parse(ByteArray(size)), "size $size should be rejected")
        }
    }

    @Test
    fun `length longer than the array does not read out of bounds`() {
        // Regression for an ArrayIndexOutOfBoundsException the random fuzz below found first.
        //
        // The bytes must be NON-ZERO. With a zero-filled array the name-skip loop exits on the
        // first byte and never walks off the end, so a zero-filled version of this test passes
        // against the buggy parser — which is exactly what an earlier hand-written probe did,
        // reporting "ok" for input that was in fact broken. Filling with 0x41 makes the loop run
        // to the end of the array and index one past it.
        parse(ByteArray(60) { 0x41 }, length = 5_000)
        parse(ByteArray(60) { 0x41 }, length = Int.MAX_VALUE)
        parse(ByteArray(200) { 0x41 }, length = 201)
    }

    @Test
    fun `negative and zero lengths are handled`() {
        assertNull(parse(ByteArray(100), length = 0))
        assertNull(parse(ByteArray(100), length = -1))
    }

    @Test
    fun `an inflated name count cannot walk past the buffer`() {
        // nameCount is a single attacker-controlled byte that drives the read loop. 0xFF claims
        // 255 entries of 18 bytes = 4590, far beyond any real datagram.
        val packet = wellFormed(nameCount = 0xFF, entries = 1)

        parse(packet)
    }

    @Test
    fun `a name count of zero yields null rather than a partial record`() {
        assertNull(parse(wellFormed(nameCount = 0, entries = 0)))
    }

    @Test
    fun `a response whose name field never terminates is handled`() {
        // The parser skips the name with `while (offset < length && data[offset] != 0)`. A buffer
        // with no zero byte at all drives offset to `length`, and everything after must cope.
        val packet = ByteArray(120) { 0x41 }
        parse(packet)
    }

    @Test
    fun `non-ascii and zero bytes inside a name do not throw`() {
        val packet = wellFormed(nameCount = 1, entries = 1) { nameBytes ->
            byteArrayOf(0xFF.toByte(), 0x00, 0x7F, 0xC3.toByte(), 0x28) +
                ByteArray(nameBytes - 5) { 0x20 }
        }

        parse(packet)
    }

    @Test
    fun `a well-formed response still parses — the corpus has not broken the happy path`() {
        val packet = wellFormed(nameCount = 1, entries = 1) { nameBytes ->
            "NAS".padEnd(nameBytes, ' ').toByteArray(Charsets.US_ASCII)
        }

        assertEquals("NAS", parse(packet)?.name)
    }

    @Test
    fun `random fuzz over the length space never throws`() {
        // Deterministic seed: a failure must be reproducible, and a random-seeded test that fails
        // once a month is worse than no test.
        val random = Random(20260807)
        repeat(2_000) { i ->
            val size = random.nextInt(0, 300)
            val data = ByteArray(size).also(random::nextBytes)
            val length = if (i % 8 == 0) random.nextInt(-2, 400) else size
            parse(data, length)
        }
    }

    @Test
    fun `random fuzz around the size threshold never throws`() {
        // 57 is the parser's minimum; concentrate on the boundary where offset arithmetic is
        // most likely to run off the end.
        val random = Random(20260807)
        repeat(2_000) {
            val data = ByteArray(random.nextInt(50, 90)).also(random::nextBytes)
            parse(data)
        }
    }

    /**
     * Builds a structurally valid response: 12-byte header, a terminated name, type/class, TTL,
     * data length, then [nameCount] followed by [entries] 18-byte name records.
     *
     * [nameCount] is separate from [entries] on purpose — a hostile responder can claim more
     * records than it sends, which is the interesting case.
     */
    private fun wellFormed(
        nameCount: Int,
        entries: Int,
        nameBytes: (Int) -> ByteArray = { ByteArray(it) { 0x20 } },
    ): ByteArray {
        val out = ArrayList<Byte>()
        repeat(12) { out.add(0) }              // header
        out.add(0x20); repeat(32) { out.add(0x41) }  // encoded name
        out.add(0)                             // name terminator
        repeat(4) { out.add(0) }               // type + class
        repeat(6) { out.add(0) }               // TTL + data length
        out.add(nameCount.toByte())
        repeat(entries) {
            out.addAll(nameBytes(15).toList())
            out.add(0x00)                      // name type: workstation
            out.add(0x00); out.add(0x00)       // flags: unique
        }
        return out.toByteArray()
    }
}
