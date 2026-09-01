package com.ventouxlabs.netlens.widget.util

data class ChipDefinition(val route: String, val shortLabel: String)

/**
 * Which [ToolDestination][com.ventouxlabs.netlens.navigation.ToolDestination] routes may appear
 * as widget shortcut chips, and their compact display labels.
 *
 * `:widget` cannot depend on `:app` (the dependency runs the other way), so route strings here
 * are duplicated from `ToolDestination.route` rather than shared — they must be kept in sync by
 * hand. [ELIGIBLE]'s declaration order is also the widget's chip display order: it is never
 * derived from the `Set<String>` the routes are selected from, to keep that order stable across
 * periodic widget refreshes.
 */
object ChipCatalog {
    val ELIGIBLE: List<ChipDefinition> = listOf(
        ChipDefinition("ping", "Ping"),
        ChipDefinition("speedtest", "Speed"),
        ChipDefinition("lanscan", "LAN"),
        ChipDefinition("dns", "DNS"),
        ChipDefinition("dnsleak", "DNS Leak"),
        ChipDefinition("vpnstatus", "VPN"),
        ChipDefinition("wifiaudit", "WiFi Audit"),
        ChipDefinition("ipinfo", "IP Info"),
        ChipDefinition("portscan", "Ports"),
        ChipDefinition("devices", "Devices"),
        ChipDefinition("whois", "WHOIS"),
    )

    const val MAX_WIDGET_CHIPS = 4
}
