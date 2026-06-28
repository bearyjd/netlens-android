package com.ventouxlabs.netlens.feature.netlog.engine

import kotlinx.coroutines.flow.Flow
import com.ventouxlabs.netlens.core.data.model.NetworkEvent

interface NetworkMonitor {
    fun observeNetworkChanges(): Flow<NetworkEvent>
}
