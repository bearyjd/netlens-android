package com.ventouxlabs.netlens.widget

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class LatencyHistoryTest {

    @Test
    fun `appendLatencySample appends to empty history`() {
        assertEquals("42", appendLatencySample(existingCsv = null, sample = 42))
    }

    @Test
    fun `appendLatencySample appends to existing history`() {
        assertEquals("10,20,30", appendLatencySample(existingCsv = "10,20", sample = 30))
    }

    @Test
    fun `appendLatencySample caps history at 12 most recent samples`() {
        val existing = (1..12).joinToString(",")
        val result = appendLatencySample(existingCsv = existing, sample = 13)
        assertEquals((2..13).joinToString(","), result)
    }

    @Test
    fun `appendLatencySample respects custom cap`() {
        val result = appendLatencySample(existingCsv = "1,2,3", sample = 4, cap = 3)
        assertEquals("2,3,4", result)
    }

    @Test
    fun `appendLatencySample with null sample is a no-op`() {
        assertEquals("10,20,30", appendLatencySample(existingCsv = "10,20,30", sample = null))
    }

    @Test
    fun `appendLatencySample with null sample and null history returns empty`() {
        assertEquals("", appendLatencySample(existingCsv = null, sample = null))
    }

    @Test
    fun `appendLatencySample tolerates malformed csv entries`() {
        assertEquals("10,30,40", appendLatencySample(existingCsv = "10,abc,,30", sample = 40))
    }
}
