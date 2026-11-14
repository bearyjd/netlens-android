package com.ventouxlabs.netlens.core.scan.inventory

import com.ventouxlabs.netlens.core.data.dao.DeviceIdentityDao
import com.ventouxlabs.netlens.core.data.model.DeviceIdentifierEntity
import com.ventouxlabs.netlens.core.data.model.DeviceObservationEntity
import com.ventouxlabs.netlens.core.data.model.KnownDeviceEntity
import com.ventouxlabs.netlens.core.scan.RecordingNewDeviceNotifier
import com.ventouxlabs.netlens.core.scan.identity.IdentityHarness
import com.ventouxlabs.netlens.core.scan.identity.MAC1
import com.ventouxlabs.netlens.core.scan.identity.MAC1_ID
import com.ventouxlabs.netlens.core.scan.identity.MAC2
import com.ventouxlabs.netlens.core.scan.identity.lan
import com.ventouxlabs.netlens.core.scan.identity.udn
import com.ventouxlabs.netlens.core.scan.identity.universalMac
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** The duplicate-row guard that replaces the unique MAC index: the repository Mutex and re-resolve on a lost race. */
class InventoryRaceTest {
    private val h = IdentityHarness()
    private val notifier = RecordingNewDeviceNotifier()

    /** Wraps the transactional DAO so a test can interfere with the first [insertDeviceWithIdentifiers] call. */
    private fun interfering(onInsert: suspend (call: Int) -> Long?): DeviceIdentityDao {
        var calls = 0
        return object : DeviceIdentityDao by h.tx {
            override suspend fun insertDeviceWithIdentifiers(
                device: KnownDeviceEntity,
                identifiers: List<DeviceIdentifierEntity>,
                observations: List<DeviceObservationEntity>,
                dedupeByMac: Boolean,
            ): Long = onInsert(calls++) ?: h.tx.insertDeviceWithIdentifiers(device, identifiers, observations, dedupeByMac)
        }
    }

    @Test
    fun `losing the insert race re-resolves onto the winner's row instead of duplicating it`() = runTest {
        val repo = h.fakes.repository(
            notifier,
            identityDao = interfering { call ->
                if (call == 0) {
                    // Another writer inserts the same device between our resolve and our insert.
                    h.fakes.seedLegacyRow(KnownDeviceEntity(id = 1, macAddress = MAC1, hostname = null, ip = "10.0.0.1", vendor = null))
                    DeviceIdentityDao.DUPLICATE
                } else {
                    null
                }
            },
        )

        repo.persistScan(listOf(lan("10.0.0.50", MAC1)), networkId = null)

        assertEquals(1, h.known.allDevices.size)
        assertEquals("10.0.0.50", h.known.byId(1)!!.ip)
        assertEquals(0, notifier.notified.size)
    }

    @Test
    fun `when the insert keeps failing the device is skipped, never inserted another way and never notified`() = runTest {
        val repo = h.fakes.repository(notifier, identityDao = interfering { DeviceIdentityDao.DUPLICATE })

        repo.persistScan(listOf(lan("10.0.0.50", MAC1), lan("10.0.0.51", MAC2)), networkId = null)

        assertEquals(0, h.known.allDevices.size)
        assertEquals(0, notifier.notified.size)
    }

    @Test
    fun `a re-resolve after a lost race does not turn an untrusted claim into ownership`() = runTest {
        // Two sightings claim uuid:x, so both lose it. While the first inserts, a competing row that owns
        // uuid:x appears. Re-resolving with the claim still on the device would make it that row.
        val repo = h.fakes.repository(
            notifier,
            autoMerge = true,
            identityDao = interfering { call ->
                if (call == 0) {
                    h.fakes.seedLegacyRow(KnownDeviceEntity(id = 9, macAddress = null, hostname = null, ip = "10.0.0.99", vendor = null))
                    h.identifiers.insertIfNew(
                        DeviceIdentifierEntity(
                            deviceId = 9, kind = "UPNP_UDN", value = "uuid:x", scopeKey = "", source = "SSDP", firstSeen = 1, lastSeen = 1,
                        ),
                    )
                    DeviceIdentityDao.DUPLICATE
                } else {
                    null
                }
            },
        )

        repo.persistScan(
            listOf(lan("10.0.0.20", MAC1, udn("uuid:x")), lan("10.0.0.21", MAC2, udn("uuid:x"))),
            networkId = null,
        )

        assertEquals(3, h.known.allDevices.size)
        assertEquals(setOf("10.0.0.20", "10.0.0.21"), notifier.notified.map { it.ip }.toSet())
        assertEquals(9L, h.identifiers.find("UPNP_UDN", "uuid:x", "")?.deviceId)
    }

    @Test
    fun `two concurrent scans of the same identifier-less device create one row and one alert`() = runTest {
        val slow = interfering {
            delay(100) // a suspension between resolve and insert: where an unlocked second scan sneaks in
            null
        }
        val repo = h.fakes.repository(notifier, identityDao = slow)

        val a = launch { repo.persistScan(listOf(lan("10.0.0.77")), networkId = null) }
        val b = launch { repo.persistScan(listOf(lan("10.0.0.77")), networkId = 3) }
        advanceUntilIdle()
        a.join()
        b.join()

        assertEquals(1, h.known.allDevices.size)
        assertEquals(1, notifier.notified.size)
        assertTrue(h.known.allDevices.single().networkId == 3L)
    }
}
