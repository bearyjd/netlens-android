package com.ventouxlabs.netlens.core.network

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * [DisplayText.flatten] exists because a device picks its own mDNS/NetBIOS/SSDP name and the LAN
 * Scan and Devices exports render one device per line. A control character in that name forges a
 * row in text the user shares.
 *
 * The distinction from [HostName]: that one validates and returns null, which is right for a URI
 * authority. This one flattens and keeps the value, because an mDNS instance name is a human
 * label that legitimately holds spaces and apostrophes.
 */
class DisplayTextTest {

    @Test
    fun `a newline cannot survive to forge a row`() {
        val forged = "nas)\n192.168.1.1 (Router)  Vendor=Cisco"

        val flat = DisplayText.flatten(forged)

        assertEquals("nas) 192.168.1.1 (Router) Vendor=Cisco", flat)
        assertEquals(1, flat!!.lines().size)
    }

    @Test
    fun `carriage return and tab are flattened too`() {
        assertEquals("a b", DisplayText.flatten("a\rb"))
        assertEquals("a b", DisplayText.flatten("a\tb"))
        assertEquals("a b", DisplayText.flatten("a\r\nb"))
    }

    @Test
    fun `NUL and other C0 controls are flattened`() {
        assertEquals("a b", DisplayText.flatten("a\u0000b"))
        assertEquals("a b", DisplayText.flatten("a\u0007b"))
        assertEquals("a b", DisplayText.flatten("a\u001Fb"))
    }

    @Test
    fun `DEL and C1 controls are flattened`() {
        assertEquals("a b", DisplayText.flatten("a\u007Fb"))
        assertEquals("a b", DisplayText.flatten("a\u0085b"))
        assertEquals("a b", DisplayText.flatten("a\u009Fb"))
    }

    @Test
    fun `legitimate mDNS instance names survive intact`() {
        // These are exactly what HostName.sanitize correctly REJECTS, and exactly what a display
        // path must keep. If this test starts failing, someone has swapped in the validator.
        assertEquals("Brian's MacBook Pro", DisplayText.flatten("Brian's MacBook Pro"))
        assertEquals("Living Room TV", DisplayText.flatten("Living Room TV"))
        assertEquals("HP ENVY 5000 series [A1B2C3]", DisplayText.flatten("HP ENVY 5000 series [A1B2C3]"))
        assertEquals("my_nas.local", DisplayText.flatten("my_nas.local"))
    }

    @Test
    fun `non-ascii is preserved — only controls are targeted`() {
        assertEquals("Café Drucker", DisplayText.flatten("Café Drucker"))
        assertEquals("プリンタ", DisplayText.flatten("プリンタ"))
    }

    @Test
    fun `whitespace runs collapse so padding cannot bloat a column`() {
        assertEquals("a b", DisplayText.flatten("a          b"))
        assertEquals("a b", DisplayText.flatten("a\n\n\n\nb"))
    }

    @Test
    fun `leading and trailing whitespace is trimmed`() {
        assertEquals("nas", DisplayText.flatten("   nas   "))
        assertEquals("nas", DisplayText.flatten("\n\tnas\r\n"))
    }

    @Test
    fun `null in, null out`() {
        assertNull(DisplayText.flatten(null))
    }

    @Test
    fun `a name that flattens to nothing becomes null, not an empty column`() {
        assertNull(DisplayText.flatten(""))
        assertNull(DisplayText.flatten("   "))
        assertNull(DisplayText.flatten("\n\n\n"))
        assertNull(DisplayText.flatten("\u0000\u0000"))
    }

    @Test
    fun `bidi overrides embeddings and PDF are stripped`() {
        for (cp in listOf("\u202A", "\u202B", "\u202C", "\u202D", "\u202E")) {
            assertEquals("ab", DisplayText.flatten("a${cp}b"), "U+%04X".format(cp[0].code))
        }
    }

    @Test
    fun `bidi isolates are stripped`() {
        for (cp in listOf("\u2066", "\u2067", "\u2068", "\u2069")) {
            assertEquals("ab", DisplayText.flatten("a${cp}b"), "U+%04X".format(cp[0].code))
        }
    }

    @Test
    fun `directional marks are stripped`() {
        for (cp in listOf("\u200E", "\u200F", "\u061C")) {
            assertEquals("ab", DisplayText.flatten("a${cp}b"), "U+%04X".format(cp[0].code))
        }
    }

    @Test
    fun `invisible spoofing characters are stripped`() {
        for (cp in listOf("\u200B", "\u2060", "\uFEFF", "\u180E", "\u00AD")) {
            assertEquals("ab", DisplayText.flatten("a${cp}b"), "U+%04X".format(cp[0].code))
        }
    }

    @Test
    fun `other format characters are stripped`() {
        assertEquals("ab", DisplayText.flatten("a\u2061b"))
        assertEquals("ab", DisplayText.flatten("a\u206Fb"))
        assertEquals("ab", DisplayText.flatten("a\u0600b"))
    }

    @Test
    fun `tag block characters are stripped as whole code points`() {
        val tag = String(Character.toChars(0xE0041))
        val cancel = String(Character.toChars(0xE007F))
        val lang = String(Character.toChars(0xE0001))
        assertEquals("ab", DisplayText.flatten("a${tag}b"))
        assertEquals("ab", DisplayText.flatten("a${lang}${tag}${cancel}b"))
    }

    @Test
    fun `line and paragraph separators become a space`() {
        assertEquals("a b", DisplayText.flatten("a\u2028b"))
        assertEquals("a b", DisplayText.flatten("a\u2029b"))
        assertEquals("a b", DisplayText.flatten("a\u2028\u2029b"))
    }

    @Test
    fun `a name of only invisible characters is null`() {
        assertNull(DisplayText.flatten("\u202E\u200B\uFEFF"))
        assertNull(DisplayText.flatten("\u2066\u2069\u00AD\u2060"))
        assertNull(DisplayText.flatten(String(Character.toChars(0xE0041))))
        assertNull(DisplayText.flatten(" \u200B \u2028 "))
    }

    @Test
    fun `emoji ZWJ sequence survives byte-identical`() {
        val family = "\uD83D\uDC68\u200D\uD83D\uDC69\u200D\uD83D\uDC67\u200D\uD83D\uDC66"
        assertEquals(family, DisplayText.flatten(family))
        assertEquals("Mom's $family iPad", DisplayText.flatten("Mom's $family iPad"))
    }

    @Test
    fun `variation selectors for emoji presentation survive`() {
        val heart = "\u2764\uFE0F"
        val text = "\u2764\uFE0E"
        assertEquals(heart, DisplayText.flatten(heart))
        assertEquals(text, DisplayText.flatten(text))
    }

    @Test
    fun `Persian ZWNJ word survives byte-identical`() {
        val word = "\u0645\u06CC\u200C\u062E\u0648\u0627\u0647\u0645"
        assertEquals(word, DisplayText.flatten(word))
    }

    @Test
    fun `Trojan Source style name cannot reorder after flatten`() {
        val flat = DisplayText.flatten("admin\u202Egnp.exe")
        assertEquals("admingnp.exe", flat)
        assertTrue(flat!!.none { Character.getDirectionality(it) == Character.DIRECTIONALITY_RIGHT_TO_LEFT_OVERRIDE })
    }

    @Test
    fun `surrogate pairs survive intact`() {
        val s = "a\uD83D\uDE00b\uD835\uDC9C"
        assertEquals(s, DisplayText.flatten(s))
    }

    @Test
    fun `unpaired surrogates do not crash and are replaced with U+FFFD`() {
        assertEquals("a\uFFFDb", DisplayText.flatten("a\uD83Db"))
        assertEquals("a\uFFFDb", DisplayText.flatten("a\uDE00b"))
        assertEquals("a\uFFFD", DisplayText.flatten("a\uD83D"))
    }

    @Test
    fun `flatten is idempotent`() {
        val inputs = listOf(
            "admin\u202Egnp.exe", "a\u2028 b\u200B  c", "nas)\n192.168.1.1", "\uD83D\uDC68\u200D\uD83D\uDC69",
            "a\uD83Db", "\u200B", "plain", "ad\u200Dmin", "a" + "\u0301".repeat(9), "\u2764\uFE0F\uFE0F", "x\uFE0Fy", "\u0645\u200C\u200C\u062E", "  pad \t ded  ",
        )
        for (x in inputs) {
            val once = DisplayText.flatten(x)
            assertEquals(once, DisplayText.flatten(once), "input: ${x.length} chars")
        }
    }

    @Test
    fun `a name of only joiners is null`() {
        assertNull(DisplayText.flatten("\u200D"))
        assertNull(DisplayText.flatten("\u200C"))
        assertNull(DisplayText.flatten("\u200D\u200C\u200D"))
        assertNull(DisplayText.flatten(" \u200D "))
    }

    @Test
    fun `a joiner next to ASCII or at an edge is dropped`() {
        assertEquals("admin", DisplayText.flatten("ad\u200Dmin"))
        assertEquals("admin", DisplayText.flatten("ad\u200Cmin"))
        assertEquals("abc", DisplayText.flatten("\u200Dabc\u200D"))
        assertEquals("a b", DisplayText.flatten("a \u200D b"))
        assertEquals("\u00E9x", DisplayText.flatten("\u00E9\u200Dx"))
    }

    @Test
    fun `doubled joiners are dropped even between non-ASCII characters`() {
        assertEquals("\u0645\u062E", DisplayText.flatten("\u0645\u200C\u200C\u062E"))
        assertEquals("\uD83D\uDC68\uD83D\uDC69", DisplayText.flatten("\uD83D\uDC68\u200D\u200D\uD83D\uDC69"))
    }

    @Test
    fun `blank-looking fillers and invisible non-Cf characters are stripped`() {
        for (cp in listOf(0x115F, 0x1160, 0x3164, 0xFFA0, 0x2800, 0x17B4, 0x17B5, 0x034F, 0xFFFC)) {
            val c = String(Character.toChars(cp))
            assertEquals("ab", DisplayText.flatten("a" + c + "b"), "U+%04X".format(cp))
            assertNull(DisplayText.flatten(c + c), "only U+%04X".format(cp))
        }
    }

    @Test
    fun `variation selectors other than emoji presentation are stripped`() {
        assertEquals("ab", DisplayText.flatten("a\uFE00b"))
        assertEquals("ab", DisplayText.flatten("a\uFE0Db"))
        assertEquals("ab", DisplayText.flatten("a" + String(Character.toChars(0xE0100)) + "b"))
        assertEquals("ab", DisplayText.flatten("a" + String(Character.toChars(0xE01EF)) + "b"))
    }

    @Test
    fun `VS15 and VS16 are kept only directly after a symbol`() {
        assertEquals("\u2764\uFE0F", DisplayText.flatten("\u2764\uFE0F"))
        assertEquals("\u2764\uFE0E", DisplayText.flatten("\u2764\uFE0E"))
        assertEquals("ab", DisplayText.flatten("a\uFE0Fb"))
        assertEquals("ab", DisplayText.flatten("a\uFE0Eb"))
        assertEquals("\u2764\uFE0F", DisplayText.flatten("\u2764\uFE0F\uFE0F"))
        assertNull(DisplayText.flatten("\uFE0F"))
        assertEquals("1\uFE0F\u20E3", DisplayText.flatten("1\uFE0F\u20E3"))
    }

    @Test
    fun `a name with no letter digit punctuation or symbol is null`() {
        assertNull(DisplayText.flatten("\u0301"))
        assertNull(DisplayText.flatten(" \u0301\u0301 "))
        assertNull(DisplayText.flatten("\u200D\u0301"))
    }

    @Test
    fun `consecutive combining marks are capped at four per base`() {
        assertEquals("a" + "\u0301".repeat(4), DisplayText.flatten("a" + "\u0301".repeat(30)))
        assertEquals("a" + "\u0301".repeat(4) + "b" + "\u0301".repeat(4), DisplayText.flatten("a" + "\u0301".repeat(9) + "b" + "\u0301".repeat(6)))
    }

    @Test
    fun `legitimate stacked diacritics survive the cap`() {
        val arabic = "\u0628\u064E\u0651\u064F"
        val vietnamese = "e\u0323\u0302"
        val thai = "\u0E01\u0E31\u0E49"
        assertEquals(arabic, DisplayText.flatten(arabic))
        assertEquals(vietnamese, DisplayText.flatten(vietnamese))
        assertEquals(thai, DisplayText.flatten(thai))
    }

    @Test
    fun `Arabic Cf marks are stripped`() {
        assertEquals("ab", DisplayText.flatten("a\u0605b"))
        assertEquals("ab", DisplayText.flatten("a\u06DDb"))
        assertEquals("ab", DisplayText.flatten("a\u070Fb"))
    }

    @Test
    fun `a lone surrogate at the start or reversed pair becomes U+FFFD`() {
        assertEquals("\uFFFDabc", DisplayText.flatten("\uDE00abc"))
        assertEquals("\uFFFDabc", DisplayText.flatten("\uD83Dabc"))
        assertEquals("\uFFFD\uFFFD", DisplayText.flatten("\uDE00\uD83D"))
    }

    @Test
    fun `a joiner hidden behind combining marks in ASCII text is dropped`() {
        assertEquals("a\u0301\u0301", DisplayText.flatten("a\u0301\u200D\u0301"))
        assertEquals("a\u0301b", DisplayText.flatten("a\u0301\u200Db"))
        assertEquals("a\u0301", DisplayText.flatten("a\u0301\u200D"))
    }

    @Test
    fun `Indic virama joiners and Persian words survive byte-identical`() {
        val devanagari = "\u0915\u094D\u200D\u0937" // KA + virama + ZWJ + SSA
        val malayalam = "\u0D28\u0D4D\u200D\u0D2E" // NA + virama + ZWJ + MA
        val persian = "\u0645\u06CC\u200C\u062E\u0648\u0627\u0647\u0645"
        val emoji = "\u2764\uFE0F\u200D\uD83D\uDD25" // heart VS16 ZWJ fire
        val skin = "\uD83D\uDC4D\uD83C\uDFFD\u200D\uD83D\uDC69" // thumbs up + skin tone + ZWJ + woman
        for (s in listOf(devanagari, malayalam, persian, emoji, skin)) assertEquals(s, DisplayText.flatten(s))
    }

    @Test
    fun `Mongolian free variation selectors are stripped`() {
        for (cp in listOf(0x180B, 0x180C, 0x180D, 0x180F)) {
            assertEquals("ab", DisplayText.flatten("a" + String(Character.toChars(cp)) + "b"), "U+%04X".format(cp))
        }
    }

    @Test
    fun `total combining marks per string are capped`() {
        val zalgo = (1..20).joinToString(" ") { "a" + "\u0301".repeat(4) }
        val out = DisplayText.flatten(zalgo)!!
        assertEquals(DisplayText.MAX_TOTAL_COMBINING_MARKS, out.count { it == '\u0301' })
        assertEquals(DisplayText.flatten(out), out)
    }

    @Test
    fun `a normal name with few marks is untouched by the total cap`() {
        val name = (1..5).joinToString(" ") { "e\u0323\u0302" }
        assertEquals(name, DisplayText.flatten(name))
    }
}
