package com.ventouxlabs.netlens.core.scan.inventory

import com.ventouxlabs.netlens.core.data.model.KnownDeviceEntity
import com.ventouxlabs.netlens.core.scan.testing.InventoryFakes
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/** The fixture must mirror MIGRATION_18_19's backfill, or tests pass against rows production never has. */
class SeedLegacyRowTest {
    private fun row(mac: String?) = KnownDeviceEntity(
        id = 1, macAddress = mac, hostname = null, ip = "10.0.0.1", vendor = null, firstSeen = 7, lastSeen = 9,
    )

    @Test
    fun `colons and dashes are stripped, the value lowercased, and the row's timestamps kept`() = runTest {
        val fakes = InventoryFakes()
        fakes.seedLegacyRow(row("00-BB-CC-00-00-01"))

        val id = fakes.identifiers.getForDevice(1).single()
        assertEquals("00bbcc000001", id.value)
        assertEquals("MAC_UNIVERSAL", id.kind)
        assertEquals(listOf(7L, 9L), listOf(id.firstSeen, id.lastSeen))
    }

    @Test
    fun `anything that is not exactly 12 hex digits is skipped without crashing`() = runTest {
        for (junk in listOf("---", "", "zz:zz:zz:zz:zz:zz", "00:11:22", "00:11:22:33:44:55:66")) {
            val fakes = InventoryFakes()
            fakes.seedLegacyRow(row(junk))
            assertEquals(0, fakes.identifiers.all.size, junk)
        }
    }
}
