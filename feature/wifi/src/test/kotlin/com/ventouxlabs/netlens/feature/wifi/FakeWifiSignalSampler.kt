package com.ventouxlabs.netlens.feature.wifi

import com.ventouxlabs.netlens.feature.wifi.engine.WifiSignalSampler
import com.ventouxlabs.netlens.feature.wifi.model.WifiSignalSample
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow

/**
 * Hand-driven sampler: tests push each reading explicitly instead of waiting on a timer, so a
 * capture burst completes in as many `emit` calls as it needs samples.
 */
class FakeWifiSignalSampler(
    /** When false, the flow never emits — the "phone isn't on Wi-Fi" case. */
    var connected: Boolean = true,
) : WifiSignalSampler {

    private val live = MutableSharedFlow<WifiSignalSample?>(extraBufferCapacity = 64)

    /**
     * A reading pushed while nobody is collecting. It is handed to the *next* subscriber and then
     * forgotten — which is what the production sampler does, since it polls the association the
     * moment it is subscribed to.
     *
     * This deliberately replaces a `replay = 1` buffer. What changed, precisely: a sample emitted
     * *while a collector was subscribed* is no longer redelivered to the next one. Under replay a
     * burst resuming after a fold silently absorbed one stale pre-stop sample, so tests whose
     * sample arithmetic worked did so by accident.
     *
     * What did NOT change: a sample pushed while nothing is collecting is still held and handed to
     * the next subscriber — that is this field, and tests rely on it (see `startSurvey`, and the
     * emit between `onScreenStopped` and `onScreenStarted` in the backgrounding test). Only one is
     * held; a second push while unsubscribed overwrites the first and is lost, so `emitBurst`
     * against a stopped screen collapses to a single sample.
     */
    private var pending: WifiSignalSample? = null

    /** Intervals the ViewModel asked for, so tests can assert the sampling cadence. */
    val requestedIntervals = mutableListOf<Long>()

    private var nextTimestamp = 1_000L

    override fun samples(intervalMs: Long): Flow<WifiSignalSample?> {
        requestedIntervals.add(intervalMs)
        // Must not be emptyFlow(): that *completes*, so a caller's timeout is never exercised and
        // a test would pass with the timeout deleted while the real app hung forever on Start.
        // The production sampler polls on and on, emitting null each tick — so does this.
        if (!connected) return flow { while (true) { emit(null); delay(intervalMs) } }
        return flow {
            pending?.let {
                pending = null
                emit(it)
            }
            emitAll(live)
        }
    }

    /** One tick observed while unassociated — what a walk out of range looks like. */
    suspend fun emitDisconnected() {
        push(null)
    }

    suspend fun emit(
        rssi: Int,
        bssid: String? = "aa:bb:cc:dd:ee:01",
        ssid: String? = "HomeWiFi",
        frequency: Int = 5180,
        linkSpeedMbps: Int = 400,
    ) {
        push(
            WifiSignalSample(
                timestampMs = nextTimestamp,
                rssi = rssi,
                ssid = ssid,
                bssid = bssid,
                frequency = frequency,
                linkSpeedMbps = linkSpeedMbps,
            ),
        )
        nextTimestamp += 700L
    }

    private suspend fun push(sample: WifiSignalSample?) {
        if (live.subscriptionCount.value == 0) pending = sample else live.emit(sample)
    }

    /** Emits [count] readings at [rssi] — one full capture burst by default. */
    suspend fun emitBurst(count: Int, rssi: Int, bssid: String? = "aa:bb:cc:dd:ee:01") {
        repeat(count) { emit(rssi, bssid = bssid) }
    }
}
