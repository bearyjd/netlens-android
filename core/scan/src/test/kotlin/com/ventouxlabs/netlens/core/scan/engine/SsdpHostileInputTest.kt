package com.ventouxlabs.netlens.core.scan.engine

import com.ventouxlabs.netlens.core.scan.model.SsdpDevice
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Adversarial inputs for the SSDP parsers.
 *
 * Everything these two functions see is chosen by whoever answers an M-SEARCH on the LAN, and
 * `parseDeviceXml` additionally parses an HTTP body fetched from an attacker-supplied URL. The
 * existing [SsdpScannerTest] covers well-formed input; this file covers input designed to break
 * the parser.
 *
 * **The contract being pinned is "never throw".** `fetchDeviceDescription` wraps these in a
 * catch-all, so a throw is currently swallowed into a device with no description — invisible in
 * production and invisible in a test that only checks the happy path. That masking is why the
 * reordered-tag crash below survived: reachable from the network, and nothing failed. These
 * assert on the parser directly and never lean on the caller's catch.
 */
class SsdpHostileInputTest {

    /** Asserts no throw and returns the result, with the offending input in the message. */
    private fun parseXml(xml: String): SsdpDevice {
        val result = runCatching { SsdpScannerImpl.parseDeviceXml(IP, xml) }
        assertTrue(
            result.isSuccess,
            "parseDeviceXml threw on \"${xml.take(60)}\": ${result.exceptionOrNull()}",
        )
        return result.getOrThrow()
    }

    private fun parseLoc(response: String): String? {
        val result = runCatching { SsdpScannerImpl.parseLocation(response) }
        assertTrue(
            result.isSuccess,
            "parseLocation threw on ${response.take(60)}: ${result.exceptionOrNull()}",
        )
        return result.getOrNull()
    }

    // --- parseDeviceXml ------------------------------------------------------------------

    @Test
    fun `closing tag before opening tag does not throw`() {
        // Regression: extractTag searched the whole string for "</tag>", found the one at index 0,
        // and called substring(29, 0) -> StringIndexOutOfBoundsException.
        assertEquals("real", parseXml("</friendlyName><friendlyName>real</friendlyName>").friendlyName)
    }

    @Test
    fun `closing tag with no opening tag yields null`() {
        assertNull(parseXml("</friendlyName>").friendlyName)
    }

    @Test
    fun `opening tag with no closing tag yields null`() {
        assertNull(parseXml("<friendlyName>never closed").friendlyName)
    }

    @Test
    fun `adjacent tags yield empty, normalised to null`() {
        assertNull(parseXml("<friendlyName></friendlyName>").friendlyName)
    }

    @Test
    fun `whitespace-only body is normalised to null`() {
        assertNull(parseXml("<friendlyName>   \n\t </friendlyName>").friendlyName)
    }

    @Test
    fun `nested same-name tags take the first close after the first open`() {
        // Not correct XML semantics — this is a substring scanner, not a parser. Pinned so that
        // changing extractTag is a deliberate decision rather than an accident.
        val xml = "<friendlyName>outer<friendlyName>inner</friendlyName></friendlyName>"
        assertEquals("outer<friendlyName>inner", parseXml(xml).friendlyName)
    }

    @Test
    fun `empty and garbage documents do not throw`() {
        listOf("", "   ", "not xml at all", "<<<>>>", "<friendlyName", "<>", "</>")
            .forEach { parseXml(it) }
    }

    @Test
    fun `control characters are flattened at ingestion but bidi overrides survive`() {
        // Contract inverted on 2026-08-17: extractTag now flattens through DisplayText, so a
        // control character never reaches ANY sink (this test used to pin the opposite — "no
        // sink can assume the parser sanitised anything"). Two things still hold: HostName
        // guards the URI sink separately, and a bidi override is NOT a control character and
        // survives — a sink rendering RTL-sensitive text still owns that concern.
        val device = parseXml("<friendlyName>a\u0000b\u202Ec</friendlyName>")
        assertEquals("a b\u202Ec", device.friendlyName)
    }

    @Test
    fun `a very large body is returned without blowing up`() {
        val body = "x".repeat(512 * 1024)
        assertEquals(body.length, parseXml("<friendlyName>$body</friendlyName>").friendlyName?.length)
    }

    @Test
    fun `the four fields are extracted independently`() {
        val xml = "<deviceType>dt</deviceType><manufacturer></manufacturer>" +
            "</modelName><modelName>mn</modelName>"

        val device = parseXml(xml)

        assertEquals("dt", device.deviceType)
        assertNull(device.manufacturer)
        assertEquals("mn", device.modelName)
        assertNull(device.friendlyName)
        assertEquals(IP, device.ip)
    }

    // --- parseLocation -------------------------------------------------------------------

    @Test
    fun `header variants do not throw`() {
        listOf(
            "",
            "LOCATION:",
            "LOCATION: ",
            "location: http://1.2.3.4/d.xml",
            "LOCATION: http://[::1]:8080/d.xml",
            "LOCATION:\thttp://1.2.3.4/d.xml",
            "NOTLOCATION: http://1.2.3.4/d.xml",
            "LOCATION: " + "a".repeat(100_000),
            "LOCATION\u0000: http://1.2.3.4/d.xml",
            "\r\n\r\n",
        ).forEach { parseLoc(it) }
    }

    @Test
    fun `empty LOCATION value yields empty string`() {
        assertEquals("", parseLoc("LOCATION:"))
    }

    @Test
    fun `first LOCATION wins when duplicated`() {
        assertEquals(
            "http://1.1.1.1/a.xml",
            parseLoc("LOCATION: http://1.1.1.1/a.xml\r\nLOCATION: http://2.2.2.2/b.xml"),
        )
    }

    @Test
    fun `a colon in the URL does not truncate the value`() {
        assertEquals(
            "http://192.168.1.5:49152/desc.xml",
            parseLoc("LOCATION: http://192.168.1.5:49152/desc.xml"),
        )
    }

    @Test
    fun `a leading space means the header is not matched`() {
        // startsWith is not trimmed, so an indented header is ignored. Pinned as current
        // behaviour: a device that indents its headers is simply not discovered, not a crash.
        assertNull(parseLoc(" LOCATION: http://1.2.3.4/d.xml"))
    }

    @Test
    fun `no LOCATION header yields null`() {
        assertNull(parseLoc("HTTP/1.1 200 OK\r\nST: ssdp:all"))
    }

    private companion object {
        const val IP = "192.168.1.50"
    }
}
