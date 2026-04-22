package us.beary.netlens.feature.ping.model

data class PingSummary(
    val transmitted: Int,
    val received: Int,
    val lossPercent: Float,
    val minMs: Float = 0f,
    val avgMs: Float = 0f,
    val maxMs: Float = 0f,
)
