package com.ventouxlabs.netlens.feature.celltower.model

enum class SignalQuality { Excellent, Fair, Poor, Unknown }

fun rsrpQuality(rsrp: Int?): SignalQuality = when {
    rsrp == null -> SignalQuality.Unknown
    rsrp > -80 -> SignalQuality.Excellent
    rsrp > -100 -> SignalQuality.Fair
    else -> SignalQuality.Poor
}

fun rssiQuality(rssi: Int?): SignalQuality = when {
    rssi == null -> SignalQuality.Unknown
    rssi > -75 -> SignalQuality.Excellent
    rssi > -95 -> SignalQuality.Fair
    else -> SignalQuality.Poor
}
