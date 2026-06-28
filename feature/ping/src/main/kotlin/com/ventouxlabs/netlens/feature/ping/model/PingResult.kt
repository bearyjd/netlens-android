package com.ventouxlabs.netlens.feature.ping.model

data class PingResult(
    val sequenceNumber: Int,
    val latencyMs: Float? = null,
    val isTimeout: Boolean = false,
    val ttl: Int? = null,
    val ip: String? = null,
)
