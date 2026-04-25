package com.ventoux.netlens.feature.portscan.engine

import kotlinx.coroutines.flow.Flow
import com.ventoux.netlens.feature.portscan.model.PortResult

interface PortScanner {
    fun scan(
        host: String,
        ports: List<Int>,
        timeoutMs: Int = 1000,
    ): Flow<PortResult>
}
