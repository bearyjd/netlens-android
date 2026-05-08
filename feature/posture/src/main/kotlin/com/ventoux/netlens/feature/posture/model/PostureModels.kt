package com.ventoux.netlens.feature.posture.model

import com.ventoux.netlens.core.network.VpnState

data class PostureScore(
    val grade: String,
    val numericScore: Int,
    val factors: List<FactorResult>,
    val vpnState: VpnState = VpnState.None,
)

data class FactorResult(
    val factor: PostureFactor,
    val score: Int,
    val label: String,
    val detail: String,
    val severity: Severity,
)

enum class Severity { Good, Moderate, Poor, Critical, Unavailable }

enum class PostureFactor(
    val displayName: String,
    val weight: Float,
) {
    Encryption("Wi-Fi Encryption", 0.45f),
    DeviceCount("Connected Devices", 0.30f),
    VpnStatus("VPN Protection", 0.25f),
}
