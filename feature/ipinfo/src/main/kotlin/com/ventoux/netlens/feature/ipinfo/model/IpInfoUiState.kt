package com.ventoux.netlens.feature.ipinfo.model

sealed interface IpInfoUiState {
    data object Loading : IpInfoUiState
    data class Success(val data: IpApiResponse) : IpInfoUiState
    data class Error(val message: String) : IpInfoUiState
}
