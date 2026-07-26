package com.ventouxlabs.netlens.feature.wifi

import com.ventouxlabs.netlens.feature.wifi.engine.WifiSignalSampler
import com.ventouxlabs.netlens.feature.wifi.model.WifiSignalSample
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow

/**
 * Hand-driven sampler: tests push each reading explicitly instead of waiting on a timer, so a
 * capture burst completes in as many `emit` calls as it needs samples.
 */
class FakeWifiSignalSampler(
    /** When false, the flow never emits — the "phone isn't on Wi-Fi" case. */
    var connected: Boolean = true,
) : WifiSignalSampler {

    private val flow = MutableSharedFlow<WifiSignalSample>(replay = 1, extraBufferCapacity = 64)

    /** Intervals the ViewModel asked for, so tests can assert the sampling cadence. */
    val requestedIntervals = mutableListOf<Long>()

    private var nextTimestamp = 1_000L

    override fun samples(intervalMs: Long): Flow<WifiSignalSample> {
        requestedIntervals.add(intervalMs)
        return if (connected) flow else kotlinx.coroutines.flow.emptyFlow()
    }

    suspend fun emit(
        rssi: Int,
        bssid: String? = "aa:bb:cc:dd:ee:01",
        ssid: String? = "HomeWiFi",
        frequency: Int = 5180,
        linkSpeedMbps: Int = 400,
    ) {
        flow.emit(
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

    /** Emits [count] readings at [rssi] — one full capture burst by default. */
    suspend fun emitBurst(count: Int, rssi: Int, bssid: String? = "aa:bb:cc:dd:ee:01") {
        repeat(count) { emit(rssi, bssid = bssid) }
    }
}
