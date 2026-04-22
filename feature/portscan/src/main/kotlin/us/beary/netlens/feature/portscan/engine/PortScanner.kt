package us.beary.netlens.feature.portscan.engine

import kotlinx.coroutines.flow.Flow
import us.beary.netlens.feature.portscan.model.PortResult

interface PortScanner {
    fun scan(
        host: String,
        ports: List<Int>,
        timeoutMs: Int = 1000,
    ): Flow<PortResult>
}
