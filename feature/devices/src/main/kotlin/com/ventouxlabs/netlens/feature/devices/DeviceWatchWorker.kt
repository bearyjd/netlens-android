package com.ventouxlabs.netlens.feature.devices

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

class DeviceWatchWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface WorkerEntryPoint {
        fun watchRunner(): WatchRunner
    }

    override suspend fun doWork(): Result {
        return try {
            val entryPoint = EntryPointAccessors.fromApplication(
                applicationContext,
                WorkerEntryPoint::class.java,
            )
            when (entryPoint.watchRunner().run()) {
                is WatchOutcome.NoGateway,
                is WatchOutcome.NotWatched,
                is WatchOutcome.Swept,
                -> Result.success()
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (_: Exception) {
            // Transient failure: back off, WorkManager caps attempts; give up after 3.
            // runAttemptCount is 0-indexed (0, 1, 2 = 3 attempts), so use MAX_ATTEMPTS - 1
            if (runAttemptCount < MAX_ATTEMPTS - 1) Result.retry() else Result.success()
        }
    }

    companion object {
        const val UNIQUE_WORK_NAME = "device_watch"
        private const val MAX_ATTEMPTS = 3
    }
}
