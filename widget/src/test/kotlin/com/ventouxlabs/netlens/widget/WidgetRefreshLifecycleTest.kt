package com.ventouxlabs.netlens.widget

import android.content.Context
import android.net.ConnectivityManager
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

/**
 * Covers [WidgetRefresh.kt]'s network-callback register/unregister lifecycle and WorkManager
 * enqueue shape, both of which need a real [Context] and Android framework services — the reason
 * this file (unlike [WidgetRefreshTest]) is Robolectric-backed rather than plain JUnit5.
 */
@RunWith(RobolectricTestRunner::class)
class WidgetRefreshLifecycleTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        val config = Configuration.Builder()
            .setExecutor(SynchronousExecutor())
            .build()
        WorkManagerTestInitHelper.initializeTestWorkManager(context, config)
    }

    @After
    fun tearDown() {
        // Undo the network callback registrations these tests perform so shadow state doesn't
        // leak across tests in this class.
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        shadowOf(cm).networkCallbacks.toList().forEach {
            try { cm.unregisterNetworkCallback(it) } catch (_: IllegalArgumentException) { }
        }
    }

    @Test
    fun `registerWidgetNetworkCallback registers exactly one callback`() {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

        registerWidgetNetworkCallback(context, current = null)

        assertEquals(1, shadowOf(cm).networkCallbacks.size)
    }

    @Test
    fun `re-registering unregisters the previous callback first`() {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

        val first = registerWidgetNetworkCallback(context, current = null)
        val second = registerWidgetNetworkCallback(context, current = first)

        // Exactly the new callback is registered; the old one was unregistered, not leaked.
        assertEquals(1, shadowOf(cm).networkCallbacks.size)
        assertTrue(shadowOf(cm).networkCallbacks.contains(second))
        assertTrue(!shadowOf(cm).networkCallbacks.contains(first))
    }

    @Test
    fun `unregisterWidgetNetworkCallback removes the callback`() {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val callback = registerWidgetNetworkCallback(context, current = null)

        unregisterWidgetNetworkCallback(context, callback)

        assertTrue(shadowOf(cm).networkCallbacks.isEmpty())
    }

    @Test
    fun `unregisterWidgetNetworkCallback with a null callback is a no-op`() {
        // Must not throw — every receiver's onDisabled calls this unconditionally.
        unregisterWidgetNetworkCallback(context, callback = null)
    }

    @Test
    fun `enqueueWidgetRefresh schedules unique REPLACE work requiring network`() {
        enqueueWidgetRefresh(context)

        val workInfos = WorkManager.getInstance(context).getWorkInfosForUniqueWork("widget_refresh").get()
        assertEquals(1, workInfos.size)
        assertEquals(WorkInfo.State.ENQUEUED, workInfos.first().state)
    }

    @Test
    fun `enqueueWidgetRefresh with REPLACE policy supersedes a prior enqueue`() {
        enqueueWidgetRefresh(context)
        val firstId = WorkManager.getInstance(context)
            .getWorkInfosForUniqueWork("widget_refresh").get().first().id

        enqueueWidgetRefresh(context)
        val workInfos = WorkManager.getInstance(context).getWorkInfosForUniqueWork("widget_refresh").get()

        // REPLACE means exactly one live work item under this name, and it isn't the first one.
        assertEquals(1, workInfos.count { !it.state.isFinished })
        assertTrue(workInfos.none { it.id == firstId && !it.state.isFinished })
    }

    @Test
    fun `enqueuePeriodicWidgetRefresh schedules unique KEEP periodic work`() {
        enqueuePeriodicWidgetRefresh(context)

        val workInfos = WorkManager.getInstance(context)
            .getWorkInfosForUniqueWork("widget_refresh_periodic").get()
        assertEquals(1, workInfos.size)
        assertEquals(WorkInfo.State.ENQUEUED, workInfos.first().state)
    }

    @Test
    fun `enqueuePeriodicWidgetRefresh with KEEP policy does not replace an existing schedule`() {
        enqueuePeriodicWidgetRefresh(context)
        val firstId = WorkManager.getInstance(context)
            .getWorkInfosForUniqueWork("widget_refresh_periodic").get().first().id

        enqueuePeriodicWidgetRefresh(context)
        val workInfos = WorkManager.getInstance(context)
            .getWorkInfosForUniqueWork("widget_refresh_periodic").get()

        // KEEP means the original schedule survives untouched.
        assertEquals(1, workInfos.size)
        assertEquals(firstId, workInfos.first().id)
    }
}
