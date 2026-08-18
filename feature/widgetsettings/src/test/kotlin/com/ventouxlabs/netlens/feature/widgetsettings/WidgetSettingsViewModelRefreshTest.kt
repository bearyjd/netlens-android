package com.ventouxlabs.netlens.feature.widgetsettings

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import com.ventouxlabs.netlens.core.data.preferences.UserPreferencesRepository
import com.ventouxlabs.netlens.core.data.testing.FakeDataStore
import com.ventouxlabs.netlens.core.data.testing.FakeKeyValueStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * `refreshWidgets()` must trigger [com.ventouxlabs.netlens.widget.WidgetRefreshWorker] — the
 * only place that fetches fresh data and applies consent-based clearing (e.g. the public-IP
 * block on revoked ipinfo consent) — not merely repaint the widget from stale cached state.
 * Needs Robolectric + [WorkManagerTestInitHelper] for a real WorkManager instance, the same
 * seam as [com.ventouxlabs.netlens.widget.WidgetRefreshLifecycleTest].
 */
@RunWith(RobolectricTestRunner::class)
class WidgetSettingsViewModelRefreshTest {

    private lateinit var viewModel: WidgetSettingsViewModel
    private lateinit var workManager: WorkManager

    @Before
    fun setUp() {
        val application = ApplicationProvider.getApplicationContext<Application>()
        val config = Configuration.Builder()
            .setExecutor(SynchronousExecutor())
            .build()
        WorkManagerTestInitHelper.initializeTestWorkManager(application, config)
        workManager = WorkManager.getInstance(application)

        val userPreferencesRepository = UserPreferencesRepository(FakeDataStore(), FakeKeyValueStore())
        viewModel = WidgetSettingsViewModel(application, userPreferencesRepository)
    }

    @Test
    fun `refreshWidgets enqueues the widget_refresh unique work`() {
        viewModel.refreshWidgets()

        val workInfos = workManager.getWorkInfosForUniqueWork("widget_refresh").get()

        assertEquals(1, workInfos.size)
        assertTrue(workInfos.single().state == WorkInfo.State.ENQUEUED || workInfos.single().state == WorkInfo.State.SUCCEEDED)
    }
}
