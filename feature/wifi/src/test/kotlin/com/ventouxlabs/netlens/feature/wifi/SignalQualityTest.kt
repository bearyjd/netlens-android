package com.ventouxlabs.netlens.feature.wifi

import com.ventouxlabs.netlens.feature.wifi.model.SignalQuality
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SignalQualityTest {

    @Test
    fun `bands are inclusive at their upper boundary`() {
        assertEquals(SignalQuality.EXCELLENT, SignalQuality.forRssi(-55))
        assertEquals(SignalQuality.GOOD, SignalQuality.forRssi(-56))
        assertEquals(SignalQuality.GOOD, SignalQuality.forRssi(-67))
        assertEquals(SignalQuality.FAIR, SignalQuality.forRssi(-68))
        assertEquals(SignalQuality.FAIR, SignalQuality.forRssi(-75))
        assertEquals(SignalQuality.WEAK, SignalQuality.forRssi(-76))
        assertEquals(SignalQuality.WEAK, SignalQuality.forRssi(-85))
        assertEquals(SignalQuality.UNUSABLE, SignalQuality.forRssi(-86))
    }

    @Test
    fun `implausible readings still classify`() {
        assertEquals(SignalQuality.EXCELLENT, SignalQuality.forRssi(0))
        assertEquals(SignalQuality.UNUSABLE, SignalQuality.forRssi(-127))
    }

    @Test
    fun `only weak and unusable count as weak spots`() {
        assertFalse(SignalQuality.EXCELLENT.isWeakSpot)
        assertFalse(SignalQuality.GOOD.isWeakSpot)
        assertFalse(SignalQuality.FAIR.isWeakSpot)
        assertTrue(SignalQuality.WEAK.isWeakSpot)
        assertTrue(SignalQuality.UNUSABLE.isWeakSpot)
    }

    @Test
    fun `normalize clamps to the graph window`() {
        assertEquals(0f, SignalQuality.normalize(-120))
        assertEquals(0f, SignalQuality.normalize(SignalQuality.MIN_GRAPH_RSSI))
        assertEquals(1f, SignalQuality.normalize(SignalQuality.MAX_GRAPH_RSSI))
        assertEquals(1f, SignalQuality.normalize(-10))
    }

    @Test
    fun `normalize is monotonic across the window`() {
        val weak = SignalQuality.normalize(-85)
        val mid = SignalQuality.normalize(-65)
        val strong = SignalQuality.normalize(-45)
        assertTrue(weak < mid)
        assertTrue(mid < strong)
    }
}
