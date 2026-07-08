package com.ventouxlabs.netlens.core.oui

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class OuiLookupImplTest {

    private val sampleLines = sequenceOf(
        "OUI/MA-L                                                      Organization",
        "company_id                                                    Organization",
        "",
        "00-1A-11   (hex)\t\tGoogle, Inc.",
        "AC-DE-48   (hex)\t\tPrivate",
        "F4-F5-D8   (hex)\t\tGoogle, Inc.",
        "garbage line with no hex marker",
    )

    @Test
    fun `known vendor lookup returns vendor name`() {
        val table = OuiLookupImpl.parseOuiTable(sampleLines)
        assertEquals("Google, Inc.", table["00:1A:11"])
        assertEquals("Private", table["AC:DE:48"])
    }

    @Test
    fun `unknown prefix falls back to null`() {
        val table = OuiLookupImpl.parseOuiTable(sampleLines)
        assertNull(table["11:22:33"])
    }

    @Test
    fun `lines without a hex marker are skipped`() {
        val table = OuiLookupImpl.parseOuiTable(sampleLines)
        assertEquals(3, table.size)
    }

    @Test
    fun `malformed hex line with extra separator is skipped`() {
        val table = OuiLookupImpl.parseOuiTable(
            sequenceOf("00-1A-11   (hex)\t\tGoogle (hex) Inc."),
        )
        assertTrue(table.isEmpty())
    }

    @Test
    fun `parsing empty input yields empty table`() {
        val table = OuiLookupImpl.parseOuiTable(emptySequence())
        assertTrue(table.isEmpty())
    }

    @Test
    fun `normalizePrefix uppercases and converts dashes to colons`() {
        assertEquals("00:1A:11", OuiLookupImpl.normalizePrefix("00-1a-11-22-33-44"))
        assertEquals("00:1A:11", OuiLookupImpl.normalizePrefix("00:1a:11:22:33:44"))
    }

    @Test
    fun `normalizePrefix is case-insensitive`() {
        assertEquals(
            OuiLookupImpl.normalizePrefix("f4:f5:d8:aa:bb:cc"),
            OuiLookupImpl.normalizePrefix("F4:F5:D8:AA:BB:CC"),
        )
    }

    @Test
    fun `normalizePrefix on malformed short input does not throw`() {
        assertEquals("AB", OuiLookupImpl.normalizePrefix("ab"))
        assertEquals("", OuiLookupImpl.normalizePrefix(""))
    }

    @Test
    fun `lookup by prefix combines parsing and normalization`() {
        val table = OuiLookupImpl.parseOuiTable(sampleLines)
        val vendor = table[OuiLookupImpl.normalizePrefix("f4:f5:d8:11:22:33")]
        assertEquals("Google, Inc.", vendor)
    }
}
