package com.ventouxlabs.netlens.feature.whois.model

sealed interface WhoisUiState {
    data object Idle : WhoisUiState
    data object Loading : WhoisUiState
    data class Success(
        val whois: WhoisResult?,
        val rdns: RdnsResult?,
    ) : WhoisUiState
    data class Error(val message: String) : WhoisUiState
}
