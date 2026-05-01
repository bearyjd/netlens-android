package com.ventoux.netlens.feature.wifi.engine

import kotlinx.coroutines.flow.Flow
import com.ventoux.netlens.feature.wifi.model.ConnectedWifiInfo
import com.ventoux.netlens.feature.wifi.model.WifiNetwork

interface WifiScanner {

    fun scan(): Flow<List<WifiNetwork>>

    fun observeConnected(): Flow<ConnectedWifiInfo?>
}
