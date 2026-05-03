package com.ventoux.netlens.feature.traceroute.model

data class TracerouteHop(
    val hopNumber: Int,
    val ip: String? = null,
    val hostname: String? = null,
    val rttMs: List<Float> = emptyList(),
    val isTimeout: Boolean = false,
    val location: HopLocation? = null,
    val anomalies: List<HopAnomaly> = emptyList(),
)
