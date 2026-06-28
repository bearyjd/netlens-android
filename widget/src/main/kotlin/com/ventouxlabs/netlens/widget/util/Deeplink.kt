package com.ventouxlabs.netlens.widget.util

object Deeplink {
    const val SCHEME = "netlens"
    const val HOST = "feature"

    const val HOME = "$SCHEME://$HOST/home"
    const val POSTURE = "$SCHEME://$HOST/posture"
    const val VPNSTATUS = "$SCHEME://$HOST/vpnstatus"
    const val WIFI_AUDIT = "$SCHEME://$HOST/wifiaudit"
    const val IPINFO = "$SCHEME://$HOST/ipinfo"
    const val SPEED_TEST = "$SCHEME://$HOST/speedtest"
    const val LATENCY = "$SCHEME://$HOST/ping"
    const val DEVICES = "$SCHEME://$HOST/lanscan"
    const val DNS = "$SCHEME://$HOST/dns"
    const val DNS_LEAK = "$SCHEME://$HOST/dnsleak"
    const val PORT_SCAN = "$SCHEME://$HOST/portscan"
    const val TRIGGER_SCAN = "$SCHEME://$HOST/scan"

    fun pingHost(host: String): String = "$LATENCY?host=$host"
    fun lanScanForDevice(ip: String): String = "$DEVICES?device=$ip"
    fun dnsWithServer(server: String): String = "$DNS?server=$server"
    fun issue(issueId: String): String = "$SCHEME://$HOST/issue/$issueId"
}
