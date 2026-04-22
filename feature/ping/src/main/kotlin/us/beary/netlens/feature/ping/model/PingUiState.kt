package us.beary.netlens.feature.ping.model

data class PingUiState(
    val host: String = "",
    val results: List<PingResult> = emptyList(),
    val summary: PingSummary? = null,
    val isPinging: Boolean = false,
    val error: String? = null,
)
