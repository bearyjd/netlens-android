package com.ventouxlabs.netlens.core.scan.identity

import com.ventouxlabs.netlens.core.scan.model.identity.DeviceIdentifier
import com.ventouxlabs.netlens.core.scan.model.identity.IdentifierKind
import com.ventouxlabs.netlens.core.scan.model.identity.ObservationSource
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** The write path used once a device has an id: ownership, per-kind cap, no stealing. */
class IdentifierRecorderTest {
    private val h = IdentityHarness()
    private val recorder = DeviceIdentifierRecorder(h.identifiers)

    private fun mdns(n: Int) = DeviceIdentifier(IdentifierKind.MDNS_DEVICE_ID, "id-%03d".format(n), ObservationSource.MDNS)

    @Test
    fun `new identifiers are inserted scoped by kind and existing ones are touched`() = runTest {
        h.seedRow(101, "10.0.0.1")
        recorder.record(101, setOf(udn("uuid:a"), localMac(MAC_LOCAL_ID)), NET, now = 5)
        recorder.record(101, setOf(udn("uuid:a")), NET, now = 9)

        assertEquals(9L, h.identifiers.find("UPNP_UDN", "uuid:a", "")?.lastSeen)
        assertEquals(101L, h.identifiers.find("MAC_LOCAL", MAC_LOCAL_ID, NET)?.deviceId)
    }

    @Test
    fun `an identifier owned by another device is never stolen`() = runTest {
        h.seedRow(101, "10.0.0.1", ids = listOf(udn("uuid:a")))
        h.seedRow(102, "10.0.0.2")

        recorder.record(102, setOf(udn("uuid:a")), NET, now = 5)

        assertEquals(101L, h.identifiers.find("UPNP_UDN", "uuid:a", "")?.deviceId)
        assertTrue(h.identifiers.getForDevice(102).isEmpty())
    }

    @Test
    fun `a device never accumulates more than the per-kind cap across scans`() = runTest {
        h.seedRow(101, "10.0.0.1")

        (1..100).chunked(10).forEachIndexed { i, chunk ->
            recorder.record(101, chunk.map(::mdns).toSet(), NET, now = i.toLong())
        }

        assertEquals(IdentityPolicy.MAX_IDENTIFIERS_PER_KIND, h.identifiers.countByKind(101, "MDNS_DEVICE_ID"))
    }

    @Test
    fun `over-long identifier values are not stored`() = runTest {
        h.seedRow(101, "10.0.0.1")

        recorder.record(101, setOf(udn("u".repeat(257))), NET, now = 1)

        assertTrue(h.identifiers.getForDevice(101).isEmpty())
    }
}
