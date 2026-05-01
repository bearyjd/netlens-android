package com.ventoux.netlens.feature.speedtest.engine

import com.ventoux.netlens.feature.speedtest.model.SpeedProgress
import kotlinx.coroutines.flow.Flow

interface SpeedTestEngine {
    fun measureDownload(): Flow<SpeedProgress>
    fun measureUpload(): Flow<SpeedProgress>
    suspend fun measureLatency(): Long
}
