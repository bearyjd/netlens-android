package com.ventouxlabs.netlens.core.network

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
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
}
