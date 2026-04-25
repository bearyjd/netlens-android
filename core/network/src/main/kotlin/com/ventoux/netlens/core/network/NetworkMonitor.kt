package com.ventoux.netlens.core.network

import kotlinx.coroutines.flow.Flow

interface NetworkMonitor {
    val isOnline: Flow<Boolean>
    val isVpnActive: Flow<Boolean>
}
