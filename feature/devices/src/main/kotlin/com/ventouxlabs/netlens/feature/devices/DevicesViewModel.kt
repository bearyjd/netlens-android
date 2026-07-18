package com.ventouxlabs.netlens.feature.devices

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ventouxlabs.netlens.core.data.dao.KnownDeviceDao
import com.ventouxlabs.netlens.core.data.dao.WatchedNetworkDao
import com.ventouxlabs.netlens.core.data.model.WatchedNetworkEntity
import com.ventouxlabs.netlens.core.data.preferences.UserPreferencesRepository
import com.ventouxlabs.netlens.feature.devices.model.DevicesUiState
import com.ventouxlabs.netlens.feature.devices.model.WatchCadence
import com.ventouxlabs.netlens.feature.devices.model.displayName
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val MAX_NAME_LENGTH = 60

@HiltViewModel
class DevicesViewModel @Inject constructor(
    private val knownDeviceDao: KnownDeviceDao,
    private val watchedNetworkDao: WatchedNetworkDao,
    private val networkIdentity: NetworkIdentity,
    private val userPreferences: UserPreferencesRepository,
    private val watchScheduler: WatchScheduler,
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    private val _selectedDeviceId = MutableStateFlow<Long?>(null)

    private val devicesFlow = combine(
        knownDeviceDao.getAllDevices(),
        _searchQuery,
    ) { devices, query ->
        if (query.isBlank()) devices
        else devices.filter { it.displayName().contains(query, ignoreCase = true) || it.ip.contains(query, ignoreCase = true) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _uiState = MutableStateFlow(DevicesUiState())
    val uiState: StateFlow<DevicesUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                devicesFlow,
                _searchQuery,
                _selectedDeviceId,
                watchedNetworkDao.observeAll(),
            ) { devices, query, selectedId, watched ->
                DevicesUiState(
                    devices = devices,
                    searchQuery = query,
                    watchedNetworks = watched,
                    selectedDeviceId = selectedId,
                )
            }.collect { next ->
                // Preserve cadence/masterWatchEnabled, folded in below from preferences.
                _uiState.update {
                    next.copy(cadence = it.cadence, masterWatchEnabled = it.masterWatchEnabled)
                }
            }
        }
        viewModelScope.launch {
            combine(
                userPreferences.watchCadenceMinutes,
                userPreferences.watchMasterEnabled,
            ) { minutes, master ->
                WatchCadence.fromMinutes(minutes) to master
            }.collect { (cadence, master) ->
                _uiState.update { it.copy(cadence = cadence, masterWatchEnabled = master) }
            }
        }
    }

    fun setSearchQuery(query: String) { _searchQuery.value = query }

    fun selectDevice(id: Long?) { _selectedDeviceId.value = id }

    fun rename(id: Long, rawName: String) {
        val trimmed = rawName.trim().take(MAX_NAME_LENGTH)
        viewModelScope.launch {
            knownDeviceDao.setCustomName(id, trimmed.ifBlank { null })
        }
    }

    fun toggleKnown(id: Long) {
        viewModelScope.launch {
            val device = uiState.value.devices.find { it.id == id } ?: return@launch
            knownDeviceDao.setKnown(id, !device.isKnown)
        }
    }

    fun delete(id: Long) {
        viewModelScope.launch { knownDeviceDao.delete(id) }
    }

    fun watchCurrentNetwork() {
        viewModelScope.launch {
            val gatewayMac = networkIdentity.currentGatewayMac() ?: return@launch
            val subnet = networkIdentity.currentSubnet() ?: return@launch
            watchedNetworkDao.upsert(
                WatchedNetworkEntity(
                    displayName = networkIdentity.currentSsid(),
                    gatewayMac = gatewayMac,
                    subnet = subnet,
                    watchEnabled = true,
                ),
            )
        }
    }

    fun toggleNetworkWatch(id: Long, enabled: Boolean) {
        viewModelScope.launch { watchedNetworkDao.setWatchEnabled(id, enabled) }
    }

    fun removeWatchedNetwork(id: Long) {
        viewModelScope.launch { watchedNetworkDao.delete(id) }
    }

    fun setCadence(cadence: WatchCadence) {
        viewModelScope.launch { userPreferences.setWatchCadenceMinutes(cadence.minutes) }
    }

    fun setMasterWatch(enabled: Boolean) {
        viewModelScope.launch { userPreferences.setWatchMasterEnabled(enabled) }
    }

    fun applySchedule(isPro: Boolean) {
        watchScheduler.apply(isPro, uiState.value.masterWatchEnabled, uiState.value.cadence)
    }
}
