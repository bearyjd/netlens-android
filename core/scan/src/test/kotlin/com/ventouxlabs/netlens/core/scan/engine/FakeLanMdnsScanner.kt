package com.ventouxlabs.netlens.core.scan.engine

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import com.ventouxlabs.netlens.core.scan.model.LanDevice

class FakeLanMdnsScanner : LanMdnsScanner {
    var devices: List<LanDevice> = emptyList()
    var error: Throwable? = null

    override fun discover(timeoutMs: Long): Flow<LanDevice> = flow {
        error?.let { throw it }
        devices.forEach { emit(it) }
    }
}
