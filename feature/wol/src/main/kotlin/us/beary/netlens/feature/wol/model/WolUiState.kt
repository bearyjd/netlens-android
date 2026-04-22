package us.beary.netlens.feature.wol.model

import us.beary.netlens.core.data.model.WolTarget

data class WolUiState(
    val savedTargets: List<WolTarget> = emptyList(),
    val macInput: String = "",
    val broadcastIp: String = "255.255.255.255",
    val port: Int = 9,
    val lastSentStatus: String? = null,
    val showAddDialog: Boolean = false,
    val addLabel: String = "",
    val addMac: String = "",
    val error: String? = null,
)
