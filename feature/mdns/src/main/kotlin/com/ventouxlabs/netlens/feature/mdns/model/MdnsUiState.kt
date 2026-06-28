package com.ventouxlabs.netlens.feature.mdns.model

data class MdnsUiState(
    val services: List<MdnsService> = emptyList(),
    val isScanning: Boolean = false,
    val error: String? = null,
)
