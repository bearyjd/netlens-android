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

    /** Gateway MAC, gateway IP and subnet as read right now; two snapshots tell whether the network changed. */
    suspend fun snapshot(): NetworkSnapshot = NetworkSnapshot(currentGatewayMac(), currentGatewayIp(), currentSubnet())

    fun currentNetworkLabel(): String? = NetworkKeys.label(currentSsid(), currentSubnet())
}

data class NetworkSnapshot(val gatewayMac: String?, val gatewayIp: String?, val subnet: String?) {
    /**
     * False when the phone moved networks between the two readings. The MAC is compared only when both
     * readings have one: the ARP table fills as a scan runs, so a missing MAC at the start is not a change.
     */
    fun sameNetworkAs(other: NetworkSnapshot): Boolean =
        gatewayIp == other.gatewayIp && subnet == other.subnet &&
            (gatewayMac == null || other.gatewayMac == null || gatewayMac == other.gatewayMac)

    fun keys(scanned: List<LanDevice>): List<NetworkKey> = NetworkKeys.build(gatewayMac, gatewayIp, subnet, scanned)
}
