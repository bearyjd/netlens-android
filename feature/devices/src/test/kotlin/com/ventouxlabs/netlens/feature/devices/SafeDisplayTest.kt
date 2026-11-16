package com.ventouxlabs.netlens.feature.devices

import com.ventouxlabs.netlens.core.network.DisplayText
import com.ventouxlabs.netlens.feature.devices.model.bidiIsolate
import com.ventouxlabs.netlens.feature.devices.model.safeDisplay
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** safeDisplay must be DisplayText.flatten plus a clamp: one sanitising rule, not two. */
class SafeDisplayTest {

    @Test
    fun `ordinary input is unchanged`() {
        assertEquals("Brian's MacBook Pro", safeDisplay("Brian's MacBook Pro", 63))
        assertEquals("a b", safeDisplay("  a \n\t b ", 63))
        assertNull(safeDisplay(null, 63))
        assertNull(safeDisplay("   ", 63))
    }

    @Test
    fun `it delegates to flatten for hostile input`() {
        val hostile = listOf(
            "admin\u202Egnp.exe", "a\u200Bb", "a\u2028b", "x\u2066y\u2069", "a\uFEFFb\u00ADc",
            "\uD83D\uDC68\u200D\uD83D\uDC69", "nas)\n192.168.1.1",
        )
        for (h in hostile) assertEquals(DisplayText.flatten(h), safeDisplay(h, 63))
    }

    @Test
    fun `invisible-only input is null`() {
        assertNull(safeDisplay("\u202E\u200B\uFEFF", 63))
    }

    @Test
    fun `ZWJ emoji sequence survives when it fits`() {
        val family = "\uD83D\uDC68\u200D\uD83D\uDC69"
        assertEquals(family, safeDisplay(family, 63))
    }

    @Test
    fun `clamp never ends on half a surrogate pair`() {
        val s = "ab\uD83D\uDE00cd"
        assertEquals("ab", safeDisplay(s, 3))
        assertEquals("ab\uD83D\uDE00", safeDisplay(s, 4))
        assertEquals("abc", safeDisplay("abcdef", 3))
    }

    @Test
    fun `clamp then trim leaves no trailing space`() {
        assertEquals("ab", safeDisplay("ab cd", 3))
    }

    @Test
    fun `bidiIsolate wraps flattened text in FSI and PDI`() {
        val wrapped = bidiIsolate(safeDisplay("admin\u202Egnp.exe", 63)!!)
        assertEquals("\u2068admingnp.exe\u2069", wrapped)
        assertTrue(wrapped.startsWith("\u2068") && wrapped.endsWith("\u2069"))
    }

    @Test
    fun `hostile input gives literal expected output`() {
        assertEquals("admingnp.exe", safeDisplay("admin\u202Egnp.exe", 63))
        assertEquals("ab", safeDisplay("a\u200Bb", 63))
        assertEquals("a b", safeDisplay("a\u2028b", 63))
        assertEquals("xy", safeDisplay("x\u2066y\u2069", 63))
        assertEquals("abc", safeDisplay("a\uFEFFb\u00ADc", 63))
        assertEquals("admin", safeDisplay("ad\u200Dmin", 63))
        assertEquals("nas) 192.168.1.1", safeDisplay("nas)\n192.168.1.1", 63))
        assertNull(safeDisplay("\u200D", 63))
    }

    @Test
    fun `a ZWJ sequence clamped mid-sequence never ends on a joiner or half a pair`() {
        val family = "\uD83D\uDC68\u200D\uD83D\uDC69\u200D\uD83D\uDC67"
        assertEquals("\uD83D\uDC68", safeDisplay(family, 3)) // ends on the ZWJ
        assertEquals("\uD83D\uDC68", safeDisplay(family, 4)) // ends on half of the next emoji
        assertEquals("\uD83D\uDC68\u200D\uD83D\uDC69", safeDisplay(family, 5))
        assertEquals("\uD83D\uDC68\u200D\uD83D\uDC69", safeDisplay(family, 6)) // trailing ZWJ dropped
        for (max in 1..family.length) {
            val out = safeDisplay(family, max)
            assertEquals(DisplayText.flatten(out), out, "idempotent at max=$max")
        }
    }

    @Test
    fun `a trailing ZWNJ from a clamped Persian word is dropped`() {
        assertEquals("\u0645\u06CC", safeDisplay("\u0645\u06CC\u200C\u062E", 3))
    }
}
