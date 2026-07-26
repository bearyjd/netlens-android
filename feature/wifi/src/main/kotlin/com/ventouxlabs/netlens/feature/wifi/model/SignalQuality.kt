package com.ventouxlabs.netlens.feature.wifi.model

/**
 * RSSI banding used across the survey UI.
 *
 * Thresholds follow the usual field rules of thumb: -55 dBm and up is as good as it gets on
 * consumer gear, -67 dBm is the commonly-cited floor for reliable video/voice, below -75 dBm
 * throughput falls off hard, and below -85 dBm the link is effectively unusable. A spot only
 * counts as a *weak spot* at [WEAK] or worse — [FAIR] still works, it just has no headroom.
 */
enum class SignalQuality(val minRssi: Int) {
    EXCELLENT(-55),
    GOOD(-67),
    FAIR(-75),
    WEAK(-85),
    UNUSABLE(Int.MIN_VALUE),
    ;

    val isWeakSpot: Boolean get() = this == WEAK || this == UNUSABLE

    companion object {
        fun forRssi(rssi: Int): SignalQuality = entries.first { rssi >= it.minRssi }

        /**
         * Maps an RSSI onto 0f..1f for bar/graph rendering, clamped to the -95..-30 dBm window
         * the survey graphs use as their axis.
         */
        fun normalize(rssi: Int): Float =
            ((rssi - MIN_GRAPH_RSSI).toFloat() / (MAX_GRAPH_RSSI - MIN_GRAPH_RSSI)).coerceIn(0f, 1f)

        const val MIN_GRAPH_RSSI = -95
        const val MAX_GRAPH_RSSI = -30
    }
}
