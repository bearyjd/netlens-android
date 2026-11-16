package com.ventouxlabs.netlens.core.scan.network

import com.ventouxlabs.netlens.core.scan.engine.ArpTableReader
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Reads the gateway and subnet of the network the process is bound to, then of the LAN (Wi-Fi or
 * Ethernet) network, and only then of the system default: under a VPN the default network is the
 * tunnel, whose gateway says nothing about the LAN the scan actually ran on. The LAN step matters
 * when a VPN that does not allow bypass makes the scan's binding fail and nothing is bound.
 */
@Singleton
class NetworkIdentityImpl @Inject constructor(
    private val links: NetworkLinkReader,
    private val arpTableReader: ArpTableReader,
) : NetworkIdentity {

    private fun link(): LinkInfo? = links.boundLink() ?: links.lanLink() ?: links.activeLink()

    override suspend fun currentGatewayMac(): String? {
        val gatewayIp = currentGatewayIp() ?: return null
        arpTableReader.invalidateCache()
        return arpTableReader.getMacForIp(gatewayIp)
    }

    override fun currentGatewayIp(): String? = link()?.gatewayIp

    override fun currentSubnet(): String? = link()?.subnet

    override fun currentSsid(): String? = links.ssid()
}
