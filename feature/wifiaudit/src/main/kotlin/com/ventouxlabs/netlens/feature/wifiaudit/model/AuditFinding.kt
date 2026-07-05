package com.ventouxlabs.netlens.feature.wifiaudit.model

import com.ventouxlabs.netlens.core.ui.UiText

data class AuditFinding(
    val id: String,
    val severity: AuditSeverity,
    val title: UiText,
    val description: UiText,
    val guidance: UiText,
)
