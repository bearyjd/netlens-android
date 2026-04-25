package com.ventoux.netlens.feature.monitor.model

import com.ventoux.netlens.core.data.model.EndpointCheck
import com.ventoux.netlens.core.data.model.MonitoredEndpoint

data class MonitorUiState(
    val endpoints: List<MonitoredEndpoint> = emptyList(),
    val selectedEndpoint: MonitoredEndpoint? = null,
    val checks: List<EndpointCheck> = emptyList(),
    val isChecking: Boolean = false,
    val error: String? = null,
)
