package com.ventouxlabs.netlens.feature.lanscan.model

data class LanScanHistoryUiModel(
    val id: Long,
    val timestamp: Long,
    val subnet: String?,
    val deviceCount: Int,
    val devicesJson: String = "[]",
    val latitude: Double? = null,
    val longitude: Double? = null,
)
