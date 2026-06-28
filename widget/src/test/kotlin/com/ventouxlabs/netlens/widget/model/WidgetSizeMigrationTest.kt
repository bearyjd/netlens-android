package com.ventouxlabs.netlens.widget.model

import com.ventouxlabs.netlens.widget.data.WidgetPreferencesRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class WidgetSizeMigrationTest {

    @Nested
    inner class EnumValueOf {
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

    @Nested
    inner class MigrateWidgetSize {
        @Test
        fun `SMALL migrates to COMPACT`() {
            assertEquals(WidgetSize.COMPACT, WidgetPreferencesRepository.migrateWidgetSize("SMALL"))
        }

        @Test
        fun `MEDIUM migrates to STANDARD`() {
            assertEquals(WidgetSize.STANDARD, WidgetPreferencesRepository.migrateWidgetSize("MEDIUM"))
        }

        @Test
        fun `WIDE migrates to STANDARD`() {
            assertEquals(WidgetSize.STANDARD, WidgetPreferencesRepository.migrateWidgetSize("WIDE"))
        }

        @Test
        fun `BANNER migrates to DASHBOARD`() {
            assertEquals(WidgetSize.DASHBOARD, WidgetPreferencesRepository.migrateWidgetSize("BANNER"))
        }

        @Test
        fun `current COMPACT resolves directly`() {
            assertEquals(WidgetSize.COMPACT, WidgetPreferencesRepository.migrateWidgetSize("COMPACT"))
        }

        @Test
        fun `current STANDARD resolves directly`() {
            assertEquals(WidgetSize.STANDARD, WidgetPreferencesRepository.migrateWidgetSize("STANDARD"))
        }

        @Test
        fun `current DASHBOARD resolves directly`() {
            assertEquals(WidgetSize.DASHBOARD, WidgetPreferencesRepository.migrateWidgetSize("DASHBOARD"))
        }

        @Test
        fun `unknown value returns null`() {
            assertNull(WidgetPreferencesRepository.migrateWidgetSize("NONEXISTENT"))
        }
    }
}
