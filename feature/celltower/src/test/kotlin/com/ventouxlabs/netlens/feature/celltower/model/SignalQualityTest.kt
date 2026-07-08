package com.ventouxlabs.netlens.feature.celltower.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class SignalQualityTest {

    @Test
    fun `rsrpQuality null is unknown`() {
        assertEquals(SignalQuality.Unknown, rsrpQuality(null))
    }

    @Test
    fun `rsrpQuality above -80 is excellent`() {
        assertEquals(SignalQuality.Excellent, rsrpQuality(-79))
        assertEquals(SignalQuality.Excellent, rsrpQuality(-50))
    }

    @Test
    fun `rsrpQuality at or below -80 down to -100 is fair`() {
        assertEquals(SignalQuality.Fair, rsrpQuality(-80))
        assertEquals(SignalQuality.Fair, rsrpQuality(-99))
    }

    @Test
    fun `rsrpQuality at or below -100 is poor`() {
        assertEquals(SignalQuality.Poor, rsrpQuality(-100))
        assertEquals(SignalQuality.Poor, rsrpQuality(-140))
    }

    @Test
    fun `rssiQuality null is unknown`() {
        assertEquals(SignalQuality.Unknown, rssiQuality(null))
    }

    @Test
    fun `rssiQuality above -75 is excellent`() {
        assertEquals(SignalQuality.Excellent, rssiQuality(-74))
        assertEquals(SignalQuality.Excellent, rssiQuality(-40))
    }

    @Test
    fun `rssiQuality at or below -75 down to -95 is fair`() {
        assertEquals(SignalQuality.Fair, rssiQuality(-75))
        assertEquals(SignalQuality.Fair, rssiQuality(-94))
    }

    @Test
    fun `rssiQuality at or below -95 is poor`() {
        assertEquals(SignalQuality.Poor, rssiQuality(-95))
        assertEquals(SignalQuality.Poor, rssiQuality(-120))
    }
}
