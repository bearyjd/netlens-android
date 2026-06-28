package com.ventouxlabs.netlens.feature.lanscan.model

import com.ventouxlabs.netlens.feature.portscan.model.PortResult

data class HostDetailState(
    val device: LanDevice,
    val portResults: List<PortResult> = emptyList(),
    val enrichedResults: List<HostPortResult> = emptyList(),
    val isScanning: Boolean = false,
    val progress: Float = 0f,
    val openCount: Int = 0,
    val enrichedType: String? = null,
    val enrichedOs: String? = null,
    val fingerprintEvidence: List<String> = emptyList(),
    val error: String? = null,
)
