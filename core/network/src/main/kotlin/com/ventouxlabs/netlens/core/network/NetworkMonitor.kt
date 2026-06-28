package com.ventouxlabs.netlens.core.network

import kotlinx.coroutines.flow.Flow

interface NetworkMonitor {
    val isOnline: Flow<Boolean>
    val vpnState: Flow<VpnState>
}
