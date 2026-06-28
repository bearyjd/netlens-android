package com.ventouxlabs.netlens.feature.mdns.engine

import kotlinx.coroutines.flow.Flow
import com.ventouxlabs.netlens.feature.mdns.model.MdnsService

interface MdnsScanner {

    fun discoverServices(serviceType: String): Flow<MdnsService>

    fun stopDiscovery()
}
