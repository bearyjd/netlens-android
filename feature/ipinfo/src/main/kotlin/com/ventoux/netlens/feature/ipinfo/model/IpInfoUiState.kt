package com.ventoux.netlens.feature.ipinfo.model

sealed interface IpInfoUiState {
    data object Loading : IpInfoUiState
    data object ConsentRequired : IpInfoUiState
    data class Success(val data: IpInfoResponse) : IpInfoUiState
    data class Error(val message: String) : IpInfoUiState
}
