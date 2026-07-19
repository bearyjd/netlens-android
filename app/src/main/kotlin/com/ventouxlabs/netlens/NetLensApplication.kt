package com.ventouxlabs.netlens

import android.app.Application
import androidx.work.Configuration
import com.ventouxlabs.netlens.core.billing.ProStatus
import com.ventouxlabs.netlens.core.data.preferences.UserPreferencesRepository
import com.ventouxlabs.netlens.feature.devices.WatchScheduler
import com.ventouxlabs.netlens.feature.devices.model.WatchCadence
import com.ventouxlabs.netlens.widget.enqueuePeriodicWidgetRefresh
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class NetLensApplication : Application(), Configuration.Provider {

    @Inject lateinit var watchScheduler: WatchScheduler
    @Inject lateinit var userPreferences: UserPreferencesRepository
    @Inject lateinit var proStatus: ProStatus

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    // Providing the WorkManager configuration here (paired with removing the default
    // androidx.startup initializer in the manifest) defers WorkManager initialization —
    // and the synchronous open of its Room database — from process start to first use.
    // The default WorkerFactory is sufficient: workers self-inject via Hilt EntryPoints
    // (DeviceWatchWorker/WidgetRefreshWorker), so no HiltWorkerFactory is needed.
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setMinimumLoggingLevel(android.util.Log.INFO)
            .build()

    override fun onCreate() {
        super.onCreate()
        // Belt-and-suspenders for users who already have widgets placed:
        // onEnabled() only fires on first placement, so without this the
        // periodic refresh would only schedule after a fresh widget add.
        // ExistingPeriodicWorkPolicy.KEEP makes repeat calls idempotent.
        enqueuePeriodicWidgetRefresh(this)

        // Reconcile the background watch at start: WatchSchedulerImpl enqueues only when
        // Pro AND the master toggle is on, and cancels otherwise (e.g. Pro lost).
        appScope.launch {
            val masterEnabled = userPreferences.watchMasterEnabled.first()
            val cadence = WatchCadence.fromMinutes(userPreferences.watchCadenceMinutes.first())
            watchScheduler.apply(proStatus.isPro.value, masterEnabled, cadence)
        }
    }
}
