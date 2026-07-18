package com.ventouxlabs.netlens.feature.devices

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.ventouxlabs.netlens.feature.devices.model.WatchCadence
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WatchSchedulerImpl @Inject constructor(
    @ApplicationContext private val context: Context,
) : WatchScheduler {

    override fun apply(isPro: Boolean, masterEnabled: Boolean, cadence: WatchCadence) {
        val wm = WorkManager.getInstance(context)
        when (val action = computeScheduleAction(isPro, masterEnabled, cadence)) {
            is ScheduleAction.Cancel -> wm.cancelUniqueWork(DeviceWatchWorker.UNIQUE_WORK_NAME)
            is ScheduleAction.Enqueue -> {
                val request = PeriodicWorkRequestBuilder<DeviceWatchWorker>(
                    action.cadence.minutes.toLong(), TimeUnit.MINUTES,
                )
                    .setConstraints(
                        Constraints.Builder()
                            .setRequiredNetworkType(NetworkType.CONNECTED)
                            .build(),
                    )
                    .build()
                // UPDATE (not KEEP): a cadence change must replace the existing schedule.
                wm.enqueueUniquePeriodicWork(
                    DeviceWatchWorker.UNIQUE_WORK_NAME,
                    ExistingPeriodicWorkPolicy.UPDATE,
                    request,
                )
            }
        }
    }
}
