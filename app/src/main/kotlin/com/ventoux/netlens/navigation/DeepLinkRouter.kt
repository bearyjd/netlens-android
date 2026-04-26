package com.ventoux.netlens.navigation

import android.content.Intent

private val PATH_TO_ROUTE = mapOf(
    "home" to "home",
    "ipinfo" to ToolDestination.IpInfo.route,
    "ping" to ToolDestination.Ping.route,
    "dns" to ToolDestination.Dns.route,
    "lanscan" to ToolDestination.LanScan.route,
    "portscan" to ToolDestination.PortScan.route,
    "traceroute" to ToolDestination.Traceroute.route,
    "whois" to ToolDestination.Whois.route,
    "tls" to ToolDestination.Tls.route,
    "httptester" to ToolDestination.HttpTester.route,
    "wol" to ToolDestination.Wol.route,
    "monitor" to ToolDestination.Monitor.route,
    "netlog" to ToolDestination.NetLog.route,
    "widgetsettings" to ToolDestination.WidgetSettings.route,
    // Widget deep links for screens that don't exist yet — fall back to home
    "posture" to "home",
    "wifiaudit" to "home",
    "speedtest" to "home",
    "latency" to ToolDestination.Ping.route,
    "scan" to "home",
)

internal fun resolveRoute(scheme: String?, host: String?, path: String?): String? {
    if (scheme != "netlens" || host != "feature") return null
    return path?.let { PATH_TO_ROUTE[it] }
}

fun Intent.resolveDeepLinkRoute(): String? {
    val uri = data ?: return null
    return resolveRoute(uri.scheme, uri.host, uri.pathSegments.firstOrNull())
}
