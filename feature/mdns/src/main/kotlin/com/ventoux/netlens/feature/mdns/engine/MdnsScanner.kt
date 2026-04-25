package com.ventoux.netlens.feature.mdns.engine

import kotlinx.coroutines.flow.Flow
import com.ventoux.netlens.feature.mdns.model.MdnsService

interface MdnsScanner {

    fun discoverServices(serviceType: String): Flow<MdnsService>

    fun stopDiscovery()
}
