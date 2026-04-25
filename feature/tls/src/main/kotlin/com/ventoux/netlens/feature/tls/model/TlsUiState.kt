package com.ventoux.netlens.feature.tls.model

sealed interface TlsUiState {
    data object Idle : TlsUiState
    data object Loading : TlsUiState
    data class Success(val result: TlsInspectResult) : TlsUiState
    data class Error(val message: String) : TlsUiState
}
