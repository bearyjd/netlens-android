package com.ventouxlabs.netlens.feature.ipinfo.model

sealed interface IpInfoUiState {
    data object Loading : IpInfoUiState
    data object ConsentRequired : IpInfoUiState
    data class Success(
        val data: IpInfoResponse,
        val reputation: ReputationResult? = null,
        val reputationLoading: Boolean = false,
        val reputationError: String? = null,
    ) : IpInfoUiState
    data class Error(val message: String) : IpInfoUiState
}
