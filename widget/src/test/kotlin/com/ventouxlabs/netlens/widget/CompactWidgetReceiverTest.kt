package com.ventouxlabs.netlens.widget

import android.content.Context
import android.net.ConnectivityManager
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.WorkManager
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

/**
 * Covers one representative widget receiver's `onEnabled`/`onDisabled` lifecycle — all four
 * `*WidgetReceiver` classes are byte-identical apart from their class name and
 * `glanceAppWidget` assignment (confirmed by diff), so this stands in for all of them.
 *
 * This is also the regression guard for the private-static-companion `networkCallback` hazard:
 * each receiver keeps its own registered-callback reference in a per-class `private companion
 * object` var, so a second `onEnabled()` without an intervening `onDisabled()` must not leak a
 * second registered callback.
 */
@RunWith(RobolectricTestRunner::class)
class CompactWidgetReceiverTest {

    private lateinit var context: Context
    private lateinit var receiver: CompactWidgetReceiver

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        WorkManagerTestInitHelper.initializeTestWorkManager(
            context,
            Configuration.Builder().setExecutor(SynchronousExecutor()).build(),
        )
        receiver = CompactWidgetReceiver()
    }

    @After
    fun tearDown() {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        shadowOf(cm).networkCallbacks.toList().forEach {
            try { cm.unregisterNetworkCallback(it) } catch (_: IllegalArgumentException) { }
        }
    }

    @Test
    fun `onEnabled registers a network callback and enqueues both refresh work items`() {
        receiver.onEnabled(context)

        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        assertEquals(1, shadowOf(cm).networkCallbacks.size)
        assertEquals(
            1,
            WorkManager.getInstance(context).getWorkInfosForUniqueWork("widget_refresh").get().size,
        )
        assertEquals(
            1,
            WorkManager.getInstance(context).getWorkInfosForUniqueWork("widget_refresh_periodic").get().size,
        )
    }

    @Test
    fun `onDisabled unregisters the network callback`() {
        receiver.onEnabled(context)

        receiver.onDisabled(context)

        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        assertEquals(0, shadowOf(cm).networkCallbacks.size)
    }

    // Guards the private-static-companion hazard: calling onEnabled twice without an
    // intervening onDisabled must still leave exactly one callback registered, not two.
    @Test
    fun `calling onEnabled twice does not leak a second registered callback`() {
        receiver.onEnabled(context)

        receiver.onEnabled(context)

        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        assertEquals(1, shadowOf(cm).networkCallbacks.size)
    }
}
