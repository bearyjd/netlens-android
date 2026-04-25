package us.beary.netlens.feature.lanscan.engine

import kotlinx.coroutines.flow.Flow
import us.beary.netlens.feature.lanscan.model.LanDevice

interface LanMdnsScanner {
    fun discover(timeoutMs: Long = 5000): Flow<LanDevice>
}
