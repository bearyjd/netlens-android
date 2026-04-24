package us.beary.netlens.feature.mdns.engine

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import us.beary.netlens.feature.mdns.model.MdnsService

class FakeMdnsScanner : MdnsScanner {
    private val _services = MutableSharedFlow<MdnsService>(extraBufferCapacity = 64)
    var error: Throwable? = null

    fun emit(service: MdnsService) {
        _services.tryEmit(service)
    }

    override fun discoverServices(serviceType: String): Flow<MdnsService> {
        error?.let { throw it }
        return _services.asSharedFlow()
    }

    override fun stopDiscovery() {}
}
