package com.ventoux.netlens.feature.lanscan.engine

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import com.ventoux.netlens.feature.lanscan.model.LanDevice

class FakeSubnetScanner : SubnetScanner {
    var devices: List<LanDevice> = emptyList()
    var error: Throwable? = null

    override fun scan(subnet: String, prefixLength: Int): Flow<LanDevice> = flow {
        error?.let { throw it }
        devices.forEach { emit(it) }
    }
}
