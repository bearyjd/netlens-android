package com.ventouxlabs.netlens.feature.devices

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ventouxlabs.netlens.core.scan.DeviceInventoryRepository
import com.ventouxlabs.netlens.core.scan.identity.MergeSuggestion
import com.ventouxlabs.netlens.core.scan.network.NetworkIdentity
import com.ventouxlabs.netlens.core.scan.network.NetworkKeys
import com.ventouxlabs.netlens.core.network.DisplayText
import com.ventouxlabs.netlens.core.data.dao.DeviceIdentifierDao
import com.ventouxlabs.netlens.core.data.dao.KnownDeviceDao
import com.ventouxlabs.netlens.core.data.dao.WatchedNetworkDao
import com.ventouxlabs.netlens.core.data.di.DefaultDispatcher
import com.ventouxlabs.netlens.core.data.model.DeviceTags
import com.ventouxlabs.netlens.core.data.model.KnownDeviceEntity
import com.ventouxlabs.netlens.core.data.model.KnownDeviceSearch
import com.ventouxlabs.netlens.core.data.model.WatchedNetworkEntity
import com.ventouxlabs.netlens.core.data.preferences.UserPreferencesRepository
import com.ventouxlabs.netlens.feature.devices.model.DeviceDetailsEdit
import com.ventouxlabs.netlens.feature.devices.model.DevicesUiState
import com.ventouxlabs.netlens.feature.devices.model.MAX_DEVICE_NAME_LENGTH
import com.ventouxlabs.netlens.feature.devices.model.MergeSuggestionUi
import com.ventouxlabs.netlens.feature.devices.model.WatchCadence
import com.ventouxlabs.netlens.feature.devices.model.buildMergeSuggestionUi
import com.ventouxlabs.netlens.feature.devices.model.survivorAndLoser
import com.ventouxlabs.netlens.feature.devices.model.displayModel
import com.ventouxlabs.netlens.feature.devices.model.displayName
import com.ventouxlabs.netlens.feature.devices.model.displayOs
import com.ventouxlabs.netlens.feature.devices.model.displayType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.logging.Level
import java.util.logging.Logger
import javax.inject.Inject

@HiltViewModel
class DevicesViewModel @Inject constructor(
    private val knownDeviceDao: KnownDeviceDao,
    private val watchedNetworkDao: WatchedNetworkDao,
    private val networkIdentity: NetworkIdentity,
    private val userPreferences: UserPreferencesRepository,
    private val inventory: DeviceInventoryRepository,
    private val identifierDao: DeviceIdentifierDao,
    private val watchScheduler: WatchScheduler,
    @DefaultDispatcher private val defaultDispatcher: CoroutineDispatcher,
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    private val _selectedDeviceId = MutableStateFlow<Long?>(null)
    private val _activeTags = MutableStateFlow<Set<String>>(emptySet())

    /** Bumped after a refused action so the pair list is rebuilt from the database, not trusted from memory. */
    private val suggestionRefresh = MutableStateFlow(0)

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
                // Preserve cadence/masterWatchEnabled/autoMergeEnabled (folded in below from preferences), the
                // suggestion state (own collector below) and the transient errors, which are set by
                // user actions rather than these flows. This state is rebuilt from scratch on every
                // search keystroke, so anything not copied here would vanish.
                _uiState.update {
                    next.copy(
                        cadence = it.cadence,
                        masterWatchEnabled = it.masterWatchEnabled,
                        autoMergeEnabled = it.autoMergeEnabled,
                        suggestions = it.suggestions,
                        suggestionsOpen = it.suggestionsOpen,
                        suggestionBusyKey = it.suggestionBusyKey,
                        suggestionMessage = it.suggestionMessage,
                        watchError = it.watchError,
                    )
                }
            }
        }
        viewModelScope.launch {
            combine(inventory.pendingMergeSuggestions, knownDeviceDao.getAllDevices(), suggestionRefresh) { pairs, devices, _ ->
                pairs to devices
            }
                .map { (pairs, devices) -> buildSuggestions(pairs, devices) }
                .flowOn(defaultDispatcher)
                .collect { next -> _uiState.update { it.copy(suggestions = next) } }
        }
        viewModelScope.launch {
            combine(
                userPreferences.watchCadenceMinutes,
                userPreferences.watchMasterEnabled,
                userPreferences.autoMergeDevices,
            ) { minutes, master, autoMerge ->
                Triple(WatchCadence.fromMinutes(minutes), master, autoMerge)
            }.collect { (cadence, master, autoMerge) ->
                _uiState.update {
                    it.copy(cadence = cadence, masterWatchEnabled = master, autoMergeEnabled = autoMerge)
                }
            }
        }
    }

    /**
     * Pairs are resolved against the unfiltered inventory, never [DevicesUiState.devices] (which a
     * search or tag filter narrows). A pair whose row is gone is dropped, so the banner count matches
     * what the sheet can show.
     */
    private suspend fun buildSuggestions(
        pairs: List<MergeSuggestion.BetweenRows>,
        devices: List<KnownDeviceEntity>,
    ): List<MergeSuggestionUi> {
        val rows = devices.associateBy { it.id }
        return pairs.mapNotNull { pair ->
            val (survivor, loser) = survivorAndLoser(pair, rows) ?: return@mapNotNull null
            buildMergeSuggestionUi(pair, survivor, loser, identifierDao.getForDevice(loser.id))
        }
    }

    fun openSuggestions() { _uiState.update { it.copy(suggestionsOpen = true, suggestionMessage = null) } }

    fun closeSuggestions() { _uiState.update { it.copy(suggestionsOpen = false, suggestionMessage = null) } }

    /**
     * Merges the pair. When the two rows disagree on trust, [isKnown] is the user's explicit choice and
     * is required: without it nothing is sent, so a merge can never default into (or out of) a known row.
     */
    fun acceptSuggestion(key: String, isKnown: Boolean?) {
        val suggestion = uiState.value.suggestions.find { it.key == key }
        if (uiState.value.suggestionBusyKey != null) return
        when {
            suggestion == null -> refuse(R.string.devices_suggestion_merge_refused)
            suggestion.trustDiffers && isKnown == null ->
                _uiState.update { it.copy(suggestionMessage = R.string.devices_suggestion_choose_trust) }
            else -> runSuggestionAction(key, R.string.devices_suggestion_merge_refused) {
                inventory.acceptSuggestion(suggestion.pair, if (suggestion.trustDiffers) isKnown else null)
            }
        }
    }

    /** Writes DISTINCT for the pair. */
    fun rejectSuggestion(key: String) {
        val suggestion = uiState.value.suggestions.find { it.key == key }
        if (uiState.value.suggestionBusyKey != null) return
        if (suggestion == null) {
            refuse(R.string.devices_suggestion_reject_refused)
            return
        }
        runSuggestionAction(key, R.string.devices_suggestion_reject_refused) { inventory.rejectSuggestion(suggestion.pair) }
    }

    private fun refuse(@StringRes message: Int) {
        _uiState.update { it.copy(suggestionMessage = message) }
        suggestionRefresh.update { it + 1 }
    }

    private fun runSuggestionAction(key: String, @StringRes refusedMessage: Int, action: suspend () -> Boolean) {
        _uiState.update { it.copy(suggestionBusyKey = key, suggestionMessage = null) }
        viewModelScope.launch {
            val done = try {
                action()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logger.log(Level.WARNING, "suggestion action failed", e)
                false
            }
            _uiState.update { it.copy(suggestionBusyKey = null, suggestionMessage = if (done) null else refusedMessage) }
            if (!done) suggestionRefresh.update { it + 1 }
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
                deviceTypeOverride = normalized.deviceTypeOverride,
                osOverride = normalized.osOverride,
                modelOverride = normalized.modelOverride,
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
                    displayName = NetworkKeys.label(networkIdentity.currentSsid(), null),
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

    /** Applies from the next scan: the repository reads the preference per scan. */
    fun setAutoMergeDevices(enabled: Boolean) {
        viewModelScope.launch { userPreferences.setAutoMergeDevices(enabled) }
    }

    fun buildExportText(): String {
        val current = uiState.value
        val sb = StringBuilder()
        sb.appendLine("Device inventory (${current.devices.size} devices):")
        if (current.activeTags.isNotEmpty()) {
            sb.appendLine("Filtered by tags: ${current.activeTags.sorted().joinToString(", ")}")
        }
        current.devices.forEach { device ->
            // displayName() falls back to the scanner-supplied hostname, and macAddress comes off
            // the wire; a control character in either splits this row. The notes branch below
            // already splits on newlines for USER text - the network-supplied fields are the ones
            // that actually carry an adversary's choice. See DisplayText.
            val mac = DisplayText.flatten(device.macAddress) ?: "no-mac"
            val vendor = DisplayText.flatten(device.vendor)?.let { "  Vendor=$it" } ?: ""
            val detected = listOfNotNull(
                device.displayType()?.let { "  Type=$it" },
                device.displayOs()?.let { "  OS=$it" },
                device.displayModel()?.let { "  Model=$it" },
            ).joinToString("")
            val status = if (device.isKnown) "known" else "new"
            val name = DisplayText.flatten(device.displayName()) ?: device.ip
            sb.appendLine("$name  ${device.ip}  $mac  [$status]$vendor$detected")
            DisplayText.flatten(device.location)?.let { sb.appendLine("    Location: $it") }
            val tags = DeviceTags.parse(device.tags)
            if (tags.isNotEmpty()) sb.appendLine("    Tags: ${tags.joinToString(", ")}")
            device.notes?.let { notes ->
                notes.lineSequence().forEach { line -> sb.appendLine("    Note: $line") }
            }
        }
        return sb.toString().trimEnd()
    }

    private companion object {
        val logger: Logger = Logger.getLogger("DevicesViewModel")
    }
}
