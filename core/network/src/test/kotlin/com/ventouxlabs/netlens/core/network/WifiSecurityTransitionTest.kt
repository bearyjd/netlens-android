package com.ventouxlabs.netlens.core.network

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class WifiSecurityTransitionTest {
    @Test
    fun `transition labels are detected`() {
        listOf("WPA2/WPA3", "WPA3/WPA2", "wpa2/wpa3-transition").forEach {
            assertTrue(isWpa3TransitionMode(it), it)
        }
    }

    @Test
    fun `pure and enterprise labels are not transition`() {
        listOf("WPA3", "WPA2", "WPA3-Enterprise", "WPA2-Enterprise", "OWE", "Open").forEach {
            assertFalse(isWpa3TransitionMode(it), it)
        }
    }
}
