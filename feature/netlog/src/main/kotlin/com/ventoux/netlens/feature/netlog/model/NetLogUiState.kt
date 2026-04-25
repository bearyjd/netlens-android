package com.ventoux.netlens.feature.netlog.model

import com.ventoux.netlens.core.data.model.NetworkEvent

data class NetLogUiState(
    val events: List<NetworkEvent> = emptyList(),
    val isMonitoring: Boolean = false,
    val showClearConfirmation: Boolean = false,
    val error: String? = null,
)
