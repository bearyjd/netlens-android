package com.ventouxlabs.netlens.widget.util

import android.telephony.TelephonyManager
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * The framework-independent half of [NetworkCollector], testable without Robolectric. See
 * `NetworkCollectorTest` for the parts that genuinely need a `Context`.
 */
class NetworkCollectorPureTest {

    // A 15-branch constant mapping with no prior coverage — a mis-assigned constant here is
    // invisible in review and silently mislabels the widget's cell-generation readout.
    @Test
    fun `cellGenerationFor maps every known network type`() {
        assertEquals("LTE", cellGenerationFor(TelephonyManager.NETWORK_TYPE_LTE))
        assertEquals("5G", cellGenerationFor(TelephonyManager.NETWORK_TYPE_NR))

        assertEquals("3G+", cellGenerationFor(TelephonyManager.NETWORK_TYPE_HSPAP))
        assertEquals("3G+", cellGenerationFor(TelephonyManager.NETWORK_TYPE_HSPA))
        assertEquals("3G+", cellGenerationFor(TelephonyManager.NETWORK_TYPE_HSDPA))
        assertEquals("3G+", cellGenerationFor(TelephonyManager.NETWORK_TYPE_HSUPA))

        assertEquals("3G", cellGenerationFor(TelephonyManager.NETWORK_TYPE_UMTS))
        assertEquals("3G", cellGenerationFor(TelephonyManager.NETWORK_TYPE_EVDO_0))
        assertEquals("3G", cellGenerationFor(TelephonyManager.NETWORK_TYPE_EVDO_A))
        assertEquals("3G", cellGenerationFor(TelephonyManager.NETWORK_TYPE_EVDO_B))

        assertEquals("2G", cellGenerationFor(TelephonyManager.NETWORK_TYPE_EDGE))
        assertEquals("2G", cellGenerationFor(TelephonyManager.NETWORK_TYPE_GPRS))
    }

    @Test
    fun `cellGenerationFor returns empty for unknown or unmapped types`() {
        assertEquals("", cellGenerationFor(TelephonyManager.NETWORK_TYPE_UNKNOWN))
        assertEquals("", cellGenerationFor(TelephonyManager.NETWORK_TYPE_IDEN))
        assertEquals("", cellGenerationFor(-1))
    }

    @Test
    fun `isVpnInterfaceName matches every known tunnel prefix`() {
        assertTrue(isVpnInterfaceName("tun0"))
        assertTrue(isVpnInterfaceName("wg0"))
        assertTrue(isVpnInterfaceName("ppp0"))
        assertTrue(isVpnInterfaceName("ipsec0"))
    }

    @Test
    fun `isVpnInterfaceName rejects physical interfaces and null`() {
        assertFalse(isVpnInterfaceName("wlan0"))
        assertFalse(isVpnInterfaceName("rmnet0"))
        assertFalse(isVpnInterfaceName("eth0"))
        assertFalse(isVpnInterfaceName(null))
        assertFalse(isVpnInterfaceName(""))
    }

    // Documents current behavior rather than asserting it's ideal: the match is startsWith, so
    // anything merely beginning with a prefix counts. Pinned so a future change to exact-match
    // or regex semantics is a deliberate decision, not an accident.
    @Test
    fun `isVpnInterfaceName is a prefix match, not an exact one`() {
        assertTrue(isVpnInterfaceName("tunnel-of-lies0"))
        assertTrue(isVpnInterfaceName("wgWhatever"))
    }

    @Test
    fun `cellularLinkSpeedMbps converts kbps to Mbps`() {
        assertEquals(50, cellularLinkSpeedMbps(50_000))
        assertEquals(1, cellularLinkSpeedMbps(1_000))
    }

    @Test
    fun `cellularLinkSpeedMbps reports -1 for a missing estimate`() {
        assertEquals(-1, cellularLinkSpeedMbps(0))
        assertEquals(-1, cellularLinkSpeedMbps(-1))
    }

    // Integer division edge worth pinning: a sub-1-Mbps link reports 0 Mbps, NOT -1 (unknown).
    // Those mean different things to the widget — 0 is "measured, and it's terrible."
    @Test
    fun `cellularLinkSpeedMbps floors a sub-1-Mbps link to zero rather than unknown`() {
        assertEquals(0, cellularLinkSpeedMbps(500))
        assertEquals(0, cellularLinkSpeedMbps(999))
    }
}
