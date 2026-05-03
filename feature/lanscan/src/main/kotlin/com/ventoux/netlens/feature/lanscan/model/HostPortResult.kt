package com.ventoux.netlens.feature.lanscan.model

import com.ventoux.netlens.feature.portscan.model.PortRiskLevel
import kotlinx.serialization.Serializable

@Serializable
data class HostPortResult(
    val port: Int,
    val protocol: String = "TCP",
    val serviceName: String,
    val isOpen: Boolean,
    val latencyMs: Long,
    val riskLevel: PortRiskLevel,
    val description: String,
)
