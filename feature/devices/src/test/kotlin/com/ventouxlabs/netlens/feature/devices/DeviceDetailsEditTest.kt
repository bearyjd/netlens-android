package com.ventouxlabs.netlens.feature.devices

import com.ventouxlabs.netlens.core.data.model.UserDetails
import com.ventouxlabs.netlens.feature.devices.model.DeviceDetailsEdit
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/** `changesOver` decides what the guard sees as the user's edit: stored forms must not look edited. */
class DeviceDetailsEditTest {

    @Test
    fun `an untouched editor over blank notes and a legacy tag column changes nothing`() {
        val base = UserDetails(notes = "  ", tags = "a, B ,a,,", location = " Den ")

        assertEquals(base, DeviceDetailsEdit.from(base).changesOver(base))
    }

    @Test
    fun `editing one field changes only that field and keeps the others' raw stored form`() {
        val base = UserDetails(notes = "  ", tags = "a, B ,a,,")

        val changes = DeviceDetailsEdit.from(base).copy(customName = " NAS ").changesOver(base)

        assertEquals(base.copy(customName = "NAS"), changes)
    }

    @Test
    fun `a real tag edit is written in the normalised form`() {
        val base = UserDetails(tags = "a, B ,a")

        val changes = DeviceDetailsEdit.from(base).copy(tagsInput = "a, B, c").changesOver(base)

        assertEquals("a,B,c", changes.tags)
    }
}
