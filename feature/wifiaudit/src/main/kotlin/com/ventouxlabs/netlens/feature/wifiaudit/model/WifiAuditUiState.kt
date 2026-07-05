package com.ventouxlabs.netlens.feature.wifiaudit.model

import com.ventouxlabs.netlens.core.ui.UiText

data class WifiAuditUiState(
    val isAuditing: Boolean = false,
    val ssid: String? = null,
    val findings: List<AuditFinding> = emptyList(),
    val error: UiText? = null,
    val dismissedIds: Set<String> = emptySet(),
)
