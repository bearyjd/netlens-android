package com.ventouxlabs.netlens.core.scan.model

/** Upper bound on a device-supplied model string before it is stored or shown. */
const val MAX_MODEL_LENGTH = 80

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
    val fingerprintConfidence: Int = 0,
    val fingerprintEvidence: List<String> = emptyList(),
    // Hardware model reported by the device itself (e.g. SSDP modelName). Device-supplied, hostile.
    val model: String? = null,
)
