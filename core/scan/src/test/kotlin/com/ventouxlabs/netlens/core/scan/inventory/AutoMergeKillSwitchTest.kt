package com.ventouxlabs.netlens.core.scan.inventory

import com.ventouxlabs.netlens.core.data.preferences.UserPreferencesRepository
import com.ventouxlabs.netlens.core.data.testing.FakeDataStore
import com.ventouxlabs.netlens.core.data.testing.FakeKeyValueStore
import com.ventouxlabs.netlens.core.scan.DefaultAutoMergeSetting
import com.ventouxlabs.netlens.core.scan.RecordingNewDeviceNotifier
import com.ventouxlabs.netlens.core.scan.identity.IdentityHarness
import com.ventouxlabs.netlens.core.scan.identity.MAC1
import com.ventouxlabs.netlens.core.scan.identity.MAC1_ID
import com.ventouxlabs.netlens.core.scan.identity.lan
import com.ventouxlabs.netlens.core.scan.identity.udn
import com.ventouxlabs.netlens.core.scan.identity.universalMac
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** The `autoMergeDevices` preference drives the resolver per scan; off is the legacy path and loses nothing. */
class AutoMergeKillSwitchTest {
    private val h = IdentityHarness()
    private val notifier = RecordingNewDeviceNotifier()
    private val prefs = UserPreferencesRepository(FakeDataStore(), FakeKeyValueStore())
    private val repo = h.fakes.repository(
        notifier,
        autoMerge = false,
        autoMergeSetting = DefaultAutoMergeSetting(prefs),
    )
    private val scan = listOf(lan("10.0.0.9", MAC1, udn("uuid:a", proven = true)))

    private suspend fun seedMergeablePair() {
        h.seedRow(101, "10.0.0.1", ids = listOf(udn("uuid:a", proven = true)), firstSeen = 10)
        h.seedRow(102, "10.0.0.2", ids = listOf(universalMac(MAC1_ID)), mac = MAC1, firstSeen = 5)
    }

    @Test
    fun `the preference defaults to on and the setting reads it`() = runTest {
        assertTrue(prefs.autoMergeDevices.first())
        assertTrue(DefaultAutoMergeSetting(prefs).isEnabled())
    }

    @Test
    fun `with the preference off a scan neither merges nor suggests nor alerts`() = runTest {
        seedMergeablePair()
        prefs.setAutoMergeDevices(false)

        repo.persistScan(scan, networkId = null)

        assertEquals(2, h.known.allDevices.size)
        assertEquals(0, notifier.notified.size)
        assertTrue(repo.pendingMergeSuggestions.first().isEmpty())
    }

    @Test
    fun `with the preference off identifiers are still recorded for a new device`() = runTest {
        prefs.setAutoMergeDevices(false)

        repo.persistScan(listOf(lan("10.0.0.3", MAC1, udn("uuid:x"))), networkId = null)

        val id = h.known.allDevices.single().id
        assertEquals(setOf(MAC1_ID, "uuid:x"), h.identifiers.getForDevice(id).map { it.value }.toSet())
    }

    @Test
    fun `turning the preference back on merges on the next scan with no data lost`() = runTest {
        seedMergeablePair()
        prefs.setAutoMergeDevices(false)
        repo.persistScan(scan, networkId = null)
        assertEquals(2, h.known.allDevices.size)

        prefs.setAutoMergeDevices(true)
        repo.persistScan(scan, networkId = null)

        assertNull(h.known.byId(101))
        assertNotNull(h.known.byId(102))
        assertEquals(setOf("uuid:a", MAC1_ID), h.identifiers.getForDevice(102).map { it.value }.toSet())
    }
}
