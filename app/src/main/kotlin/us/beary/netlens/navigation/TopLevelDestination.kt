package us.beary.netlens.navigation

import androidx.compose.material.icons.Icons
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
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.ui.graphics.vector.ImageVector

enum class TopLevelDestination(
    val route: String,
    val icon: ImageVector,
    val label: String,
) {
    IpInfo(route = "ipinfo", icon = Icons.Default.Language, label = "IP Info"),
    LanScan(route = "lanscan", icon = Icons.Default.Router, label = "LAN Scan"),
    PortScan(route = "portscan", icon = Icons.Default.Security, label = "Ports"),
    Dns(route = "dns", icon = Icons.Default.Dns, label = "DNS"),
    Ping(route = "ping", icon = Icons.Default.NetworkPing, label = "Ping"),
    Wol(route = "wol", icon = Icons.Default.Power, label = "WoL"),
    Tls(route = "tls", icon = Icons.Default.Lock, label = "TLS"),
    Whois(route = "whois", icon = Icons.Default.Search, label = "WHOIS"),
    HttpTester(route = "httptester", icon = Icons.Default.Http, label = "HTTP"),
    Mdns(route = "mdns", icon = Icons.Default.Wifi, label = "mDNS"),
    NetLog(route = "netlog", icon = Icons.Default.History, label = "Net Log"),
    Monitor(route = "monitor", icon = Icons.Default.Monitor, label = "Monitor"),
}
