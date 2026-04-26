package com.ventoux.netlens.widget.ui

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class WidgetThemeTest {

    @Test
    fun `relativeTime returns empty for zero timestamp`() {
        assertEquals("", WidgetTheme.relativeTime(0L, now = 1_000_000L))
    }

    @Test
    fun `relativeTime returns empty for negative timestamp`() {
        assertEquals("", WidgetTheme.relativeTime(-1L, now = 1_000_000L))
    }

    @Test
    fun `relativeTime returns just now for less than one minute`() {
        val now = 1_000_000L
        assertEquals("just now", WidgetTheme.relativeTime(now - 30_000, now = now))
        assertEquals("just now", WidgetTheme.relativeTime(now - 59_999, now = now))
        assertEquals("just now", WidgetTheme.relativeTime(now, now = now))
    }

    @Test
    fun `relativeTime returns minutes for under one hour`() {
        val now = 10_000_000L
        assertEquals("1m", WidgetTheme.relativeTime(now - 60_000, now = now))
        assertEquals("5m", WidgetTheme.relativeTime(now - 5 * 60_000, now = now))
        assertEquals("59m", WidgetTheme.relativeTime(now - 59 * 60_000, now = now))
    }

    @Test
    fun `relativeTime returns hours for under one day`() {
        val now = 100_000_000L
        assertEquals("1h", WidgetTheme.relativeTime(now - 60 * 60_000, now = now))
        assertEquals("2h", WidgetTheme.relativeTime(now - 120 * 60_000, now = now))
        assertEquals("23h", WidgetTheme.relativeTime(now - 23 * 60 * 60_000, now = now))
    }

    @Test
    fun `relativeTime returns days for 24 hours or more`() {
        val now = 1_000_000_000L
        assertEquals("1d", WidgetTheme.relativeTime(now - 1440 * 60_000, now = now))
        assertEquals("7d", WidgetTheme.relativeTime(now - 7 * 1440 * 60_000, now = now))
    }

    @Test
    fun `scoreColor returns green for A and B`() {
        assertEquals(WidgetTheme.SCORE_GREEN, WidgetTheme.scoreColor("A"))
        assertEquals(WidgetTheme.SCORE_GREEN, WidgetTheme.scoreColor("B"))
        assertEquals(WidgetTheme.SCORE_GREEN, WidgetTheme.scoreColor("a"))
    }

    @Test
    fun `scoreColor returns amber for C`() {
        assertEquals(WidgetTheme.SCORE_AMBER, WidgetTheme.scoreColor("C"))
    }

    @Test
    fun `scoreColor returns red for D and F`() {
        assertEquals(WidgetTheme.SCORE_RED, WidgetTheme.scoreColor("D"))
        assertEquals(WidgetTheme.SCORE_RED, WidgetTheme.scoreColor("F"))
    }

    @Test
    fun `scoreColor returns gray for unknown grades`() {
        assertEquals(WidgetTheme.SCORE_GRAY, WidgetTheme.scoreColor(""))
        assertEquals(WidgetTheme.SCORE_GRAY, WidgetTheme.scoreColor("X"))
    }
}
