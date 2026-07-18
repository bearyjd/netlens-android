package com.ventouxlabs.netlens.feature.devices

import com.ventouxlabs.netlens.core.scan.engine.SubnetScanner
import com.ventouxlabs.netlens.core.scan.model.LanDevice
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class FakeSubnetScanner : SubnetScanner {
    var devices: List<LanDevice> = emptyList()
    override fun scan(subnet: String, prefixLength: Int): Flow<LanDevice> = flow { devices.forEach { emit(it) } }
}
