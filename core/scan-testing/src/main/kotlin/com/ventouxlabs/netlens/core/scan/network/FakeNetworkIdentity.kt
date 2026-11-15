package com.ventouxlabs.netlens.core.scan.network

open class FakeNetworkIdentity : NetworkIdentity {
    var gatewayMac: String? = null
    var gatewayIp: String? = null
    var subnet: String? = null
    var ssid: String? = null

    /** Snapshots handed out first, in order, before reading the fields: lets a test move networks mid-scan. */
    val scriptedSnapshots: ArrayDeque<NetworkSnapshot> = ArrayDeque()

    /** Every [snapshot] call, in order. */
    val snapshotsTaken = mutableListOf<NetworkSnapshot>()

    override suspend fun currentGatewayMac(): String? = gatewayMac
    override fun currentGatewayIp(): String? = gatewayIp
    override fun currentSubnet(): String? = subnet
    override fun currentSsid(): String? = ssid

    override suspend fun snapshot(): NetworkSnapshot =
        (scriptedSnapshots.removeFirstOrNull() ?: super.snapshot()).also { snapshotsTaken += it }
}
