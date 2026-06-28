package com.ventouxlabs.netlens.navigation

import android.content.Intent
import android.net.Uri

private val PATH_TO_ROUTE = mapOf(
    "home" to "home",
    "ipinfo" to ToolDestination.IpInfo.route,
    "ping" to ToolDestination.Ping.route,
    "dns" to ToolDestination.Dns.route,
    "dnsleak" to ToolDestination.DnsLeak.route,
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
    "posture" to ToolDestination.Posture.route,
    "vpnstatus" to ToolDestination.VpnStatus.route,
    "wifiaudit" to "home",
    "speedtest" to "home",
    "latency" to ToolDestination.Ping.route,
    "scan" to "home",
)

// Routes whose NavHost entry is declared as "<route>?query={query}". Only these
// accept a deep-link query value — other routes get the bare route and the
// query is dropped silently.
private val ROUTES_WITH_QUERY = setOf(
    ToolDestination.LanScan.route,
    ToolDestination.PortScan.route,
    ToolDestination.Dns.route,
    ToolDestination.Ping.route,
    ToolDestination.Traceroute.route,
    ToolDestination.Tls.route,
    ToolDestination.Whois.route,
    ToolDestination.HttpTester.route,
    ToolDestination.IpCalc.route,
)

internal fun resolveRoute(
    scheme: String?,
    host: String?,
    path: String?,
    query: String? = null,
): String? {
    if (scheme != "netlens" || host != "feature") return null
    val baseRoute = path?.let { PATH_TO_ROUTE[it] } ?: return null
    val trimmed = query?.takeIf { it.isNotEmpty() }
    return if (trimmed != null && baseRoute in ROUTES_WITH_QUERY) {
        "$baseRoute?query=${Uri.encode(trimmed)}"
    } else {
        baseRoute
    }
}

fun Intent.resolveDeepLinkRoute(): String? {
    val uri = data ?: return null
    // Widget deep-link helpers use semantic keys (?host=…, ?device=…, ?server=…).
    // Pick the first param's value — the destination's NavHost entry consumes it
    // as a single "query" arg regardless of source key.
    val queryValue = uri.queryParameterNames.firstOrNull()?.let { uri.getQueryParameter(it) }
    return resolveRoute(uri.scheme, uri.host, uri.pathSegments.firstOrNull(), queryValue)
}
