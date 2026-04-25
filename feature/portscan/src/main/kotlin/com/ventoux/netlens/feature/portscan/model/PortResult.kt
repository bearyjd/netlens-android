package com.ventoux.netlens.feature.portscan.model

data class PortResult(
    val port: Int,
    val serviceName: String,
    val isOpen: Boolean,
    val latencyMs: Long = 0,
)
