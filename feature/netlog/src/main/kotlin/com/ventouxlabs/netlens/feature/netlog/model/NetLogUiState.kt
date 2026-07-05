package com.ventouxlabs.netlens.feature.netlog.model

import com.ventouxlabs.netlens.core.data.model.NetworkEvent
import com.ventouxlabs.netlens.core.ui.UiText

data class NetLogUiState(
    val events: List<NetworkEvent> = emptyList(),
    val isMonitoring: Boolean = false,
    val showClearConfirmation: Boolean = false,
    val error: UiText? = null,
    val selectedEventTypes: Set<String> = emptySet(),
    val dateRangeStartMs: Long? = null,
    val dateRangeEndMs: Long? = null,
)
