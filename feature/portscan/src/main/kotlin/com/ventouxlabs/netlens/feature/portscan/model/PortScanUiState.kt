package com.ventouxlabs.netlens.feature.portscan.model

data class PortScanUiState(
    val host: String = "",
    val results: List<PortResult> = emptyList(),
    val isScanning: Boolean = false,
    val progress: Float = 0f,
    val openCount: Int = 0,
    val error: String? = null,
)
