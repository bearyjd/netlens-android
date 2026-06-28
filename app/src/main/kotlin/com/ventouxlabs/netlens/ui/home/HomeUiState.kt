package com.ventouxlabs.netlens.ui.home

data class HomeUiState(
    val isConnected: Boolean = false,
    val isVpnActive: Boolean = false,
    val localIp: String? = null,
    val interfaceLabel: String? = null,
    val gatewayIp: String? = null,
    val favoriteRoutes: Set<String> = emptySet(),
    val recentRoutes: List<String> = emptyList(),
    val searchQuery: String = "",
    val isEditingFavorites: Boolean = false,
)
