package com.ventouxlabs.netlens.feature.portscan.model

import kotlinx.serialization.Serializable

@Serializable
enum class PortRiskLevel(val label: String, val sortOrder: Int) {
    CRITICAL("Critical", 0),
    WARNING("Open", 1),
    INFO("Info", 2),
    CLOSED("Closed", 3),
}
