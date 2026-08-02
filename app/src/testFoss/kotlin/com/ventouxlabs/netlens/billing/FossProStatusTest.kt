package com.ventouxlabs.netlens.billing

import android.app.Activity
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
        // Foss builds never inspect the Activity. Allocate one without running Android's real
        // constructor, which requires a prepared Looper and is unavailable in a plain JVM test.
        status.launchPurchase(activity = unusedActivity())
    }

    private fun unusedActivity(): Activity {
        // Android's unit-test compile classpath does not expose sun.misc.Unsafe, but the host
        // JVM does. Reflection keeps this implementation detail out of the Android artefact.
        val unsafeClass = Class.forName("sun.misc.Unsafe")
        val field = unsafeClass.getDeclaredField("theUnsafe")
        field.isAccessible = true
        val unsafe = field.get(null)
        return unsafeClass.getMethod("allocateInstance", Class::class.java)
            .invoke(unsafe, Activity::class.java) as Activity
    }
}
