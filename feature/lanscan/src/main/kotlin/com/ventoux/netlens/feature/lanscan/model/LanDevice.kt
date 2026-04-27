package com.ventoux.netlens.feature.lanscan.model

data class LanDevice(
    val ip: String,
    val hostname: String? = null,
    val isReachable: Boolean = true,
    val latencyMs: Long = 0,
    val deviceType: String? = null,
    val osGuess: String? = null,
    val discoveryMethod: DiscoveryMethod = DiscoveryMethod.PING,
    val services: List<String> = emptyList(),
    val macAddress: String? = null,
    val vendor: String? = null,
)
