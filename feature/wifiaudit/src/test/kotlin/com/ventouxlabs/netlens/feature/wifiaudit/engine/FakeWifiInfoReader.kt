package com.ventouxlabs.netlens.feature.wifiaudit.engine

import com.ventouxlabs.netlens.feature.wifiaudit.model.ConnectedNetworkInfo

class FakeWifiInfoReader : WifiInfoReader {

    var connectedResult: ConnectedNetworkInfo? = null
    var error: Throwable? = null

    override suspend fun readConnected(): ConnectedNetworkInfo? {
        error?.let { throw it }
        return connectedResult
    }
}
