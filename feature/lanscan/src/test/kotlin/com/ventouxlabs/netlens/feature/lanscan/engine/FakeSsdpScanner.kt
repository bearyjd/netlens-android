package com.ventouxlabs.netlens.feature.lanscan.engine

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import com.ventouxlabs.netlens.feature.lanscan.model.SsdpDevice

class FakeSsdpScanner : SsdpScanner {
    var devices: List<SsdpDevice> = emptyList()
    var error: Throwable? = null

    override fun discover(timeoutMs: Long): Flow<SsdpDevice> = flow {
        error?.let { throw it }
        devices.forEach { emit(it) }
    }
}
