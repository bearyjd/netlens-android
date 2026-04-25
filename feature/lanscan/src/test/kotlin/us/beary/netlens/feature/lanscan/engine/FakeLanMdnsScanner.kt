package us.beary.netlens.feature.lanscan.engine

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import us.beary.netlens.feature.lanscan.model.LanDevice

class FakeLanMdnsScanner : LanMdnsScanner {
    var devices: List<LanDevice> = emptyList()
    var error: Throwable? = null

    override fun discover(timeoutMs: Long): Flow<LanDevice> = flow {
        error?.let { throw it }
        devices.forEach { emit(it) }
    }
}
