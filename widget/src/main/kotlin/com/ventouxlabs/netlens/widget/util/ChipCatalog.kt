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

    /**
     * Hard ceiling of 4 — **raising this silently breaks two widgets**.
     *
     * Glance ships pre-generated container layouts for 0..10 children and no further
     * (verified in `glance-appwidget-1.1.1.aar`); a container with more renders only
     * its first ten, with no crash, no log, and nothing a JVM test can observe. Both
     * full-width chip rows are laid out as `Portal + n x (spacer + chip)`, so they sit
     * at `1 + 2*4 = 9` children. At 5 chips they emit 11 and each row silently loses
     * its last two chips.
     *
     * If more chips are ever needed, the row has to change shape first — drop the
     * spacers for padding on the chips, or wrap in a nested container (each nested
     * container gets its own budget of ten).
     */
    const val MAX_WIDGET_CHIPS = 4
}
