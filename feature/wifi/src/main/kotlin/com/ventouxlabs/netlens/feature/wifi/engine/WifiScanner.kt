package com.ventouxlabs.netlens.feature.wifi.engine

import kotlinx.coroutines.flow.Flow
import com.ventouxlabs.netlens.feature.wifi.model.ConnectedWifiInfo
import com.ventouxlabs.netlens.feature.wifi.model.WifiNetwork

interface WifiScanner {

    fun scan(): Flow<List<WifiNetwork>>

    fun observeConnected(): Flow<ConnectedWifiInfo?>
}
