package com.ventouxlabs.netlens.core.data

import com.ventouxlabs.netlens.core.data.model.WatchedNetworkEntity
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class WatchedNetworkEntityTest {

    @Test
    fun `watchEnabled defaults to true and id defaults to zero`() {
        val network = WatchedNetworkEntity(
            displayName = "Home",
            gatewayMac = "AA:BB:CC:DD:EE:FF",
            subnet = "192.168.1.0/24",
        )
        assertEquals(0L, network.id)
        assertTrue(network.watchEnabled)
    }
}
