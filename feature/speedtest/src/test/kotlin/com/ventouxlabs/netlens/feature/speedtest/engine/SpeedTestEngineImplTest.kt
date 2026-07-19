package com.ventouxlabs.netlens.feature.speedtest.engine

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.utils.io.ByteChannel
import io.ktor.utils.io.readAvailable
import io.ktor.utils.io.writeFully
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

@OptIn(ExperimentalCoroutinesApi::class)
class SpeedTestEngineImplTest {

    @Test
    fun `throughputMbps converts bytes and elapsed to Mbps`() {
        // 25 MB in 1000 ms == 200 Mbps.
        assertEquals(200f, SpeedTestEngineImpl.throughputMbps(25_000_000L, 1_000L))
        // Guard against divide-by-zero before any time has elapsed.
        assertEquals(0f, SpeedTestEngineImpl.throughputMbps(1_000L, 0L))
    }

    @Test
    fun `download aggregates bytes across four parallel streams`() = runTest {
        val perStream = 1_000_000
        val engine = MockEngine { respond(content = ByteArray(perStream)) }
        val impl = SpeedTestEngineImpl(
            engine = engine,
            ioDispatcher = StandardTestDispatcher(testScheduler),
            timeSource = { testScheduler.currentTime },
        )

        val items = impl.measureDownload().toList()

        assertTrue(items.isNotEmpty(), "expected at least one progress emission")
        // Four streams, each serving `perStream` bytes, aggregate into the final byte count.
        assertEquals(4L * perStream, items.last().bytesTransferred)
    }

    @Test
    fun `final download speed excludes warm-up bytes and time`() = runTest {
        // Each stream trickles a little during the warm-up window, then bursts once warm-up is
        // over. The steady-state figure must reflect the post-warm-up burst, so it is strictly
        // faster than the naive since-first-byte average that folds the slow ramp back in.
        val warmBytes = 100_000
        val fastBytes = 2_000_000
        val channels = List(4) { ByteChannel(autoFlush = true) }
        channels.forEach { ch ->
            launch {
                ch.writeFully(ByteArray(warmBytes))
                ch.flush()
                delay(2_000L) // cross the 1_500 ms warm-up boundary
                ch.writeFully(ByteArray(fastBytes))
                ch.flushAndClose()
            }
        }
        val next = AtomicInteger(0)
        val engine = MockEngine { respond(content = channels[next.getAndIncrement()]) }
        val impl = SpeedTestEngineImpl(
            engine = engine,
            ioDispatcher = StandardTestDispatcher(testScheduler),
            timeSource = { testScheduler.currentTime },
        )

        val items = impl.measureDownload().toList()
        val finalItem = items.last()

        assertEquals(4L * (warmBytes + fastBytes), finalItem.bytesTransferred)
        val naiveCumulative =
            SpeedTestEngineImpl.throughputMbps(finalItem.bytesTransferred, finalItem.elapsedMs)
        assertTrue(finalItem.speedMbps > 0f, "steady-state speed should be positive")
        assertTrue(
            finalItem.speedMbps > naiveCumulative,
            "steady-state speed (${finalItem.speedMbps}) should exceed the warm-up-inclusive " +
                "average ($naiveCumulative) because the slow ramp is discarded",
        )
    }

    @Test
    fun `one failing stream does not fail the flow`() = runTest {
        val perStream = 1_000_000
        val calls = AtomicInteger(0)
        val engine = MockEngine {
            if (calls.getAndIncrement() == 0) throw IOException("one stream down")
            respond(content = ByteArray(perStream))
        }
        val impl = SpeedTestEngineImpl(
            engine = engine,
            ioDispatcher = StandardTestDispatcher(testScheduler),
            timeSource = { testScheduler.currentTime },
        )

        val items = impl.measureDownload().toList()

        assertTrue(items.isNotEmpty(), "flow should still produce data with 3 of 4 streams alive")
        // The three surviving streams still aggregate; the dead one contributes nothing.
        assertEquals(3L * perStream, items.last().bytesTransferred)
    }

    @Test
    fun `all streams failing before warm-up propagates the error`() = runTest {
        val engine = MockEngine { throw IOException("everything is down") }
        val impl = SpeedTestEngineImpl(
            engine = engine,
            ioDispatcher = StandardTestDispatcher(testScheduler),
            timeSource = { testScheduler.currentTime },
        )

        val error = runCatching { impl.measureDownload().toList() }.exceptionOrNull()

        assertNotNull(error, "flow must propagate when every stream fails before warm-up")
        assertTrue(error is IOException, "propagated error should be the seeded IOException, not a substitute")
        assertEquals("everything is down", error?.message)
    }

    @Test
    fun `upload body counts bytes as written before any response`() = runTest {
        // Exercises the exact production egress-counting path (writeUploadBody) against a channel
        // that is never read to completion and never produces an HTTP response. Bytes are counted
        // purely from being written, which is the fix's core invariant. A full multi-stream upload
        // over Ktor requires a server that drains the chunked body, so it needs on-device
        // verification; this covers the counting/first-byte logic honestly.
        val counter = AtomicLong(0L)
        var firstByteMarked = false
        val chunk = ByteArray(1_024)
        val sink = ByteChannel(autoFlush = true)

        val writer = launch(UnconfinedTestDispatcher(testScheduler)) {
            SpeedTestEngineImpl.writeUploadBody(sink, counter, { firstByteMarked = true }, chunk)
        }
        runCurrent()

        // The writer fills the channel buffer and suspends; every chunk it managed to write is
        // already counted, with no reader and no response involved.
        assertTrue(counter.get() > 0L, "bytes should be counted as they are written")
        assertTrue(firstByteMarked, "first written byte should mark the start of the transfer")

        writer.cancel()
    }

    @Test
    fun `measureLatency discards the first sample and returns the median of the rest`() = runTest {
        // 6 samples requested (LATENCY_SAMPLES + 1 = 5 + 1). Sample 0 is always discarded
        // regardless of its value, so a wildly different first value must not affect the result.
        val values = listOf(9_999L, 30L, 10L, 20L, 40L, 15L)
        val calls = AtomicInteger(0)
        val impl = SpeedTestEngineImpl(
            engine = MockEngine { respond(content = "") },
            ioDispatcher = StandardTestDispatcher(testScheduler),
            connectProbe = { values[calls.getAndIncrement()] },
            resolveAddresses = { listOf(InetAddress.getLoopbackAddress()) },
        )

        // Remaining after discarding index 0: [30, 10, 20, 40, 15] -> sorted [10, 15, 20, 30, 40].
        assertEquals(20L, impl.measureLatency())
    }

    @Test
    fun `measureLatency skips failed connects and medians the survivors`() = runTest {
        val calls = AtomicInteger(0)
        val impl = SpeedTestEngineImpl(
            engine = MockEngine { respond(content = "") },
            ioDispatcher = StandardTestDispatcher(testScheduler),
            connectProbe = { address ->
                when (calls.getAndIncrement()) {
                    0 -> throw IOException("cold warm-up sample fails too, still discarded")
                    1 -> throw IOException("dropped connection")
                    2 -> 10L
                    3 -> 20L
                    4 -> 30L
                    else -> 40L
                }
            },
            resolveAddresses = { listOf(InetAddress.getLoopbackAddress()) },
        )

        // Attempt 0 discarded regardless; attempt 1 failed and is skipped; survivors [10, 20, 30, 40]
        // sorted, median at index size/2=2 -> 30.
        assertEquals(30L, impl.measureLatency())
    }

    @Test
    fun `measureLatency throws IOException when every post-warmup connect fails`() = runTest {
        val impl = SpeedTestEngineImpl(
            engine = MockEngine { respond(content = "") },
            ioDispatcher = StandardTestDispatcher(testScheduler),
            connectProbe = { throw IOException("connect refused") },
            resolveAddresses = { listOf(InetAddress.getLoopbackAddress()) },
        )

        val error = runCatching { impl.measureLatency() }.exceptionOrNull()

        assertTrue(error is IOException, "should propagate an IOException when every sample fails")
    }

    @Test
    fun `measureLatency falls back to a second address when the first family fails`() = runTest {
        // Simulates a v6 primary that never connects and a v4 fallback that does. Every sample must
        // try v6 first (and fail), then succeed on v4, so the median is the v4 RTT.
        val v6 = InetSocketAddress(InetAddress.getByName("::1"), 443)
        val v4 = InetSocketAddress(InetAddress.getByName("127.0.0.1"), 443)
        val v6Attempts = AtomicInteger(0)
        val impl = SpeedTestEngineImpl(
            engine = MockEngine { respond(content = "") },
            ioDispatcher = StandardTestDispatcher(testScheduler),
            connectProbe = { address ->
                when (address.address) {
                    v6.address -> { v6Attempts.incrementAndGet(); throw IOException("v6 unreachable") }
                    else -> 25L
                }
            },
            resolveAddresses = { listOf(v6.address, v4.address) },
        )

        assertEquals(25L, impl.measureLatency())
        // 6 samples requested, each exhausts the failing v6 primary before falling back to v4.
        assertEquals(6, v6Attempts.get())
    }
}
