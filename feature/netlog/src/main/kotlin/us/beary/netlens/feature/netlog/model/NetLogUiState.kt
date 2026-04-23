package us.beary.netlens.feature.netlog.model

import us.beary.netlens.core.data.model.NetworkEvent

data class NetLogUiState(
    val events: List<NetworkEvent> = emptyList(),
    val isMonitoring: Boolean = false,
    val showClearConfirmation: Boolean = false,
    val error: String? = null,
)
