package com.ventouxlabs.netlens.feature.monitor.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import com.ventouxlabs.netlens.core.data.model.EndpointCheck

class EndpointStatusTest {

    @Test
    fun `null check is NoData`() {
        assertEquals(EndpointStatus.NoData, endpointStatus(null, thresholdMs = 1000))
    }

    @Test
    fun `failed check is Down`() {
        val check = EndpointCheck(endpointId = 1, statusCode = 500, latencyMs = 10, isSuccess = false)
        assertEquals(EndpointStatus.Down, endpointStatus(check, thresholdMs = 1000))
    }

    @Test
    fun `failed check with high latency is still Down`() {
        val check = EndpointCheck(endpointId = 1, statusCode = 0, latencyMs = 5000, isSuccess = false)
        assertEquals(EndpointStatus.Down, endpointStatus(check, thresholdMs = 1000))
    }

    @Test
    fun `successful check under threshold is Up`() {
        val check = EndpointCheck(endpointId = 1, statusCode = 200, latencyMs = 500, isSuccess = true)
        assertEquals(EndpointStatus.Up, endpointStatus(check, thresholdMs = 1000))
    }

    @Test
    fun `successful check exactly at threshold is Up`() {
        val check = EndpointCheck(endpointId = 1, statusCode = 200, latencyMs = 1000, isSuccess = true)
        assertEquals(EndpointStatus.Up, endpointStatus(check, thresholdMs = 1000))
    }

    @Test
    fun `successful check one over threshold is Slow`() {
        val check = EndpointCheck(endpointId = 1, statusCode = 200, latencyMs = 1001, isSuccess = true)
        assertEquals(EndpointStatus.Slow, endpointStatus(check, thresholdMs = 1000))
    }

    @Test
    fun `successful check well over threshold is Slow`() {
        val check = EndpointCheck(endpointId = 1, statusCode = 200, latencyMs = 5000, isSuccess = true)
        assertEquals(EndpointStatus.Slow, endpointStatus(check, thresholdMs = 1000))
    }
}
