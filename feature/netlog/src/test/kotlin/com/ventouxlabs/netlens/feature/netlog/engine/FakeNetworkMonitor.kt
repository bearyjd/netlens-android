package com.ventouxlabs.netlens.feature.netlog.engine

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import com.ventouxlabs.netlens.core.data.model.NetworkEvent

class FakeNetworkMonitor : NetworkMonitor {
    val channel = Channel<NetworkEvent>(Channel.UNLIMITED)

    override fun observeNetworkChanges(): Flow<NetworkEvent> = channel.receiveAsFlow()
}
