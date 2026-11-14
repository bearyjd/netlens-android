package com.ventouxlabs.netlens.core.scan.inventory

import com.ventouxlabs.netlens.core.data.model.DeviceSightingEntity
import com.ventouxlabs.netlens.core.data.model.KnownDeviceEntity
import com.ventouxlabs.netlens.core.scan.RecordingNewDeviceNotifier
import com.ventouxlabs.netlens.core.scan.chosenMac
import com.ventouxlabs.netlens.core.scan.identity.IdentityHarness
import com.ventouxlabs.netlens.core.scan.identity.MAC1
import com.ventouxlabs.netlens.core.scan.identity.MAC1_ID
import com.ventouxlabs.netlens.core.scan.identity.MAC2
import com.ventouxlabs.netlens.core.scan.identity.MAC_LOCAL_RAW
import com.ventouxlabs.netlens.core.scan.identity.lan
import com.ventouxlabs.netlens.core.scan.identity.udn
import com.ventouxlabs.netlens.core.scan.identity.universalMac
import com.ventouxlabs.netlens.core.scan.model.identity.Observation
import com.ventouxlabs.netlens.core.scan.model.identity.ObservationSource
import com.ventouxlabs.netlens.core.scan.model.identity.ObservedField
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** What persistScan writes for a device it has resolved: columns, confidences, identifiers, evidence, sightings. */
class InventoryPersistTest {
    private val h = IdentityHarness()
    private val notifier = RecordingNewDeviceNotifier()
    private val repo = h.fakes.repository(notifier)

    @Test
    fun `a reading that sets only deviceType leaves osConfidence and osGuess alone`() = runTest {
        h.fakes.seedLegacyRow(
            KnownDeviceEntity(
                id = 1, macAddress = MAC1, hostname = null, ip = "10.0.0.5", vendor = null,
                osGuess = "Linux", osConfidence = 70, fingerprintConfidence = 70,
            ),
        )

        repo.persistScan(listOf(lan("10.0.0.5", MAC1).copy(deviceType = "Printer", fingerprintConfidence = 50)), networkId = null)

        val row = h.known.byId(1)!!
        assertEquals("Printer", row.deviceType)
        assertEquals(50, row.typeConfidence)
        assertEquals("Linux", row.osGuess)
        assertEquals(70, row.osConfidence)
    }

    @Test
    fun `a legacy row without per-field confidence is compared on its single fingerprint confidence`() = runTest {
        h.fakes.seedLegacyRow(
            KnownDeviceEntity(
                id = 1, macAddress = MAC1, hostname = null, ip = "10.0.0.5", vendor = null,
                deviceType = "Chromecast", osGuess = "Android", fingerprintConfidence = 90,
            ),
        )

        repo.persistScan(listOf(lan("10.0.0.5", MAC1).copy(deviceType = "Phone", osGuess = "iOS", fingerprintConfidence = 40)), networkId = null)

        val row = h.known.byId(1)!!
        assertEquals("Chromecast", row.deviceType)
        assertEquals("Android", row.osGuess)
        assertNull(row.typeConfidence)
        assertNull(row.osConfidence)
    }

    @Test
    fun `a new device records its confidence per field it actually set`() = runTest {
        repo.persistScan(listOf(lan("10.0.0.6", MAC1).copy(deviceType = "Router", fingerprintConfidence = 85)), networkId = null)

        val row = h.known.allDevices.single()
        assertEquals(85, row.typeConfidence)
        assertNull(row.osConfidence)
    }

    @Test
    fun `a rescan never overwrites a user-authored field or isKnown`() = runTest {
        h.fakes.seedLegacyRow(
            KnownDeviceEntity(
                id = 1, macAddress = MAC1, hostname = "old", ip = "10.0.0.5", vendor = "V",
                isKnown = true, customName = "My NAS", tags = "a,b", notes = "n", location = "Garage",
                deviceTypeOverride = "NAS", osOverride = "TrueNAS", modelOverride = "Mine",
            ),
        )

        repo.persistScan(
            listOf(lan("10.0.0.9", MAC1, hostname = "scan-name").copy(deviceType = "Router", osGuess = "OpenWrt", model = "X", fingerprintConfidence = 99)),
            networkId = 4,
        )

        val row = h.known.byId(1)!!
        assertEquals("scan-name", row.hostname)
        assertEquals("Router", row.deviceType)
        assertEquals(true, row.isKnown)
        assertEquals(listOf("My NAS", "a,b", "n", "Garage"), listOf(row.customName, row.tags, row.notes, row.location))
        assertEquals(listOf("NAS", "TrueNAS", "Mine"), listOf(row.deviceTypeOverride, row.osOverride, row.modelOverride))
    }

    @Test
    fun `identifiers of a new device are stored and a rescan bumps lastSeen without duplicating`() = runTest {
        repo.persistScan(listOf(lan("10.0.0.7", MAC1, udn("uuid:a", proven = true))), networkId = null)
        val id = h.known.allDevices.single().id
        val stored = h.identifiers.getForDevice(id)
        assertEquals(setOf("uuid:a", MAC1_ID), stored.map { it.value }.toSet())
        // age them, then rescan
        stored.forEach { h.identifiers.touch(it.id, 1) }

        repo.persistScan(listOf(lan("10.0.0.7", MAC1, udn("uuid:a", proven = true))), networkId = null)

        val after = h.identifiers.getForDevice(id)
        assertEquals(2, after.size)
        assertTrue(after.all { it.lastSeen > 1 })
    }

    @Test
    fun `an identifier another device owns is not taken, it is kept as untrusted evidence`() = runTest {
        h.seedRow(1, "10.0.0.5", ids = listOf(udn("uuid:x")))

        repo.persistScan(listOf(lan("10.0.0.20", MAC2, udn("uuid:x"))), networkId = null)

        val newRow = h.known.allDevices.single { it.id != 1L }
        assertEquals(1L, h.identifiers.find("UPNP_UDN", "uuid:x", "")?.deviceId)
        assertEquals(listOf("00bbcc000002"), h.identifiers.getForDevice(newRow.id).map { it.value })
        assertTrue(h.observations.getForDevice(newRow.id).any { it.weight == 0 && it.value.contains("uuid:x") })
    }

    @Test
    fun `observations are capped at 64 per device, evicting the lowest weight first`() = runTest {
        fun obs(range: IntRange, weight: Int) =
            range.map { Observation(ObservationSource.PORTS, ObservedField.PRODUCT, "p$it", weight, null) }
        repo.persistScan(listOf(lan("10.0.0.8", MAC1).copy(observations = obs(1..40, weight = 10))), networkId = null)
        repo.persistScan(listOf(lan("10.0.0.8", MAC1).copy(observations = obs(101..140, weight = 50))), networkId = null)

        val rows = h.observations.getForDevice(h.known.allDevices.single().id)
        assertEquals(64, rows.size)
        assertEquals(40, rows.count { it.weight == 50 })
        assertEquals(24, rows.count { it.weight == 10 })
    }

    @Test
    fun `a rescan keeps the sighting's firstSeen and refreshes lastSeen and lastIp`() = runTest {
        h.fakes.seedLegacyRow(KnownDeviceEntity(id = 1, macAddress = MAC1, hostname = null, ip = "10.0.0.8", vendor = null))
        h.sightings.insertIfNew(DeviceSightingEntity(1, "unknown-network", null, "10.0.0.8", firstSeen = 1, lastSeen = 1))

        repo.persistScan(listOf(lan("10.0.0.99", MAC1)), networkId = null)

        val s = h.sightings.getForDevice(1).single()
        assertEquals(1L, s.firstSeen)
        assertTrue(s.lastSeen > 1)
        assertEquals("10.0.0.99", s.lastIp)
    }

    @Test
    fun `the macAddress column keeps the most recent universal MAC and a local MAC never replaces it`() {
        assertEquals(MAC2, chosenMac(stored = MAC1, scanned = MAC2))
        assertEquals(MAC1, chosenMac(stored = MAC1, scanned = MAC_LOCAL_RAW))
        assertEquals(MAC1, chosenMac(stored = MAC_LOCAL_RAW, scanned = MAC1))
        assertEquals(MAC_LOCAL_RAW, chosenMac(stored = null, scanned = MAC_LOCAL_RAW))
        assertEquals(MAC1, chosenMac(stored = MAC1, scanned = null))
        assertEquals(MAC1, chosenMac(stored = MAC1, scanned = "ff:ff:ff:ff:ff:ff"))
    }
}
