package com.ventouxlabs.netlens.feature.lanscan

import com.ventouxlabs.netlens.core.data.model.KnownDeviceEntity
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue

class InventoryGroupingTest {

    private fun device(id: Long, block: (KnownDeviceEntity) -> KnownDeviceEntity = { it }) =
        block(KnownDeviceEntity(id = id, macAddress = null, hostname = null, ip = "10.0.0.$id", vendor = null))

    private fun plain(id: Long) = device(id)
    private fun named(id: Long) = device(id) { it.copy(hostname = "host$id") }

    private fun group(devices: List<KnownDeviceEntity>, query: String = "") =
        groupInventory(devices, searchQuery = query)

    @Test
    fun `annotated-only devices are identified`() {
        listOf<(KnownDeviceEntity) -> KnownDeviceEntity>(
            { it.copy(customName = "Mine") },
            { it.copy(tags = "iot") },
            { it.copy(notes = "note") },
            { it.copy(location = "Garage") },
        ).forEachIndexed { i, f ->
            val g = group(listOf(device(i + 1L, f), plain(100)))
            assertEquals(listOf(i + 1L), g.identified.map { it.id })
        }
    }

    @Test
    fun `scan-derived and override-only fields are identified`() {
        listOf<(KnownDeviceEntity) -> KnownDeviceEntity>(
            { it.copy(hostname = "h") },
            { it.copy(vendor = "v") },
            { it.copy(deviceType = "Router") },
            { it.copy(deviceTypeOverride = "Router") },
            { it.copy(osOverride = "Linux") },
            { it.copy(osGuess = "Linux") },
            { it.copy(modelOverride = "X1") },
            { it.copy(model = "X1") },
            { it.copy(isKnown = true) },
        ).forEachIndexed { i, f ->
            val g = group(listOf(device(i + 1L, f), plain(100)))
            assertEquals(listOf(i + 1L), g.identified.map { it.id })
            assertEquals(listOf(100L), g.unidentified.map { it.id })
        }
    }

    @Test
    fun `blank and whitespace strings count as absent`() {
        val blank = device(1) {
            it.copy(
                customName = " ", hostname = "", vendor = "\t", deviceType = " ", osGuess = "",
                tags = " ", notes = "\n", location = " ", model = " ", modelOverride = "",
            )
        }
        val g = group(listOf(named(2), blank))
        assertEquals(listOf(1L), g.unidentified.map { it.id })
        assertEquals(listOf(2L), g.identified.map { it.id })
        assertTrue(g.grouped)
    }

    @Test
    fun `separator-only tags are not identified`() {
        val g = group(listOf(named(2), device(1) { it.copy(tags = ", ,") }))
        assertEquals(listOf(1L), g.unidentified.map { it.id })
        assertTrue(g.grouped)
    }

    @Test
    fun `blank override does not mask a detected value`() {
        listOf<(KnownDeviceEntity) -> KnownDeviceEntity>(
            { it.copy(deviceTypeOverride = " ", deviceType = "Router") },
            { it.copy(osOverride = "", osGuess = "Linux") },
            { it.copy(modelOverride = "\t", model = "X1") },
        ).forEachIndexed { i, f ->
            val g = group(listOf(device(i + 1L, f), plain(100)))
            assertEquals(listOf(i + 1L), g.identified.map { it.id })
        }
    }

    @Test
    fun `empty device list yields empty ungrouped result`() {
        val g = group(emptyList())
        assertTrue(g.identified.isEmpty())
        assertTrue(g.unidentified.isEmpty())
        assertFalse(g.grouped)
    }

    @Test
    fun `whitespace-only query is no filter and still groups`() {
        // The ViewModel's KnownDeviceSearch trims the query, so "  " matches every device.
        val g = group(listOf(named(1), plain(2)), query = "  ")
        assertTrue(g.grouped)
    }

    @Test
    fun `all unidentified is not grouped`() {
        val g = group(listOf(plain(1), plain(2)))
        assertFalse(g.grouped)
        assertEquals(listOf(1L, 2L), g.unidentified.map { it.id })
    }

    @Test
    fun `active search is not grouped`() {
        val g = group(listOf(named(1), plain(2)), query = "10.0")
        assertFalse(g.grouped)
    }

    @Test
    fun `order is preserved within each partition`() {
        val g = group(listOf(plain(5), named(4), plain(3), named(2), plain(1)))
        assertTrue(g.grouped)
        assertEquals(listOf(4L, 2L), g.identified.map { it.id })
        assertEquals(listOf(5L, 3L, 1L), g.unidentified.map { it.id })
    }

    @Test
    fun `no unidentified devices means no group`() {
        val g = group(listOf(named(1), named(2)))
        assertFalse(g.grouped)
        assertTrue(g.unidentified.isEmpty())
    }
}
