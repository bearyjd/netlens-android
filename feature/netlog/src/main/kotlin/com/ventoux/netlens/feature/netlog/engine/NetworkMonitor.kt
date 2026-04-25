package com.ventoux.netlens.feature.netlog.engine

import kotlinx.coroutines.flow.Flow
import com.ventoux.netlens.core.data.model.NetworkEvent

interface NetworkMonitor {
    fun observeNetworkChanges(): Flow<NetworkEvent>
}
