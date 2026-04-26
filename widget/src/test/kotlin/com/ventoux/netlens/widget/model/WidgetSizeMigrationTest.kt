package com.ventoux.netlens.widget.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class WidgetSizeMigrationTest {

    @Test
    fun `old SMALL value does not resolve via valueOf`() {
        val result = runCatching { WidgetSize.valueOf("SMALL") }.getOrNull()
        assertNull(result)
    }

    @Test
    fun `old MEDIUM value does not resolve via valueOf`() {
        val result = runCatching { WidgetSize.valueOf("MEDIUM") }.getOrNull()
        assertNull(result)
    }

    @Test
    fun `old WIDE value does not resolve via valueOf`() {
        val result = runCatching { WidgetSize.valueOf("WIDE") }.getOrNull()
        assertNull(result)
    }

    @Test
    fun `old BANNER value does not resolve via valueOf`() {
        val result = runCatching { WidgetSize.valueOf("BANNER") }.getOrNull()
        assertNull(result)
    }

    @Test
    fun `new COMPACT value resolves correctly`() {
        assertEquals(WidgetSize.COMPACT, WidgetSize.valueOf("COMPACT"))
    }

    @Test
    fun `new STANDARD value resolves correctly`() {
        assertEquals(WidgetSize.STANDARD, WidgetSize.valueOf("STANDARD"))
    }

    @Test
    fun `new DASHBOARD value resolves correctly`() {
        assertEquals(WidgetSize.DASHBOARD, WidgetSize.valueOf("DASHBOARD"))
    }
}
