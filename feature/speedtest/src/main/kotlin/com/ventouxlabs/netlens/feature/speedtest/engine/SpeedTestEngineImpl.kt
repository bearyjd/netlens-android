package com.ventouxlabs.netlens.feature.speedtest.engine

import com.ventouxlabs.netlens.feature.speedtest.model.SpeedProgress
import com.ventouxlabs.netlens.feature.speedtest.model.SpeedTestPhase
import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.request.prepareGet
import io.ktor.client.request.preparePost
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.ContentType
import io.ktor.http.isSuccess
import io.ktor.http.content.OutgoingContent
import io.ktor.utils.io.ByteWriteChannel
import io.ktor.utils.io.readAvailable
import io.ktor.utils.io.writeFully
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URI
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import kotlin.random.Random

/**
 * Throughput is measured with the methodology commercial speed tests use, because a single
 * short TCP transfer measured from before the request systematically under-reports a fast link:
 *
 *  - **Parallel streams.** [STREAMS] concurrent connections are needed to saturate a fast or
 *    lossy path; one connection stalls in TCP flow control / loss recovery and reads a fraction
 *    of the real capacity.
 *  - **Time-bounded, not size-bounded.** Each stream requests a large body ([PER_REQUEST_BYTES]);
 *    the run is bounded by [SpeedTestEngine.MEASURE_WINDOW_MS] and the streams are cancelled at the
 *    end, so data use is capped by duration, not by a fixed download size.
 *  - **Warm-up exclusion, clock from first byte.** The rate clock starts when the first byte
 *    arrives (TTFB excluded) and the first [WARMUP_MS] — TCP slow-start ramp — are discarded so
 *    the reported figure is steady-state throughput rather than an average dragged down by the
 *    ramp.
 */
class SpeedTestEngineImpl private constructor(
    private val client: HttpClient,
    private val ioDispatcher: CoroutineDispatcher,
    private val timeSource: () -> Long,
    private val connectProbe: suspend (InetSocketAddress) -> Long,
    private val resolveAddresses: (String) -> List<InetAddress>,
    // Visible for testing: replaces the per-stream download body with a caller-supplied one.
    // Tests inject a pure-coroutine byte source so the whole run stays on the virtual test
    // scheduler — real Ktor reads hop to a real dispatcher, which raced the virtual-time
    // sampling loop under CI load and made the throughput tests flaky. null in production.
    private val downloadStreamOverride: (suspend (AtomicLong, () -> Unit) -> Unit)? = null,
) : SpeedTestEngine {

    @Inject constructor() : this(
        HttpClient(OkHttp) {
            engine {
                // OkHttp's connection pool (default: keep-alive, reused across the 4 parallel
                // streams) is the whole point of this engine swap over CIO — see KTOR-6503.
                // callTimeout covers connect+write+read for the whole call, matching CIO's
                // requestTimeout semantics; a stream is allowed to live the full measurement
                // window (MEASURE_WINDOW_MS) plus margin.
                config { callTimeout(TIMEOUT_MS, TimeUnit.MILLISECONDS) }
            }
        },
        Dispatchers.IO,
        System::currentTimeMillis,
        defaultConnectProbe(System::currentTimeMillis),
        defaultResolveAddresses(),
    )

    /**
     * Visible for testing: swap in a [io.ktor.client.engine.mock.MockEngine], a test dispatcher
     * backed by the coroutines-test scheduler, a virtual-time [timeSource] so the warm-up and
     * window arithmetic is deterministic, a [connectProbe] so [measureLatency] samples don't
     * require real sockets, and a [resolveAddresses] so the resolve step is deterministic and the
     * address-family fallback can be exercised without real DNS.
     */
    internal constructor(
        engine: HttpClientEngine,
        ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
        timeSource: () -> Long = System::currentTimeMillis,
        connectProbe: suspend (InetSocketAddress) -> Long = defaultConnectProbe(timeSource),
        resolveAddresses: (String) -> List<InetAddress> = defaultResolveAddresses(),
        downloadStreamOverride: (suspend (AtomicLong, () -> Unit) -> Unit)? = null,
    ) : this(
        HttpClient(engine),
        ioDispatcher,
        timeSource,
        connectProbe,
        resolveAddresses,
        downloadStreamOverride,
    )

    override fun measureDownload(): Flow<SpeedProgress> =
        measureThroughput(SpeedTestPhase.DOWNLOAD) { counter, markFirstByte ->
            (downloadStreamOverride ?: { c, m -> downloadStream(c, m) })(counter, markFirstByte)
        }

    override fun measureUpload(): Flow<SpeedProgress> =
        measureThroughput(SpeedTestPhase.UPLOAD) { counter, markFirstByte ->
            uploadStream(counter, markFirstByte)
        }

    /**
     * Reports idle TCP connect RTT to [BASE_URL], not full HTTPS request time. Commercial speed
     * tests (e.g. Google's) report latency this way — the time to open a bare TCP connection —
     * so this is the figure comparable to them; a full HEAD request (the old approach) pays TLS
     * handshake and response wait on top, which read ~25x higher than the browser reference on
     * the same link.
     *
     * Samples [LATENCY_SAMPLES] + 1 raw connects to port [HTTPS_PORT]. The first is always
     * discarded regardless of outcome (ARP/route-cache warm-up, not representative of
     * steady-state RTT); the reported value is the median of the remaining successful samples.
     * An individual failed connect is skipped; if every post-warm-up sample fails, [IOException]
     * is thrown.
     *
     * The host is resolved once (all address families) before sampling. Each sample tries the
     * resolved addresses in order, so a v6 address that is unreachable on a v4-only path falls
     * back to the next candidate rather than failing the whole sample.
     */
    override suspend fun measureLatency(): Long = withContext(ioDispatcher) {
        val host = URI(BASE_URL).host
        val candidates = resolveAddresses(host).map { InetSocketAddress(it, HTTPS_PORT) }
        if (candidates.isEmpty()) throw IOException("No addresses resolved for $BASE_URL")
        val samples = (0..LATENCY_SAMPLES).mapNotNull { attempt ->
            val elapsed = probeWithFallback(candidates)
            if (attempt == 0) null else elapsed
        }
        if (samples.isEmpty()) throw IOException("All TCP connect probes to $BASE_URL failed")
        samples.sorted().let { it[it.size / 2] }
    }

    /**
     * Times a single connect, trying each resolved address in turn until one succeeds. Returns the
     * RTT of the first successful connect, or null if every candidate failed for this sample.
     */
    private suspend fun probeWithFallback(candidates: List<InetSocketAddress>): Long? {
        for (address in candidates) {
            val elapsed = runCatching { connectProbe(address) }
                .onFailure { if (it is CancellationException) throw it }
                .getOrNull()
            if (elapsed != null) return elapsed
        }
        return null
    }

    /**
     * Runs [STREAMS] parallel transfer streams, aggregating bytes into a shared counter, and
     * emits [SpeedProgress] every [EMIT_INTERVAL_MS] until the [SpeedTestEngine.MEASURE_WINDOW_MS] window closes.
     *
     * The streams are launched under a [SupervisorJob] child so one failing stream does not tear
     * down its siblings or the flow. If every stream fails before warm-up completes the failure is
     * propagated; otherwise the aggregate continues on whatever streams remain alive. The final
     * emission is always the steady-state (post-warm-up) figure — that is the value the UI and
     * history record.
     */
    private fun measureThroughput(
        phase: SpeedTestPhase,
        runStream: suspend (counter: AtomicLong, markFirstByte: () -> Unit) -> Unit,
    ): Flow<SpeedProgress> = flow {
        coroutineScope {
            val counter = AtomicLong(0L)
            // -1 = no byte received yet; a real timestamp can legitimately be 0 (virtual time).
            val firstByteAt = AtomicLong(NO_BYTE_YET)
            val failures = AtomicInteger(0)
            val firstError = AtomicReference<Throwable?>(null)
            val markFirstByte: () -> Unit = {
                if (firstByteAt.get() == NO_BYTE_YET) firstByteAt.compareAndSet(NO_BYTE_YET, timeSource())
            }

            val supervisor = SupervisorJob(coroutineContext[Job])
            val streamScope = CoroutineScope(coroutineContext + supervisor)
            repeat(STREAMS) {
                streamScope.launch {
                    try {
                        runStream(counter, markFirstByte)
                    } catch (e: CancellationException) {
                        throw e // window-expiry cancellation of a child; never counted as failure
                    } catch (e: Throwable) {
                        firstError.compareAndSet(null, e)
                        failures.incrementAndGet()
                    }
                }
            }
            val streamJobs = supervisor.children.toList()

            var warmupBytes = 0L
            var warmupTime = 0L
            var warmupDone = false

            try {
                while (true) {
                    val now = timeSource()
                    val startedAt = firstByteAt.get()

                    if (failures.get() >= STREAMS) break
                    if (streamJobs.all { it.isCompleted }) break
                    if (startedAt != NO_BYTE_YET && now - startedAt >= SpeedTestEngine.MEASURE_WINDOW_MS) break

                    if (startedAt != NO_BYTE_YET) {
                        val elapsed = now - startedAt
                        if (!warmupDone && elapsed >= WARMUP_MS) {
                            warmupBytes = counter.get()
                            warmupTime = now
                            warmupDone = true
                        }
                        val total = counter.get()
                        emit(
                            SpeedProgress(
                                bytesTransferred = total,
                                elapsedMs = elapsed,
                                speedMbps = currentSpeed(total, warmupBytes, now, warmupTime, startedAt, warmupDone),
                                phase = phase,
                            ),
                        )
                    }
                    delay(EMIT_INTERVAL_MS)
                }

                val startedAt = firstByteAt.get()
                // No data at all, or every stream died before we had a steady-state sample.
                if (startedAt == NO_BYTE_YET || (!warmupDone && failures.get() >= STREAMS)) {
                    throw firstError.get() ?: IOException("Speed test transferred no data")
                }

                val now = timeSource()
                val total = counter.get()
                emit(
                    SpeedProgress(
                        bytesTransferred = total,
                        elapsedMs = now - startedAt,
                        speedMbps = finalSpeed(total, warmupBytes, now, warmupTime, startedAt, warmupDone),
                        phase = phase,
                    ),
                )
            } finally {
                supervisor.cancel()
            }
        }
    }.flowOn(ioDispatcher)

    private suspend fun downloadStream(counter: AtomicLong, markFirstByte: () -> Unit) {
        // Cloudflare's /__down rejects a `bytes` param over ~100 MB with HTTP 403 + a ~1-byte body
        // (the cause of the "download reads 0" bug: an over-cap request transferred nothing). We
        // request a safely-capped size, and fail loudly on any non-2xx so a future endpoint change
        // surfaces as an error instead of a silent zero.
        val url = "$BASE_URL/__down?bytes=$PER_REQUEST_BYTES"
        val buffer = ByteArray(BUFFER_SIZE)
        client.prepareGet(url).execute { response ->
            if (!response.status.isSuccess()) {
                throw IOException("Download request failed: HTTP ${response.status.value}")
            }
            val channel = response.bodyAsChannel()
            while (currentCoroutineContext().isActive && !channel.isClosedForRead) {
                val read = channel.readAvailable(buffer)
                if (read < 0) break
                if (read > 0) {
                    markFirstByte()
                    counter.addAndGet(read.toLong())
                }
            }
        }
    }

    private suspend fun uploadStream(counter: AtomicLong, markFirstByte: () -> Unit) {
        val chunk = Random.nextBytes(CHUNK_SIZE)
        val body = object : OutgoingContent.WriteChannelContent() {
            override val contentType = ContentType.Application.OctetStream
            override suspend fun writeTo(channel: ByteWriteChannel) {
                writeUploadBody(channel, counter, markFirstByte, chunk)
            }
        }
        client.preparePost("$BASE_URL/__up") { setBody(body) }.execute { /* egress already counted */ }
    }

    companion object {
        const val BASE_URL = "https://speed.cloudflare.com"

        private const val NO_BYTE_YET = -1L
        private const val STREAMS = 4
        // Deliberately far larger than any stream will transfer inside the window; the run is
        // time-bounded and streams are cancelled at the end, so this only sets an upper bound.
        // Cloudflare /__down rejects a bytes param over ~100 MB with HTTP 403 (probed: 90 MB ok,
        // 100 MB rejected), so this stays safely under that cap. Each download stream issues ONE
        // request of this size; it fills the MEASURE_WINDOW_MS window as long as a stream's rate
        // stays under PER_REQUEST_BYTES/window (~75 Mbps/stream, ~300 Mbps aggregate). Above that a
        // stream finishes early and idles for the rest of the window, shortening the steady-state
        // sample. A future re-request loop (like uploadStream) would remove that ceiling — see the
        // speedtest download-loop follow-up.
        private const val PER_REQUEST_BYTES = 75_000_000L
        private const val WARMUP_MS = 1_500L
        private const val EMIT_INTERVAL_MS = 200L
        private const val BUFFER_SIZE = 65_536
        private const val CHUNK_SIZE = 65_536
        private const val TIMEOUT_MS = 30_000L
        private const val LATENCY_SAMPLES = 5
        private const val HTTPS_PORT = 443
        private const val CONNECT_TIMEOUT_MS = 5_000

        /** Bits transferred over the elapsed window, expressed in Mbps. */
        internal fun throughputMbps(bytes: Long, elapsedMs: Long): Float =
            if (elapsedMs > 0L) bytes * 8f / (elapsedMs * 1000f) else 0f

        /**
         * Real [measureLatency] sample: times a raw TCP connect with no HTTP involved. Takes
         * [timeSource] as a parameter (rather than closing over the instance) so both the
         * production and test-facing constructors can build it before `this` exists.
         */
        private fun defaultConnectProbe(timeSource: () -> Long): suspend (InetSocketAddress) -> Long =
            { address ->
                val start = timeSource()
                Socket().use { it.connect(address, CONNECT_TIMEOUT_MS) }
                timeSource() - start
            }

        /** Resolves every address family once so [measureLatency] can fall back between them. */
        private fun defaultResolveAddresses(): (String) -> List<InetAddress> =
            { host -> InetAddress.getAllByName(host).toList() }

        /**
         * Post-warm-up steady-state rate once warm-up has completed; before that, the
         * since-first-byte cumulative rate so the UI still animates during the ramp.
         */
        private fun currentSpeed(
            total: Long,
            warmupBytes: Long,
            now: Long,
            warmupTime: Long,
            startedAt: Long,
            warmupDone: Boolean,
        ): Float = if (warmupDone) {
            throughputMbps(total - warmupBytes, now - warmupTime)
        } else {
            throughputMbps(total, now - startedAt)
        }

        /**
         * Speed for the FINAL emission (the value the UI and history keep). Uses the post-warm-up
         * steady-state rate when that slice is substantial, but falls back to the since-first-byte
         * cumulative rate when it is degenerate. A finite transfer (download) can complete just
         * after the warm-up mark on a fast link, leaving the steady-state window near-empty
         * (`total - warmupBytes` ≈ 0), which would otherwise report 0 Mbps even though data clearly
         * transferred. Upload streams run until the window cancels them, so they never hit this.
         */
        private fun finalSpeed(
            total: Long,
            warmupBytes: Long,
            now: Long,
            warmupTime: Long,
            startedAt: Long,
            warmupDone: Boolean,
        ): Float {
            val cumulative = throughputMbps(total, now - startedAt)
            if (!warmupDone) return cumulative
            val steadyMs = now - warmupTime
            val steadyBytes = total - warmupBytes
            val steadyReliable = steadyMs >= EMIT_INTERVAL_MS && steadyBytes > 0L
            return if (steadyReliable) throughputMbps(steadyBytes, steadyMs) else cumulative
        }

        /**
         * Writes [chunk] repeatedly to [channel] until the coroutine is cancelled, counting bytes
         * into [counter] **as written** (egress), not on response — a single timed round-trip
         * would fold the server's response wait into the upload figure.
         */
        internal suspend fun writeUploadBody(
            channel: ByteWriteChannel,
            counter: AtomicLong,
            markFirstByte: () -> Unit,
            chunk: ByteArray,
        ) {
            while (currentCoroutineContext().isActive) {
                channel.writeFully(chunk)
                channel.flush()
                counter.addAndGet(chunk.size.toLong())
                markFirstByte()
            }
        }
    }
}
