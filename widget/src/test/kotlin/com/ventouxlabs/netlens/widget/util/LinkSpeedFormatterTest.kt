package com.ventouxlabs.netlens.widget.util

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class LinkSpeedFormatterTest {

    @Test
    fun `zero returns dash`() {
        assertEquals("—", formatLinkSpeed(0))
    }

    @Test
    fun `negative returns dash`() {
        assertEquals("—", formatLinkSpeed(-1))
    }

    @Test
    fun `normal speed returns M suffix`() {
        assertEquals("72M", formatLinkSpeed(72))
    }

    @Test
    fun `433 Mbps returns M suffix`() {
        assertEquals("433M", formatLinkSpeed(433))
    }

    @Test
    fun `999 Mbps returns M suffix`() {
        assertEquals("999M", formatLinkSpeed(999))
    }

    @Test
    fun `1000 Mbps returns G suffix`() {
        assertEquals("1.0G", formatLinkSpeed(1000))
    }

    @Test
    fun `1200 Mbps returns G suffix`() {
        assertEquals("1.2G", formatLinkSpeed(1200))
    }

    @Test
    fun `2400 Mbps returns G suffix`() {
        assertEquals("2.4G", formatLinkSpeed(2400))
    }
}
