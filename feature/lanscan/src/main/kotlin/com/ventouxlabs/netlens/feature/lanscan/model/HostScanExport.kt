package com.ventouxlabs.netlens.feature.lanscan.model

import kotlinx.serialization.Serializable

@Serializable
data class HostScanExport(
    val host: String,
    val hostname: String?,
    val macAddress: String?,
    val vendor: String?,
    val scanTimestamp: Long,
    val totalPortsScanned: Int,
    val openPorts: Int,
    val results: List<HostPortResult>,
)
