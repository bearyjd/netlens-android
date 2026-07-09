package com.ventouxlabs.netlens.widget.ui

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class NetLensWidgetColorsTest {

    @Test
    fun `grades A and B map to accent`() {
        assertEquals(NetLensWidgetColors.accent, NetLensWidgetColors.scoreColor("A"))
        assertEquals(NetLensWidgetColors.accent, NetLensWidgetColors.scoreColor("B"))
    }

    @Test
    fun `grades C and D map to warn`() {
        assertEquals(NetLensWidgetColors.warn, NetLensWidgetColors.scoreColor("C"))
        assertEquals(NetLensWidgetColors.warn, NetLensWidgetColors.scoreColor("D"))
    }

    @Test
    fun `grade F and unknown grades map to stamp`() {
        assertEquals(NetLensWidgetColors.stamp, NetLensWidgetColors.scoreColor("F"))
        assertEquals(NetLensWidgetColors.stamp, NetLensWidgetColors.scoreColor("E"))
        assertEquals(NetLensWidgetColors.stamp, NetLensWidgetColors.scoreColor(""))
        assertEquals(NetLensWidgetColors.stamp, NetLensWidgetColors.scoreColor("?"))
    }

    @Test
    fun `scoreColor is case-insensitive`() {
        assertEquals(NetLensWidgetColors.accent, NetLensWidgetColors.scoreColor("a"))
        assertEquals(NetLensWidgetColors.warn, NetLensWidgetColors.scoreColor("d"))
    }

    @Test
    fun `signal level 3 and above maps to accent`() {
        assertEquals(NetLensWidgetColors.accent, NetLensWidgetColors.rssiColor(3))
        assertEquals(NetLensWidgetColors.accent, NetLensWidgetColors.rssiColor(4))
    }

    @Test
    fun `signal level 2 maps to warn`() {
        assertEquals(NetLensWidgetColors.warn, NetLensWidgetColors.rssiColor(2))
    }

    @Test
    fun `signal level 1 and below maps to stamp`() {
        assertEquals(NetLensWidgetColors.stamp, NetLensWidgetColors.rssiColor(1))
        assertEquals(NetLensWidgetColors.stamp, NetLensWidgetColors.rssiColor(0))
        assertEquals(NetLensWidgetColors.stamp, NetLensWidgetColors.rssiColor(-1))
    }
}
