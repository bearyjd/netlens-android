package com.ventouxlabs.netlens.core.scan.network

import com.ventouxlabs.netlens.core.scan.model.LanDevice

/**
 * Resolves the current network's identity. Gateway MAC + subnet come from LinkProperties + ARP (no
 * location permission). SSID is a display label only, read in the foreground where
 * ACCESS_FINE_LOCATION already exists.
 */
interface NetworkIdentity {
    suspend fun currentGatewayMac(): String?
    fun currentGatewayIp(): String?
    fun currentSubnet(): String?
    fun currentSsid(): String?

    /**
     * The network's keys, strongest first. Pass the devices found so far to let a strong identifier
     * of the device at the gateway IP become a `gw-id` key in the same scan; a lite background sweep
     * has none, so without ARP it can only produce the `l3` hint.
     */
    suspend fun currentNetworkKeys(scanned: List<LanDevice> = emptyList()): List<NetworkKey> =
        NetworkKeys.build(currentGatewayMac(), currentGatewayIp(), currentSubnet(), scanned)

    fun currentNetworkLabel(): String? = NetworkKeys.label(currentSsid(), currentSubnet())
}
