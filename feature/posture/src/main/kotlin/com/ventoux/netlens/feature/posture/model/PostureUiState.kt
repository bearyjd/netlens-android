package com.ventoux.netlens.feature.posture.model

sealed interface PostureUiState {
    data object Loading : PostureUiState
    data class Scored(val score: PostureScore) : PostureUiState
    data class Error(val message: String) : PostureUiState
    data object Disconnected : PostureUiState
}
