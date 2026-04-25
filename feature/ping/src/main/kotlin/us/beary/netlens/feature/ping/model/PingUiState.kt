package us.beary.netlens.feature.ping.model

data class PingUiState(
    val host: String = "",
    val results: List<PingResult> = emptyList(),
    val summary: PingSummary? = null,
    val isPinging: Boolean = false,
    val error: String? = null,
    val mode: PingMode = PingMode.FIXED,
    val totalSent: Int = 0,
    val totalReceived: Int = 0,
    val elapsedMs: Long = 0,
)
