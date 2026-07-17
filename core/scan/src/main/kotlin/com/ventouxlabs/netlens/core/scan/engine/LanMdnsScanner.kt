package com.ventouxlabs.netlens.core.scan.engine

import kotlinx.coroutines.flow.Flow
import com.ventouxlabs.netlens.core.scan.model.LanDevice

interface LanMdnsScanner {
    fun discover(timeoutMs: Long = 5000): Flow<LanDevice>
}
