package com.ventouxlabs.netlens.core.scan.inventory

import com.ventouxlabs.netlens.core.scan.RecordingNewDeviceNotifier
import com.ventouxlabs.netlens.core.scan.identity.IdentityHarness
import com.ventouxlabs.netlens.core.scan.identity.MAC1
import com.ventouxlabs.netlens.core.scan.identity.MAC1_ID
import com.ventouxlabs.netlens.core.scan.identity.lan
import com.ventouxlabs.netlens.core.scan.identity.udn
import com.ventouxlabs.netlens.core.scan.identity.universalMac
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/** NewDeviceNotifier fires only for a newly inserted New or Conflict row: never Existing, merges or Ambiguous. */
class InventoryNotifyTest {
    private val h = IdentityHarness()
    private val notifier = RecordingNewDeviceNotifier()
    private val repo = h.fakes.repository(notifier, autoMerge = true)

    private suspend fun seedIncumbent() =
        h.seedRow(101, "10.0.0.10", ids = listOf(universalMac(MAC1_ID), udn("uuid:x")), mac = MAC1)

    private val incumbent = lan("10.0.0.10", MAC1, udn("uuid:x"))

    @Test
    fun `a new device notifies once and its rescan does not`() = runTest {
        repo.persistScan(listOf(lan("10.0.0.3", MAC1)), networkId = null)
        repo.persistScan(listOf(lan("10.0.0.3", MAC1)), networkId = null)

        assertEquals(1, notifier.notified.size)
        assertEquals(h.known.allDevices.single().id, notifier.notified.single().id)
    }

    @Test
    fun `a newly inserted Conflict row notifies once and the incumbent does not`() = runTest {
        seedIncumbent()

        repo.persistScan(listOf(incumbent, lan("10.0.0.20", null, udn("uuid:x"))), networkId = null)

        assertEquals(listOf("10.0.0.20"), notifier.notified.map { it.ip })
        assertEquals(2, h.known.allDevices.size)
    }

    @Test
    fun `rescanning a known Conflict pair does not notify again`() = runTest {
        seedIncumbent()
        val scan = listOf(incumbent, lan("10.0.0.20", null, udn("uuid:x")))
        repo.persistScan(scan, networkId = null)
        notifier.notified.clear()

        repo.persistScan(scan, networkId = null)

        assertEquals(0, notifier.notified.size)
        assertEquals(2, h.known.allDevices.size)
    }

    @Test
    fun `an automatic merge does not notify`() = runTest {
        h.seedRow(101, "10.0.0.1", ids = listOf(udn("uuid:a", proven = true)), firstSeen = 10)
        h.seedRow(102, "10.0.0.2", ids = listOf(universalMac(MAC1_ID)), mac = MAC1, firstSeen = 5)

        repo.persistScan(listOf(lan("10.0.0.9", MAC1, udn("uuid:a", proven = true))), networkId = null)

        assertEquals(0, notifier.notified.size)
        assertNull(h.known.byId(101))
        assertNotNull(h.known.byId(102))
    }

    @Test
    fun `an Ambiguous device attaches to a row and does not notify`() = runTest {
        h.seedRow(101, "10.0.0.1", ids = listOf(udn("uuid:a", proven = true)), firstSeen = 10, customName = "A", lastSeen = 50)
        h.seedRow(102, "10.0.0.2", ids = listOf(universalMac(MAC1_ID)), mac = MAC1, firstSeen = 5, notes = "B", lastSeen = 40)

        repo.persistScan(listOf(lan("10.0.0.9", MAC1, udn("uuid:a", proven = true))), networkId = null)

        assertEquals(0, notifier.notified.size)
        assertEquals(2, h.known.allDevices.size)
        assertEquals("10.0.0.9", h.known.byId(101)!!.ip)
    }

    @Test
    fun `with the kill switch off a claimed identifier alerts as a plain new device and is never owned`() = runTest {
        seedIncumbent()
        val off = h.fakes.repository(notifier, autoMerge = false)

        off.persistScan(listOf(incumbent, lan("10.0.0.20", null, udn("uuid:x"))), networkId = null)

        assertEquals(listOf("10.0.0.20"), notifier.notified.map { it.ip })
        assertEquals(101L, h.identifiers.find("UPNP_UDN", "uuid:x", "")?.deviceId)
    }

    @Test
    fun `with the kill switch off, rows a sighting could have merged are left alone and nothing alerts`() = runTest {
        h.seedRow(101, "10.0.0.1", ids = listOf(udn("uuid:a", proven = true)), firstSeen = 10)
        h.seedRow(102, "10.0.0.2", ids = listOf(universalMac(MAC1_ID)), mac = MAC1, firstSeen = 5)
        val off = h.fakes.repository(notifier, autoMerge = false)

        off.persistScan(listOf(lan("10.0.0.9", MAC1, udn("uuid:a", proven = true))), networkId = null)

        assertEquals(2, h.known.allDevices.size)
        assertEquals(0, notifier.notified.size)
    }
}
