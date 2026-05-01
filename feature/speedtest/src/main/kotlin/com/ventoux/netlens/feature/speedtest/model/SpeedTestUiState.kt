package com.ventoux.netlens.feature.speedtest.model

data class SpeedTestUiState(
    val phase: SpeedTestPhase = SpeedTestPhase.IDLE,
    val downloadMbps: Float = 0f,
    val uploadMbps: Float = 0f,
    val latencyMs: Long = 0,
    val progress: Float = 0f,
    val error: String? = null,
    val isRunning: Boolean = false,
)
