package com.ventouxlabs.netlens.widget.ui

import com.ventouxlabs.netlens.widget.util.ChipCatalog
import com.ventouxlabs.netlens.widget.util.ChipDefinition
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ToolChipsRowTest {

    @Test
    fun `no selected routes yields no chips`() {
        assertEquals(emptyList<ChipDefinition>(), resolveToolChips(emptyList()))
    }

    @Test
    fun `selecting speedtest returns the Speed chip`() {
        val chips = resolveToolChips(listOf("speedtest"))

        assertEquals(listOf(ChipDefinition("speedtest", "Speed")), chips)
    }

    @Test
    fun `a route not in the catalog is silently ignored`() {
        val chips = resolveToolChips(listOf("nonexistent-route"))

        assertEquals(emptyList<ChipDefinition>(), chips)
    }

    @Test
    fun `more than MAX_WIDGET_CHIPS selected keeps only the first four in catalog order`() {
        // Catalog order is: ..., vpnstatus, wifiaudit, ipinfo, portscan, devices, whois — six of
        // these are selected, so the first four in that order must survive, not the last four
        // (which would also happen to satisfy a weaker "still catalog-ordered" assertion).
        val chips = resolveToolChips(
            listOf("whois", "devices", "portscan", "ipinfo", "wifiaudit", "vpnstatus"),
        )

        assertEquals(ChipCatalog.MAX_WIDGET_CHIPS, chips.size)
        assertEquals(
            listOf("vpnstatus", "wifiaudit", "ipinfo", "portscan"),
            chips.map(ChipDefinition::route),
        )
    }

    @Test
    fun `result order always follows catalog order, never input order`() {
        // Deliberately scrambled/reversed relative to ChipCatalog.ELIGIBLE's declaration order.
        val scrambled = listOf("dnsleak", "dns", "speedtest", "ping")

        val chips = resolveToolChips(scrambled)

        val expectedOrder = ChipCatalog.ELIGIBLE.filter { it.route in scrambled.toSet() }
        assertEquals(expectedOrder, chips)
        assertEquals(listOf("ping", "speedtest", "dns", "dnsleak"), chips.map(ChipDefinition::route))
    }
}
