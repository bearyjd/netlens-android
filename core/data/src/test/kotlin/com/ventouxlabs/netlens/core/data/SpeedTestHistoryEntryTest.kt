package com.ventouxlabs.netlens.core.data

import com.ventouxlabs.netlens.core.data.model.SpeedTestHistoryEntry
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class SpeedTestHistoryEntryTest {

    @Test
    fun `new rows default to the TCP_CONNECT latency method`() {
        val entry = SpeedTestHistoryEntry(
            downloadMbps = 42f,
            uploadMbps = 12f,
            latencyMs = 18,
            serverName = "test",
        )
        assertEquals(SpeedTestHistoryEntry.LATENCY_METHOD_TCP_CONNECT, entry.latencyMethod)
    }

    @Test
    fun `legacy and current method tags are distinct`() {
        assertEquals("TCP_CONNECT", SpeedTestHistoryEntry.LATENCY_METHOD_TCP_CONNECT)
        assertEquals("LEGACY_HTTP", SpeedTestHistoryEntry.LATENCY_METHOD_LEGACY_HTTP)
    }
}
