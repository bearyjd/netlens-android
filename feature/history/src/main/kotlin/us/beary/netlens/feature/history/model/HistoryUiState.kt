package us.beary.netlens.feature.history.model

data class HistoryUiState(
    val items: List<HistoryItem> = emptyList(),
    val searchQuery: String = "",
    val selectedFilter: ToolFilter = ToolFilter.All,
    val isLoading: Boolean = true,
    val error: String? = null,
)

data class HistoryItem(
    val id: Long,
    val toolName: String,
    val primaryLabel: String,
    val secondarySummary: String,
    val timestamp: Long,
    val toolFilter: ToolFilter,
)
