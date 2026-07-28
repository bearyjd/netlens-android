package com.ventouxlabs.netlens.feature.wifi.engine

import com.ventouxlabs.netlens.core.data.model.WifiSurveyPointEntity
import com.ventouxlabs.netlens.feature.wifi.model.SignalQuality
import com.ventouxlabs.netlens.feature.wifi.model.WifiSignalSample
import kotlin.math.roundToInt

/** Turns a burst of samples taken while standing still into one stored survey point. */
object SurveyAggregator {

    /**
     * Returns null for an empty burst — a point with no readings would render as a 0 dBm spike
     * and skew every roll-up that reads [WifiSurveyPointEntity.avgRssi].
     *
     * The BSSID recorded is the one that served the *most* samples in the burst: if the phone
     * roams mid-capture, the room is better described by the radio that actually held it than
     * by whichever one happened to be attached on the last tick.
     */
    fun aggregate(
        sessionId: Long,
        label: String,
        samples: List<WifiSignalSample>,
        capturedAt: Long,
    ): WifiSurveyPointEntity? {
        if (samples.isEmpty()) return null
        val rssis = samples.map { it.rssi }
        val dominant = samples.mapNotNull { it.bssid }
            .groupingBy { it }
            .eachCount()
            .maxByOrNull { it.value }
            ?.key
        val last = samples.last()
        return WifiSurveyPointEntity(
            sessionId = sessionId,
            label = label,
            capturedAt = capturedAt,
            avgRssi = rssis.average().roundToInt(),
            minRssi = rssis.min(),
            maxRssi = rssis.max(),
            sampleCount = samples.size,
            bssid = dominant,
            frequency = last.frequency,
            channel = ChannelCalculator.frequencyToChannel(last.frequency),
            linkSpeedMbps = last.linkSpeedMbps,
        )
    }

    /** Points whose average sits at [SignalQuality.WEAK] or worse, worst first. */
    fun weakSpots(points: List<WifiSurveyPointEntity>): List<WifiSurveyPointEntity> =
        points.filter { SignalQuality.forRssi(it.avgRssi).isWeakSpot }
            .sortedBy { it.avgRssi }
}
