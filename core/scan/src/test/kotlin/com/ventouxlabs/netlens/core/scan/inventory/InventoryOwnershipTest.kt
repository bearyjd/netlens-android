package com.ventouxlabs.netlens.core.scan.inventory

import com.ventouxlabs.netlens.core.data.model.KnownDeviceEntity
import com.ventouxlabs.netlens.core.scan.RecordingNewDeviceNotifier
import com.ventouxlabs.netlens.core.scan.identity.IdentityHarness
import com.ventouxlabs.netlens.core.scan.identity.MAC1
import com.ventouxlabs.netlens.core.scan.identity.MAC1_ID
import com.ventouxlabs.netlens.core.scan.identity.lan
import com.ventouxlabs.netlens.core.scan.identity.udn
import com.ventouxlabs.netlens.core.scan.identity.universalMac
import com.ventouxlabs.netlens.core.scan.model.identity.DeviceIdentifier
import com.ventouxlabs.netlens.core.scan.model.identity.IdentifierKind
import com.ventouxlabs.netlens.core.scan.model.identity.ObservationSource
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

/** Review hardening: unproven claims never gain ownership; junk MACs never key a row; protected fields stay put. */
class InventoryOwnershipTest {
    private val h = IdentityHarness()
    private val notifier = RecordingNewDeviceNotifier()
    private val repo = h.fakes.repository(notifier, autoMerge = true)
    private val sshKey = DeviceIdentifier(IdentifierKind.SSH_HOSTKEY_SHA256, "key1", ObservationSource.SSH, proven = true)

    @Test
    fun `an offline device's UDN plus a stranger's own proven key is a Conflict, never the device`() = runTest {
        h.seedRow(101, "10.0.0.10", ids = listOf(udn("uuid:v")), customName = "V", isKnown = true)
        val before = h.known.byId(101)!!

        repo.persistScan(listOf(lan("10.0.0.66", null, udn("uuid:v"), sshKey)), networkId = null)

        assertEquals(before, h.known.byId(101))
        assertEquals(listOf("uuid:v"), h.identifiers.getForDevice(101).map { it.value })
        assertEquals(listOf("10.0.0.66"), notifier.notified.map { it.ip })
        val stranger = h.known.allDevices.single { it.id != 101L }
        assertEquals(listOf("key1"), h.identifiers.getForDevice(stranger.id).map { it.value })
    }

    @Test
    fun `after that, the real device coming online still resolves to itself without a stranger alert`() = runTest {
        h.seedRow(101, "10.0.0.10", ids = listOf(udn("uuid:v")), customName = "V", isKnown = true)
        repo.persistScan(listOf(lan("10.0.0.66", null, udn("uuid:v"), sshKey)), networkId = null)
        notifier.notified.clear()

        repo.persistScan(listOf(lan("10.0.0.11", null, udn("uuid:v"))), networkId = null)

        assertEquals(2, h.known.allDevices.size)
        assertEquals(0, notifier.notified.size)
        assertEquals("10.0.0.11", h.known.byId(101)!!.ip)
        assertEquals("V", h.known.byId(101)!!.customName)
    }

    @Test
    fun `a claim-only match records only identifiers the row already owns`() = runTest {
        h.seedRow(101, "10.0.0.10", ids = listOf(udn("uuid:v")))

        // A local MAC and a medium-strength identifier are not enough to be a Conflict, but are not ownership either.
        repo.persistScan(listOf(lan("10.0.0.10", "02:BB:CC:00:00:09", udn("uuid:v"))), networkId = null)

        assertEquals(1, h.known.allDevices.size)
        assertEquals(listOf("uuid:v"), h.identifiers.getForDevice(101).map { it.value })
        assertNull(h.known.byId(101)!!.macAddress)
    }

    @Test
    fun `an Ambiguous attach records only identifiers the row already owns`() = runTest {
        h.seedRow(101, "10.0.0.1", ids = listOf(udn("uuid:a", proven = true)), firstSeen = 10, customName = "A", lastSeen = 50)
        h.seedRow(102, "10.0.0.2", ids = listOf(universalMac(MAC1_ID)), mac = MAC1, firstSeen = 5, notes = "B", lastSeen = 40)

        repo.persistScan(listOf(lan("10.0.0.9", MAC1, udn("uuid:a", proven = true), sshKey)), networkId = null)

        assertNull(h.identifiers.find("SSH_HOSTKEY_SHA256", "key1", ""))
    }

    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    fun `a device with a broadcast MAC scanned twice is one row and one alert`(autoMerge: Boolean) = runTest {
        val r = h.fakes.repository(notifier, autoMerge = autoMerge)

        repeat(2) { r.persistScan(listOf(lan("10.0.0.5", "FF:FF:FF:FF:FF:FF")), networkId = null) }

        assertEquals(1, h.known.allDevices.size)
        assertEquals(1, notifier.notified.size)
        assertNull(h.known.allDevices.single().macAddress)
    }

    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    fun `a rescan changes no protected field and no osConfidence it was not given`(isKnown: Boolean) = runTest {
        h.fakes.seedLegacyRow(
            KnownDeviceEntity(
                id = 1, macAddress = MAC1, hostname = "h", ip = "10.0.0.5", vendor = null, isKnown = isKnown,
                osGuess = "Linux", osConfidence = 70, fingerprintConfidence = 70,
                customName = "N", tags = "t", notes = "n", location = "L",
                deviceTypeOverride = "A", osOverride = "B", modelOverride = "C",
            ),
        )
        val before = h.known.byId(1)!!

        repo.persistScan(listOf(lan("10.0.0.5", MAC1).copy(deviceType = "Printer", fingerprintConfidence = 50)), networkId = null)

        val after = h.known.byId(1)!!
        assertEquals(
            listOf(before.isKnown, before.customName, before.tags, before.notes, before.location, before.deviceTypeOverride, before.osOverride, before.modelOverride, 70),
            listOf(after.isKnown, after.customName, after.tags, after.notes, after.location, after.deviceTypeOverride, after.osOverride, after.modelOverride, after.osConfidence),
        )
        assertTrue(after.lastSeen >= before.lastSeen)
    }
}
