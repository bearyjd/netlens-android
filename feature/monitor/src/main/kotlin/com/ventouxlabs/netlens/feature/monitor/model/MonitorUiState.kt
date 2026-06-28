package com.ventouxlabs.netlens.feature.monitor.model

import com.ventouxlabs.netlens.core.data.model.EndpointCheck
import com.ventouxlabs.netlens.core.data.model.MonitoredEndpoint

data class MonitorUiState(
    val endpoints: List<MonitoredEndpoint> = emptyList(),
    val selectedEndpoint: MonitoredEndpoint? = null,
    val checks: List<EndpointCheck> = emptyList(),
    val isChecking: Boolean = false,
    val error: String? = null,
)
