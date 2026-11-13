package com.ventouxlabs.netlens.feature.devices

import com.ventouxlabs.netlens.core.data.model.KnownDeviceEntity
import com.ventouxlabs.netlens.feature.devices.model.displayModel
import com.ventouxlabs.netlens.feature.devices.model.displayOs
import com.ventouxlabs.netlens.feature.devices.model.displayType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class DeviceDisplayTest {

    private fun device() = KnownDeviceEntity(
        macAddress = "AA", hostname = null, ip = "192.168.1.2", vendor = null,
        deviceType = "Router", osGuess = "Linux", model = "R1",
    )

    @Test
    fun `override wins and clearing it re-shows the detected value`() {
        val overridden = device().copy(deviceTypeOverride = "NAS", osOverride = "TrueNAS", modelOverride = "Mine")
        assertEquals("NAS", overridden.displayType())
        assertEquals("TrueNAS", overridden.displayOs())
        assertEquals("Mine", overridden.displayModel())

        val cleared = overridden.copy(deviceTypeOverride = null, osOverride = null, modelOverride = null)
        assertEquals("Router", cleared.displayType())
        assertEquals("Linux", cleared.displayOs())
        assertEquals("R1", cleared.displayModel())
    }

    @Test
    fun `values are null when neither override nor detection exists`() {
        val bare = device().copy(deviceType = null, osGuess = null, model = null)
        assertNull(bare.displayType())
        assertNull(bare.displayOs())
        assertNull(bare.displayModel())
    }

    @Test
    fun `a hostile detected model is flattened for display`() {
        assertEquals("X Fake Row", device().copy(model = "X\nFake Row").displayModel())
    }

    @Test
    fun `a legacy oversized model is capped for display`() {
        assertEquals(80, device().copy(model = "A".repeat(10_000)).displayModel()?.length)
        assertEquals(80, device().copy(modelOverride = "A".repeat(10_000)).displayModel()?.length)
    }

    @Test
    fun `a hostile override is flattened too`() {
        assertEquals("X Fake Row", device().copy(modelOverride = "X\r\nFake Row").displayModel())
    }
}
