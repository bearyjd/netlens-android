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
                dns.startsWith("10.0.1.") ||
                isRfc1918_172(dns) ||
                dns == "8.8.8.8" || dns == "8.8.4.4" ||
                dns == "1.1.1.1" || dns == "1.0.0.1"
        }
    }

    private fun isRfc1918_172(ip: String): Boolean {
        if (!ip.startsWith("172.")) return false
        val second = ip.split(".").getOrNull(1)?.toIntOrNull() ?: return false
        return second in 16..31
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
