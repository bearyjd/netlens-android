package com.ventoux.netlens.ui.home

import com.ventoux.netlens.core.data.preferences.UserPreferencesRepository

data class HomeUiState(
    val isConnected: Boolean = false,
    val isVpnActive: Boolean = false,
    val localIp: String? = null,
    val interfaceLabel: String? = null,
    val gatewayIp: String? = null,
    val favoriteRoutes: Set<String> = UserPreferencesRepository.DEFAULT_FAVORITES,
    val recentRoutes: List<String> = emptyList(),
    val searchQuery: String = "",
    val isEditingFavorites: Boolean = false,
)
