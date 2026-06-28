package com.ventouxlabs.netlens.feature.ipinfo

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import com.ventouxlabs.netlens.core.data.dao.IpInfoHistoryDao
import com.ventouxlabs.netlens.core.data.model.IpInfoHistoryEntry
import com.ventouxlabs.netlens.core.data.preferences.UserPreferencesRepository
import com.ventouxlabs.netlens.feature.ipinfo.data.IpInfoRepository
import com.ventouxlabs.netlens.feature.ipinfo.data.ReputationClient
import com.ventouxlabs.netlens.feature.ipinfo.model.IpInfoUiState
import javax.inject.Inject

@HiltViewModel
class IpInfoViewModel @Inject constructor(
    private val repository: IpInfoRepository,
    private val reputationClient: ReputationClient,
    private val ipInfoHistoryDao: IpInfoHistoryDao,
    private val userPreferencesRepository: UserPreferencesRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow<IpInfoUiState>(IpInfoUiState.Loading)
    val uiState: StateFlow<IpInfoUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val consented = userPreferencesRepository.ipInfoConsentGranted.first()
            if (consented) {
                refresh()
            } else {
                _uiState.value = IpInfoUiState.ConsentRequired
            }
        }
    }

    fun grantConsent() {
        viewModelScope.launch {
            userPreferencesRepository.setIpInfoConsent(true)
            refresh()
        }
    }

    fun buildExportText(): String {
        val current = _uiState.value
        if (current !is IpInfoUiState.Success) return ""
        val d = current.data
        val sb = StringBuilder()
        sb.appendLine("IP Info for ${d.ip}:")
        sb.appendLine("Hostname: ${d.hostname}")
        sb.appendLine("Org: ${d.orgName}")
        sb.appendLine("AS: ${d.asNumber}")
        sb.appendLine("Location: ${d.city}, ${d.region}, ${d.country}")
        sb.appendLine("Postal: ${d.postal}")
        sb.appendLine("Timezone: ${d.timezone}")
        sb.appendLine("Coordinates: ${d.latitude}, ${d.longitude}")
        return sb.toString().trimEnd()
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.value = IpInfoUiState.Loading
            repository.fetchIpInfo()
                .onSuccess { data ->
                    _uiState.value = IpInfoUiState.Success(data)
                    ipInfoHistoryDao.insert(
                        IpInfoHistoryEntry(
                            ip = data.ip,
                            isp = data.orgName.ifBlank { null },
                            org = data.org.ifBlank { null },
                            countryCode = data.country.ifBlank { null },
                            city = data.city.ifBlank { null },
                            isVpn = false,
                        ),
                    )
                    fetchReputation(data.ip)
                }
                .onFailure { error ->
                    _uiState.value = IpInfoUiState.Error(
                        error.localizedMessage ?: "Unknown error",
                    )
                }
        }
    }

    fun saveApiKey(key: String) {
        viewModelScope.launch {
            userPreferencesRepository.setAbuseIpDbApiKey(key.trim())
            val current = _uiState.value
            if (current is IpInfoUiState.Success && key.isNotBlank()) {
                fetchReputation(current.data.ip)
            }
        }
    }

    private suspend fun fetchReputation(ip: String) {
        val apiKey = userPreferencesRepository.abuseIpDbApiKey.first()
        if (apiKey.isBlank()) return
        val current = _uiState.value
        if (current !is IpInfoUiState.Success) return
        _uiState.value = current.copy(reputationLoading = true, reputationError = null)
        reputationClient.checkReputation(ip, apiKey)
            .onSuccess { result ->
                val state = _uiState.value
                if (state is IpInfoUiState.Success) {
                    _uiState.value = state.copy(reputation = result, reputationLoading = false)
                }
            }
            .onFailure { error ->
                val state = _uiState.value
                if (state is IpInfoUiState.Success) {
                    _uiState.value = state.copy(
                        reputationLoading = false,
                        reputationError = error.localizedMessage ?: "Reputation check failed",
                    )
                }
            }
    }
}
