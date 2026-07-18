package com.ventouxlabs.netlens.feature.speedtest.engine

import com.ventouxlabs.netlens.feature.speedtest.model.SpeedProgress
import kotlinx.coroutines.flow.Flow

interface SpeedTestEngine {
    fun measureDownload(): Flow<SpeedProgress>
    fun measureUpload(): Flow<SpeedProgress>
    suspend fun measureLatency(): Long

    companion object {
        /** Duration of each measurement phase; callers convert [SpeedProgress.elapsedMs] into a 0f..1f fraction against this. */
        const val MEASURE_WINDOW_MS = 8_000L
    }
}
