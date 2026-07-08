package com.ventouxlabs.netlens.billing

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class FossProStatusTest {

    @Test
    fun `isPro is always true`() {
        val status = FossProStatus()
        assertTrue(status.isPro.value)
    }

    @Test
    fun `isPro stays true across multiple reads`() {
        val status = FossProStatus()
        assertTrue(status.isPro.value)
        assertTrue(status.isPro.value)
    }

    @Test
    fun `launchPurchase is a no-op and does not throw`() {
        val status = FossProStatus()
        status.launchPurchase(activity = object : android.app.Activity() {})
    }
}
