package com.ventouxlabs.netlens.feature.wifiaudit.model

data class AuditFinding(
    val id: String,
    val severity: AuditSeverity,
    val title: String,
    val description: String,
    val guidance: String,
)
