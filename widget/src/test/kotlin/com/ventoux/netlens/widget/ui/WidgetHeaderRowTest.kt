package com.ventoux.netlens.widget.ui

import androidx.compose.ui.unit.dp
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class WidgetHeaderRowTest {

    // shouldShowHeaderTimestamp — threshold is 60.dp

    @Test
    fun `shouldShowHeaderTimestamp returns false below 60dp`() {
        assertFalse(shouldShowHeaderTimestamp(59.dp))
        assertFalse(shouldShowHeaderTimestamp(0.dp))
    }

    @Test
    fun `shouldShowHeaderTimestamp returns true at exactly 60dp`() {
        assertTrue(shouldShowHeaderTimestamp(60.dp))
    }

    @Test
    fun `shouldShowHeaderTimestamp returns true above 60dp`() {
        assertTrue(shouldShowHeaderTimestamp(61.dp))
        assertTrue(shouldShowHeaderTimestamp(120.dp))
    }

    // headerVerticalPad — 4.dp at or above 80.dp, 2.dp below

    @Test
    fun `headerVerticalPad returns 2dp below 80dp`() {
        assertEquals(2.dp, headerVerticalPad(0.dp))
        assertEquals(2.dp, headerVerticalPad(60.dp))
        assertEquals(2.dp, headerVerticalPad(79.dp))
    }

    @Test
    fun `headerVerticalPad returns 4dp at exactly 80dp`() {
        assertEquals(4.dp, headerVerticalPad(80.dp))
    }

    @Test
    fun `headerVerticalPad returns 4dp above 80dp`() {
        assertEquals(4.dp, headerVerticalPad(81.dp))
        assertEquals(4.dp, headerVerticalPad(200.dp))
    }
}
