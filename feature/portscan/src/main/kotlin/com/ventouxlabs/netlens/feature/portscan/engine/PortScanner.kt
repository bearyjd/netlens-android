package com.ventouxlabs.netlens.feature.portscan.engine

import kotlinx.coroutines.flow.Flow
import com.ventouxlabs.netlens.feature.portscan.model.PortResult

interface PortScanner {
    fun scan(
        host: String,
        ports: List<Int>,
        timeoutMs: Int = 1000,
    ): Flow<PortResult>
}
