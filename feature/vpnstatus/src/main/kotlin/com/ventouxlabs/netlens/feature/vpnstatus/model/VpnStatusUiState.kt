package com.ventouxlabs.netlens.feature.vpnstatus.model

import com.ventouxlabs.netlens.core.network.VpnState

data class VpnStatusUiState(
    val isLoading: Boolean = true,
    val isOnline: Boolean = true,
    val vpnState: VpnState = VpnState.None,
)
