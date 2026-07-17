package com.ventouxlabs.netlens.feature.devices

class FakeNetworkIdentity : NetworkIdentity {
    var gatewayMac: String? = null
    var subnet: String? = null
    var ssid: String? = null
    override suspend fun currentGatewayMac(): String? = gatewayMac
    override fun currentSubnet(): String? = subnet
    override fun currentSsid(): String? = ssid
}
