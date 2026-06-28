package com.ventouxlabs.netlens.feature.vpnstatus

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ventouxlabs.netlens.core.network.NetworkMonitor
import com.ventouxlabs.netlens.feature.vpnstatus.model.VpnStatusUiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class VpnStatusViewModel @Inject constructor(
    private val networkMonitor: NetworkMonitor,
) : ViewModel() {

    private val _uiState = MutableStateFlow(VpnStatusUiState())
    val uiState: StateFlow<VpnStatusUiState> = _uiState.asStateFlow()

    init {
        observe()
    }

    private fun observe() {
        viewModelScope.launch {
            combine(networkMonitor.isOnline, networkMonitor.vpnState) { online, vpn ->
                online to vpn
            }.collect { (online, vpn) ->
                _uiState.update { it.copy(isLoading = false, isOnline = online, vpnState = vpn) }
            }
        }
    }
}
