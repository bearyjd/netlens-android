package us.beary.netlens.feature.netlog.engine

import kotlinx.coroutines.flow.Flow
import us.beary.netlens.core.data.model.NetworkEvent

interface NetworkMonitor {
    fun observeNetworkChanges(): Flow<NetworkEvent>
}
