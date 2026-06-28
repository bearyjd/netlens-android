package com.ventouxlabs.netlens.feature.history.model

import com.ventouxlabs.netlens.core.data.model.HistoryDetailData

sealed interface HistoryDetailState {
    data object Loading : HistoryDetailState
    data class Loaded(val item: HistoryItem, val data: HistoryDetailData) : HistoryDetailState
    data class Error(val message: String) : HistoryDetailState
}
