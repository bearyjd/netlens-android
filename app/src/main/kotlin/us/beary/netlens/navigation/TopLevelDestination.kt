package us.beary.netlens.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.NetworkPing
import androidx.compose.material.icons.filled.Power
import androidx.compose.material.icons.filled.Router
import androidx.compose.material.icons.filled.Security
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
}
