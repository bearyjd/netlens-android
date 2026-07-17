package com.ventouxlabs.netlens.feature.devices

/**
 * Resolves the current network's identity. Gateway MAC + subnet come from
 * LinkProperties + ARP (no location permission). SSID is display-only and read
 * only in the foreground where ACCESS_FINE_LOCATION already exists.
 */
interface NetworkIdentity {
    suspend fun currentGatewayMac(): String?
    fun currentSubnet(): String?
    fun currentSsid(): String?
}
