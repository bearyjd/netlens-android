package com.ventouxlabs.netlens.feature.lanscan

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Device-supplied host detail text is hostile: it must never reach a Text as a forged row. */
class HostDetailDisplayTest {

    @Test
    fun `evidence lines are flattened and blank lines dropped`() {
        val result = hostDetailEvidenceLines(
            listOf("Open port 80", "X\nFake Row", "\r\n", "", "   ", "a\u0000b\tc"),
        )
        assertEquals(listOf("Open port 80", "X Fake Row", "a b c"), result)
    }

    @Test
    fun `evidence lines never contain control characters`() {
        val result = hostDetailEvidenceLines(listOf("one\r\ntwo\u0007three\u0085four"))
        assertEquals(listOf("one two three four"), result)
    }

    @Test
    fun `chip labels flatten each value and omit blanks`() {
        val labels = hostDetailChipLabels(
            type = "Router\nFake Row",
            os = "\r\n",
            vendor = "Acme\u0000Corp",
            model = null,
        )
        assertEquals(listOf("Router Fake Row", "Acme Corp"), labels)
    }

    @Test
    fun `chip labels are empty when nothing is supplied`() {
        assertEquals(emptyList<String>(), hostDetailChipLabels(null, null, null, null))
    }

    @Test
    fun `service names strip affixes after flattening and tolerate forged newlines`() {
        val result = deviceCardServiceNames(listOf("_http._tcp", "_http\n._tcp", "_ssh\u0000._udp", ".airplay."))
        assertEquals(listOf("http", "http", "ssh", "airplay"), result)
        assertTrue(result.none { name -> name.any { it.isISOControl() } })
    }

    @Test
    fun `service names drop blank entries so the overflow count stays right`() {
        val result = deviceCardServiceNames(listOf("", "\r\n", "_._tcp", "_a._tcp", "_b._tcp", "_c._tcp", "_d._tcp"))
        assertEquals(listOf("a", "b", "c", "d"), result)
        assertEquals(1, result.size - 3)
    }

    @Test
    fun `inventory title prefers custom name then hostname then ip`() {
        assertEquals(InventoryTitle("Den", true), inventoryTitle("Den", "host", "10.0.0.2"))
        assertEquals(InventoryTitle("host", true), inventoryTitle(null, "host", "10.0.0.2"))
    }

    @Test
    fun `blank custom name falls through to hostname`() {
        assertEquals(InventoryTitle("host", true), inventoryTitle("\n  ", "host", "10.0.0.2"))
    }

    @Test
    fun `both blank shows ip as title without a duplicate ip row`() {
        assertEquals(InventoryTitle("10.0.0.2", false), inventoryTitle("\r\n", "  ", "10.0.0.2"))
        assertEquals(InventoryTitle("10.0.0.2", false), inventoryTitle(null, null, "10.0.0.2"))
    }
}
