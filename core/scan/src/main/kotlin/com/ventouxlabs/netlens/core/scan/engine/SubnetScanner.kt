package com.ventouxlabs.netlens.core.scan.engine

import kotlinx.coroutines.flow.Flow
import com.ventouxlabs.netlens.core.scan.model.LanDevice

/**
 * Scans a subnet for reachable devices, emitting discovered devices incrementally.
 */
interface SubnetScanner {
    fun scan(subnet: String, prefixLength: Int): Flow<LanDevice>
}
