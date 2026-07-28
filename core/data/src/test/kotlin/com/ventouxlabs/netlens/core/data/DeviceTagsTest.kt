package com.ventouxlabs.netlens.core.data

import com.ventouxlabs.netlens.core.data.model.DeviceTags
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DeviceTagsTest {

    @Test
    fun `parse splits and trims`() {
        assertEquals(listOf("printer", "office"), DeviceTags.parse("printer, office"))
    }

    @Test
    fun `parse returns empty for null or blank`() {
        assertEquals(emptyList<String>(), DeviceTags.parse(null))
        assertEquals(emptyList<String>(), DeviceTags.parse("   "))
        assertEquals(emptyList<String>(), DeviceTags.parse(",,, ,"))
    }

    @Test
    fun `parse de-duplicates case-insensitively keeping the first spelling`() {
        assertEquals(listOf("Printer"), DeviceTags.parse("Printer, printer, PRINTER"))
    }

    @Test
    fun `format returns null when nothing survives normalisation`() {
        assertNull(DeviceTags.format(emptyList()))
        assertNull(DeviceTags.format(listOf("", "   ", ",")))
    }

    @Test
    fun `format round-trips through parse`() {
        val stored = DeviceTags.format(listOf(" kids ", "iot"))
        assertEquals("kids,iot", stored)
        assertEquals(listOf("kids", "iot"), DeviceTags.parse(stored))
    }

    @Test
    fun `format caps the tag count`() {
        val many = (1..30).map { "tag$it" }
        val stored = DeviceTags.format(many)
        assertEquals(DeviceTags.MAX_TAGS, DeviceTags.parse(stored).size)
    }

    @Test
    fun `the cap keeps the first tags, so callers must refuse input rather than over-fill`() {
        // Documents *which* tags survive. take(MAX_TAGS) keeps the head, so a UI that let the
        // user append past the cap would silently discard the tag they just added — the newest,
        // not the oldest. DeviceDetailSheet stops at the cap instead; if that ever regresses,
        // this test explains what the user would lose.
        val overfull = (1..DeviceTags.MAX_TAGS).map { "keep$it" } + "dropped"
        val kept = DeviceTags.parse(DeviceTags.format(overfull))
        assertEquals(DeviceTags.MAX_TAGS, kept.size)
        assertTrue("dropped" !in kept)
        assertTrue("keep1" in kept)
    }

    @Test
    fun `normalize strips the separator so a tag cannot split itself in storage`() {
        val stored = DeviceTags.formatFromInput("living,room")
        // Two comma-separated entries, not one tag containing a comma.
        assertEquals(listOf("living", "room"), DeviceTags.parse(stored))

        val single = DeviceTags.format(listOf("living,room"))
        assertEquals(listOf("living room"), DeviceTags.parse(single))
    }

    @Test
    fun `normalize collapses whitespace and caps length`() {
        assertEquals("living room", DeviceTags.normalize("  living    room  "))
        val long = "x".repeat(DeviceTags.MAX_TAG_LENGTH + 10)
        assertEquals(DeviceTags.MAX_TAG_LENGTH, DeviceTags.normalize(long)?.length)
    }

    @Test
    fun `matches is case-insensitive and substring based`() {
        assertTrue(DeviceTags.matches("printer,office", "PRINT"))
        assertFalse(DeviceTags.matches("printer,office", "camera"))
        assertFalse(DeviceTags.matches(null, "printer"))
    }

    @Test
    fun `hasTag requires a whole-tag match`() {
        assertTrue(DeviceTags.hasTag("printer,office", "PRINTER"))
        assertFalse(DeviceTags.hasTag("printer,office", "print"))
    }
}
