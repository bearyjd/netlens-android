package com.ventoux.netlens.feature.vpnstatus.model

import com.ventoux.netlens.core.network.VpnState

data class VpnStatusUiState(
    val isLoading: Boolean = true,
    val isOnline: Boolean = true,
    val vpnState: VpnState = VpnState.None,
)
