package com.ventouxlabs.netlens.widget.util

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class RelativeTimeTest {

    @Test
    fun `relativeTimeLabel returns empty for zero timestamp`() {
        assertEquals("", relativeTimeLabel(0L, now = 1_000_000L))
    }

    @Test
    fun `relativeTimeLabel returns empty for negative timestamp`() {
        assertEquals("", relativeTimeLabel(-1L, now = 1_000_000L))
    }

    @Test
    fun `relativeTimeLabel returns just now for less than one minute`() {
        val now = 1_000_000L
        assertEquals("just now", relativeTimeLabel(now - 30_000, now = now))
        assertEquals("just now", relativeTimeLabel(now - 59_999, now = now))
        assertEquals("just now", relativeTimeLabel(now, now = now))
    }

    @Test
    fun `relativeTimeLabel returns minutes for under one hour`() {
        val now = 10_000_000L
        assertEquals("1m", relativeTimeLabel(now - 60_000, now = now))
        assertEquals("5m", relativeTimeLabel(now - 5 * 60_000, now = now))
        assertEquals("59m", relativeTimeLabel(now - 59 * 60_000, now = now))
    }

    @Test
    fun `relativeTimeLabel returns hours for under one day`() {
        val now = 100_000_000L
        assertEquals("1h", relativeTimeLabel(now - 60 * 60_000, now = now))
        assertEquals("2h", relativeTimeLabel(now - 120 * 60_000, now = now))
        assertEquals("23h", relativeTimeLabel(now - 23 * 60 * 60_000, now = now))
    }

    @Test
    fun `relativeTimeLabel returns days for 24 hours or more`() {
        val now = 1_000_000_000L
        assertEquals("1d", relativeTimeLabel(now - 1440 * 60_000, now = now))
        assertEquals("7d", relativeTimeLabel(now - 7 * 1440 * 60_000, now = now))
    }
}
