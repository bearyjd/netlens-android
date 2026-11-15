package com.ventouxlabs.netlens.core.scan.network

import com.ventouxlabs.netlens.core.scan.model.LanDevice
import com.ventouxlabs.netlens.core.scan.model.identity.DeviceIdentifier

class FakeNetworkIdentity : NetworkIdentity {
    var gatewayMac: String? = null
    var gatewayIp: String? = null
    var subnet: String? = null
    var ssid: String? = null

    /** A strong identifier the device at [gatewayIp] offers even without a discovery pass (as a platform able to fetch one would). */
    var gatewayIdentifier: DeviceIdentifier? = null

    override suspend fun currentGatewayMac(): String? = gatewayMac
    override fun currentGatewayIp(): String? = gatewayIp
    override fun currentSubnet(): String? = subnet
    override fun currentSsid(): String? = ssid

    override suspend fun currentNetworkKeys(scanned: List<LanDevice>): List<NetworkKey> {
        val gateway = gatewayIdentifier?.let { id -> gatewayIp?.let { LanDevice(ip = it, identifiers = setOf(id)) } }
        return NetworkKeys.build(gatewayMac, gatewayIp, subnet, scanned + listOfNotNull(gateway))
    }
}
