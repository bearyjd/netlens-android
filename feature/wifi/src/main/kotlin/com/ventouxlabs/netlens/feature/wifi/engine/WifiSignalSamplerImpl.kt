package com.ventouxlabs.netlens.feature.wifi.engine

import com.ventouxlabs.netlens.feature.wifi.model.WifiSignalSample
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.isActive
import javax.inject.Inject

/**
 * Polls the live association rather than driving `WifiManager.startScan()`.
 *
 * A walk-through survey needs a reading every second or two; foreground scans are throttled to
 * four per two minutes on API 28+, which would leave most of the house unmeasured. The RSSI on
 * the current connection carries no such limit, and it is the number that actually matters here
 * — how well *this* phone is being held by the network in *this* room.
 */
class WifiSignalSamplerImpl @Inject constructor(
    private val wifiScanner: WifiScanner,
) : WifiSignalSampler {

    override fun samples(intervalMs: Long): Flow<WifiSignalSample> = flow {
        while (currentCoroutineContext().isActive) {
            val info = wifiScanner.observeConnected().firstOrNull()
            if (info != null) {
                emit(
                    WifiSignalSample(
                        timestampMs = System.currentTimeMillis(),
                        rssi = info.rssi,
                        ssid = info.ssid,
                        bssid = info.bssid.ifEmpty { null },
                        frequency = info.frequency,
                        linkSpeedMbps = info.linkSpeedMbps,
                    ),
                )
            }
            delay(intervalMs)
        }
    }
}
