package com.ventouxlabs.netlens.core.scan.engine

import kotlinx.coroutines.flow.Flow
import com.ventouxlabs.netlens.core.scan.model.PortResult

interface PortScanner {
    fun scan(
        host: String,
        ports: List<Int>,
        timeoutMs: Int = 1000,
    ): Flow<PortResult>
}
