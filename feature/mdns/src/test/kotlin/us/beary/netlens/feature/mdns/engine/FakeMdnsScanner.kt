package us.beary.netlens.feature.mdns.engine

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import us.beary.netlens.feature.mdns.model.MdnsService

class FakeMdnsScanner : MdnsScanner {
    val channel = Channel<MdnsService>(Channel.UNLIMITED)
    var error: Throwable? = null

    override fun discoverServices(serviceType: String): Flow<MdnsService> {
        error?.let { throw it }
        return channel.receiveAsFlow()
    }

    override fun stopDiscovery() {
        channel.close()
    }
}
