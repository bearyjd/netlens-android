package com.ventoux.netlens.feature.speedtest.model

data class SpeedProgress(
    val bytesTransferred: Long,
    val elapsedMs: Long,
    val speedMbps: Float,
    val phase: SpeedTestPhase,
)
