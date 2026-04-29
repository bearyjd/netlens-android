package com.ventoux.netlens.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ventoux.netlens.core.data.preferences.UserPreferencesRepository
import com.ventoux.netlens.core.network.NetworkInterfaceProvider
import com.ventoux.netlens.core.network.NetworkMonitor
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    networkMonitor: NetworkMonitor,
    private val networkInterfaceProvider: NetworkInterfaceProvider,
    private val userPreferencesRepository: UserPreferencesRepository,
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    private val _isEditingFavorites = MutableStateFlow(false)

    val uiState: StateFlow<HomeUiState> = combine(
        networkMonitor.isOnline,
        networkMonitor.isVpnActive,
        userPreferencesRepository.favoriteToolRoutes,
        userPreferencesRepository.recentToolRoutes,
    ) { online, vpn, favorites, recents ->
        val iface = if (online) networkInterfaceProvider.getActiveNetworkInterface() else null
        HomeUiState(
            isConnected = online,
            isVpnActive = vpn,
            localIp = iface?.ip,
            interfaceLabel = iface?.label,
            gatewayIp = iface?.gateway,
            favoriteRoutes = favorites,
            recentRoutes = recents,
        )
    }.combine(_searchQuery) { state, query ->
        state.copy(searchQuery = query)
    }.combine(_isEditingFavorites) { state, editing ->
        state.copy(isEditingFavorites = editing)
    }.flowOn(Dispatchers.IO)
    .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = HomeUiState(),
    )

    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
    }

    fun toggleFavorite(route: String) {
        viewModelScope.launch {
            userPreferencesRepository.toggleFavorite(route)
        }
    }

    fun setEditingFavorites(editing: Boolean) {
        _isEditingFavorites.value = editing
    }

    fun recordToolUsage(route: String) {
        viewModelScope.launch {
            userPreferencesRepository.recordToolUsage(route)
        }
    }
}
