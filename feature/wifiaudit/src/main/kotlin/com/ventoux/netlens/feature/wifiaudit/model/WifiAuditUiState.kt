package com.ventoux.netlens.feature.wifiaudit.model

data class WifiAuditUiState(
    val isAuditing: Boolean = false,
    val ssid: String? = null,
    val findings: List<AuditFinding> = emptyList(),
    val error: String? = null,
    val dismissedIds: Set<String> = emptySet(),
)
