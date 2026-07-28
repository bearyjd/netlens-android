package com.ventouxlabs.netlens.feature.devices

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ventouxlabs.netlens.core.data.dao.KnownDeviceDao
import com.ventouxlabs.netlens.core.data.dao.WatchedNetworkDao
import com.ventouxlabs.netlens.core.data.di.DefaultDispatcher
import com.ventouxlabs.netlens.core.data.model.DeviceTags
import com.ventouxlabs.netlens.core.data.model.KnownDeviceSearch
import com.ventouxlabs.netlens.core.data.model.WatchedNetworkEntity
import com.ventouxlabs.netlens.core.data.preferences.UserPreferencesRepository
import com.ventouxlabs.netlens.feature.devices.model.DeviceDetailsEdit
import com.ventouxlabs.netlens.feature.devices.model.DevicesUiState
import com.ventouxlabs.netlens.feature.devices.model.MAX_DEVICE_NAME_LENGTH
import com.ventouxlabs.netlens.feature.devices.model.WatchCadence
import com.ventouxlabs.netlens.feature.devices.model.displayName
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DevicesViewModel @Inject constructor(
    private val knownDeviceDao: KnownDeviceDao,
    private val watchedNetworkDao: WatchedNetworkDao,
    private val networkIdentity: NetworkIdentity,
    private val userPreferences: UserPreferencesRepository,
    private val watchScheduler: WatchScheduler,
    @DefaultDispatcher private val defaultDispatcher: CoroutineDispatcher,
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    private val _selectedDeviceId = MutableStateFlow<Long?>(null)
    private val _activeTags = MutableStateFlow<Set<String>>(emptySet())

    private val _uiState = MutableStateFlow(DevicesUiState())
    val uiState: StateFlow<DevicesUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                knownDeviceDao.getAllDevices(),
                _searchQuery,
                _activeTags,
                _selectedDeviceId,
                watchedNetworkDao.observeAll(),
            ) { allDevices, query, activeTags, selectedId, watched ->
                // Tag chips are derived from the *unfiltered* inventory so filtering never hides
                // the chip you would use to widen the selection again.
                val available = KnownDeviceSearch.allTags(allDevices)
                // Drop selections whose tag no longer exists anywhere (the last device carrying
                // it was deleted or re-tagged). Filtering on a stale tag would empty the list
                // with no chip left on screen to explain why.
                val liveTags = activeTags.filterTo(mutableSetOf()) { tag ->
                    available.any { it.equals(tag, ignoreCase = true) }
                }
                DevicesUiState(
                    devices = allDevices.filter {
                        KnownDeviceSearch.matches(it, query) &&
                            KnownDeviceSearch.matchesAnyTag(it, liveTags)
                    },
                    searchQuery = query,
                    watchedNetworks = watched,
                    selectedDeviceId = selectedId,
                    availableTags = available,
                    activeTags = liveTags,
                )
            }
                // Off the main thread: every keystroke re-parses the tag column of every device
                // (allTags, then matches and matchesAnyTag per row, each running a regex per tag).
                // On a large inventory that is enough work per character to drop frames.
                .flowOn(defaultDispatcher)
                .collect { next ->
                // Preserve cadence/masterWatchEnabled (folded in below from preferences) and the
                // transient watchError, which is set by user actions rather than these flows.
                _uiState.update {
                    next.copy(
                        cadence = it.cadence,
                        masterWatchEnabled = it.masterWatchEnabled,
                        watchError = it.watchError,
                    )
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

    fun toggleTagFilter(tag: String) {
        _activeTags.update { current ->
            val existing = current.firstOrNull { it.equals(tag, ignoreCase = true) }
            if (existing != null) current - existing else current + tag
        }
    }

    fun clearTagFilters() { _activeTags.value = emptySet() }

    fun rename(id: Long, rawName: String) {
        val trimmed = rawName.trim().take(MAX_DEVICE_NAME_LENGTH)
        viewModelScope.launch {
            knownDeviceDao.setCustomName(id, trimmed.ifBlank { null })
        }
    }

    /**
     * Saves the whole user-authored block for a device. Written in one statement so a save
     * can't half-apply, and deliberately scoped to the columns the user owns — a later scan
     * still refreshes hostname/ip/vendor underneath without touching any of this.
     */
    fun saveDetails(id: Long, edit: DeviceDetailsEdit) {
        val normalized = edit.normalized()
        viewModelScope.launch {
            knownDeviceDao.updateUserDetails(
                id = id,
                customName = normalized.customName,
                tags = normalized.tags,
                notes = normalized.notes,
                location = normalized.location,
            )
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
            val gatewayMac = networkIdentity.currentGatewayMac()
            val subnet = networkIdentity.currentSubnet()
            if (gatewayMac == null || subnet == null) {
                _uiState.update { it.copy(watchError = R.string.devices_watch_unresolved) }
                return@launch
            }
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

    fun clearWatchError() {
        _uiState.update { it.copy(watchError = null) }
    }

    fun toggleNetworkWatch(id: Long, enabled: Boolean) {
        viewModelScope.launch { watchedNetworkDao.setWatchEnabled(id, enabled) }
    }

    fun removeWatchedNetwork(id: Long) {
        viewModelScope.launch { watchedNetworkDao.delete(id) }
    }

    /**
     * Persists the new cadence and reschedules with it directly, rather than re-reading
     * [uiState] afterwards — the DataStore write and the state collector that mirrors it
     * back into [uiState] are both async, so a caller-side re-read would race the persist
     * and observe the previous cadence.
     */
    fun setCadence(cadence: WatchCadence, isPro: Boolean) {
        viewModelScope.launch {
            userPreferences.setWatchCadenceMinutes(cadence.minutes)
            watchScheduler.apply(isPro, uiState.value.masterWatchEnabled, cadence)
        }
    }

    /** See [setCadence] for why the new value is threaded through instead of re-read from [uiState]. */
    fun setMasterWatch(enabled: Boolean, isPro: Boolean) {
        viewModelScope.launch {
            userPreferences.setWatchMasterEnabled(enabled)
            watchScheduler.apply(isPro, enabled, uiState.value.cadence)
        }
    }

    fun buildExportText(): String {
        val current = uiState.value
        val sb = StringBuilder()
        sb.appendLine("Device inventory (${current.devices.size} devices):")
        if (current.activeTags.isNotEmpty()) {
            sb.appendLine("Filtered by tags: ${current.activeTags.sorted().joinToString(", ")}")
        }
        current.devices.forEach { device ->
            val mac = device.macAddress ?: "no-mac"
            val vendor = device.vendor?.let { "  Vendor=$it" } ?: ""
            val status = if (device.isKnown) "known" else "new"
            sb.appendLine("${device.displayName()}  ${device.ip}  $mac  [$status]$vendor")
            device.location?.let { sb.appendLine("    Location: $it") }
            val tags = DeviceTags.parse(device.tags)
            if (tags.isNotEmpty()) sb.appendLine("    Tags: ${tags.joinToString(", ")}")
            device.notes?.let { notes ->
                notes.lineSequence().forEach { line -> sb.appendLine("    Note: $line") }
            }
        }
        return sb.toString().trimEnd()
    }
}
