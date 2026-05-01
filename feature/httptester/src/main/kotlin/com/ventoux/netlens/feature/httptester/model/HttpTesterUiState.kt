package com.ventoux.netlens.feature.httptester.model

sealed interface HttpTesterUiState {
    data object Idle : HttpTesterUiState
    data object Loading : HttpTesterUiState
    data class Success(
        val response: HttpResponseResult,
        val requestMethod: String,
        val requestUrl: String,
    ) : HttpTesterUiState
    data class Error(val message: String) : HttpTesterUiState
}
