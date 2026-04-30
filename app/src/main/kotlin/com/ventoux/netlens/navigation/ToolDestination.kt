package com.ventoux.netlens.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.AltRoute
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Http
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Monitor
import androidx.compose.material.icons.filled.NetworkPing
import androidx.compose.material.icons.filled.Power
import androidx.compose.material.icons.filled.Router
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.ManageHistory
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.ui.graphics.vector.ImageVector

enum class ToolCategory(val label: String) {
    NetworkInfo("Network Info"),
    Connectivity("Connectivity"),
    Discovery("Discovery"),
    Security("Security & Web"),
    Tools("Tools"),
}

enum class ToolDestination(
    val route: String,
    val icon: ImageVector,
    val label: String,
    val description: String,
    val category: ToolCategory,
    val isVisibleInGrid: Boolean = true,
) {
    Posture(
        route = "posture",
        icon = Icons.Default.Shield,
        label = "Security Posture",
        description = "Network security score",
        category = ToolCategory.NetworkInfo,
        isVisibleInGrid = false,
    ),
    IpInfo(
        route = "ipinfo",
        icon = Icons.Default.Language,
        label = "IP Info",
        description = "Public IP & geolocation",
        category = ToolCategory.NetworkInfo,
    ),
    Whois(
        route = "whois",
        icon = Icons.Default.Search,
        label = "WHOIS",
        description = "Domain & IP ownership",
        category = ToolCategory.NetworkInfo,
    ),
    Ping(
        route = "ping",
        icon = Icons.Default.NetworkPing,
        label = "Ping",
        description = "Latency & reachability",
        category = ToolCategory.Connectivity,
    ),
    Dns(
        route = "dns",
        icon = Icons.Default.Dns,
        label = "DNS Lookup",
        description = "Query DNS records",
        category = ToolCategory.Connectivity,
    ),
    Traceroute(
        route = "traceroute",
        icon = Icons.AutoMirrored.Filled.AltRoute,
        label = "Traceroute",
        description = "Trace network path",
        category = ToolCategory.Connectivity,
    ),
    LanScan(
        route = "lanscan",
        icon = Icons.Default.Router,
        label = "LAN Scan",
        description = "Discover local devices",
        category = ToolCategory.Discovery,
    ),
    Mdns(
        route = "mdns",
        icon = Icons.Default.Wifi,
        label = "mDNS Browser",
        description = "Find .local services",
        category = ToolCategory.Discovery,
    ),
    PortScan(
        route = "portscan",
        icon = Icons.Default.Security,
        label = "Port Scanner",
        description = "Scan open ports",
        category = ToolCategory.Discovery,
    ),
    Tls(
        route = "tls",
        icon = Icons.Default.Lock,
        label = "TLS Inspector",
        description = "Inspect certificates",
        category = ToolCategory.Security,
    ),
    HttpTester(
        route = "httptester",
        icon = Icons.Default.Http,
        label = "HTTP Tester",
        description = "Send HTTP requests",
        category = ToolCategory.Security,
    ),
    Wol(
        route = "wol",
        icon = Icons.Default.Power,
        label = "Wake-on-LAN",
        description = "Wake network devices",
        category = ToolCategory.Tools,
    ),
    Monitor(
        route = "monitor",
        icon = Icons.Default.Monitor,
        label = "Endpoint Monitor",
        description = "Track uptime status",
        category = ToolCategory.Tools,
    ),
    IpCalc(
        route = "ipcalc",
        icon = Icons.Default.Calculate,
        label = "IP Calculator",
        description = "Subnet & CIDR calculator",
        category = ToolCategory.Tools,
    ),
    NetLog(
        route = "netlog",
        icon = Icons.Default.History,
        label = "Network Log",
        description = "Connection history",
        category = ToolCategory.Tools,
    ),
    History(
        route = "history",
        icon = Icons.Default.ManageHistory,
        label = "History",
        description = "Past scan results",
        category = ToolCategory.Tools,
    ),
    WidgetSettings(
        route = "widgetsettings",
        icon = Icons.Default.Widgets,
        label = "Widget Settings",
        description = "Customize home screen widget",
        category = ToolCategory.Tools,
        isVisibleInGrid = false,
    ),
    ;

    companion object {
        val byCategory: Map<ToolCategory, List<ToolDestination>> =
            entries.groupBy { it.category }
    }
}
