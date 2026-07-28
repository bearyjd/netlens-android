package com.ventouxlabs.netlens.core.data

import com.ventouxlabs.netlens.core.data.model.KnownDeviceEntity
import com.ventouxlabs.netlens.core.data.model.KnownDeviceSearch
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class KnownDeviceSearchTest {

    private fun device(
        id: Long = 1,
        hostname: String? = null,
        ip: String = "192.168.1.10",
        mac: String? = null,
        vendor: String? = null,
        customName: String? = null,
        tags: String? = null,
        notes: String? = null,
        location: String? = null,
    ) = KnownDeviceEntity(
        id = id,
        macAddress = mac,
        hostname = hostname,
        ip = ip,
        vendor = vendor,
        customName = customName,
        tags = tags,
        notes = notes,
        location = location,
    )

    @Test
    fun `blank query matches everything`() {
        assertTrue(KnownDeviceSearch.matches(device(), ""))
        assertTrue(KnownDeviceSearch.matches(device(), "   "))
    }

    @Test
    fun `matches the user-authored fields, not just scan output`() {
        val d = device(
            customName = "Office Printer",
            tags = "printer,paper",
            notes = "Toner runs out fast",
            location = "Study",
        )
        assertTrue(KnownDeviceSearch.matches(d, "office"))
        assertTrue(KnownDeviceSearch.matches(d, "paper"))
        assertTrue(KnownDeviceSearch.matches(d, "toner"))
        assertTrue(KnownDeviceSearch.matches(d, "study"))
    }

    @Test
    fun `matches scan-derived fields`() {
        val d = device(hostname = "nas.local", mac = "AA:BB:CC:DD:EE:01", vendor = "Synology")
        assertTrue(KnownDeviceSearch.matches(d, "nas"))
        assertTrue(KnownDeviceSearch.matches(d, "aa:bb"))
        assertTrue(KnownDeviceSearch.matches(d, "synology"))
        assertTrue(KnownDeviceSearch.matches(d, "192.168"))
        assertFalse(KnownDeviceSearch.matches(d, "printer"))
    }

    @Test
    fun `allTags de-duplicates case-insensitively and sorts`() {
        val devices = listOf(
            device(id = 1, tags = "printer,IoT"),
            device(id = 2, tags = "iot,camera"),
            device(id = 3, tags = null),
        )
        assertEquals(listOf("camera", "IoT", "printer"), KnownDeviceSearch.allTags(devices))
    }

    @Test
    fun `empty tag filter matches everything`() {
        assertTrue(KnownDeviceSearch.matchesAnyTag(device(tags = null), emptySet()))
    }

    @Test
    fun `tag filter is OR across selected tags`() {
        val printer = device(tags = "printer")
        val camera = device(tags = "camera")
        val neither = device(tags = "tv")
        val selection = setOf("printer", "camera")

        assertTrue(KnownDeviceSearch.matchesAnyTag(printer, selection))
        assertTrue(KnownDeviceSearch.matchesAnyTag(camera, selection))
        assertFalse(KnownDeviceSearch.matchesAnyTag(neither, selection))
    }
}
