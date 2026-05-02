package com.ventoux.netlens.widget.util

object DnsLeakDetector {

    fun isLeaking(
        dnsServers: List<String>,
        isVpn: Boolean,
        vpnInterfaceName: String,
        gatewayIp: String?,
    ): Boolean {
        if (!isVpn || vpnInterfaceName.isEmpty()) return false
        if (dnsServers.isEmpty()) return false
        return dnsServers.any { dns ->
            dns == gatewayIp ||
                dns.startsWith("192.168.") ||
                dns.startsWith("10.0.0.") ||
                dns.startsWith("172.16.") ||
                dns == "8.8.8.8" || dns == "8.8.4.4" ||
                dns == "1.1.1.1" || dns == "1.0.0.1"
        }
    }

    fun detectRoutingMode(
        isVpn: Boolean,
        hasVpnDefaultRoute: Boolean,
    ): String = when {
        !isVpn -> "DIRECT"
        hasVpnDefaultRoute -> "VPN_FULL"
        else -> "VPN_SPLIT"
    }
}
