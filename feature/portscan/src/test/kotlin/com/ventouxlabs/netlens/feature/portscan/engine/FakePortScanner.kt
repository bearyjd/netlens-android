package com.ventouxlabs.netlens.feature.portscan.engine

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import com.ventouxlabs.netlens.feature.portscan.model.PortResult

class FakePortScanner : PortScanner {
    var results: List<PortResult> = emptyList()
    var error: Throwable? = null

    override fun scan(host: String, ports: List<Int>, timeoutMs: Int): Flow<PortResult> = flow {
        error?.let { throw it }
        results.forEach { emit(it) }
    }
}
