package com.ventouxlabs.netlens.feature.devices

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ventouxlabs.netlens.core.scan.DeviceInventoryRepository
import com.ventouxlabs.netlens.core.scan.identity.MergeSuggestion
import com.ventouxlabs.netlens.core.scan.network.NetworkIdentity
import com.ventouxlabs.netlens.core.scan.network.NetworkKeys
import com.ventouxlabs.netlens.core.network.DisplayText
import com.ventouxlabs.netlens.core.data.dao.KnownDeviceDao
import com.ventouxlabs.netlens.core.data.dao.WatchedNetworkDao
import com.ventouxlabs.netlens.core.data.di.DefaultDispatcher
import com.ventouxlabs.netlens.core.data.model.DeviceTags
import com.ventouxlabs.netlens.core.data.model.DeviceIdentifierEntity
import com.ventouxlabs.netlens.core.data.model.KnownDeviceEntity
import com.ventouxlabs.netlens.core.data.model.KnownDeviceSearch
import com.ventouxlabs.netlens.core.data.model.SaveOutcome
import com.ventouxlabs.netlens.core.data.model.UserDetails
import com.ventouxlabs.netlens.core.data.model.userDetails
import com.ventouxlabs.netlens.core.data.model.WatchedNetworkEntity
import com.ventouxlabs.netlens.core.data.preferences.UserPreferencesRepository
import com.ventouxlabs.netlens.feature.devices.model.DetailDraft
import com.ventouxlabs.netlens.feature.devices.model.DetailNotice
import com.ventouxlabs.netlens.feature.devices.model.DeviceDetailsEdit
import com.ventouxlabs.netlens.feature.devices.model.DiscardedEdit
import com.ventouxlabs.netlens.feature.devices.model.UserField
import com.ventouxlabs.netlens.feature.devices.model.DevicesUiState
import com.ventouxlabs.netlens.feature.devices.model.MergeSuggestionUi
import com.ventouxlabs.netlens.feature.devices.model.WatchCadence
import com.ventouxlabs.netlens.feature.devices.model.bidiIsolate
import com.ventouxlabs.netlens.feature.devices.model.buildMergeSuggestionUi
import com.ventouxlabs.netlens.feature.devices.model.safeDisplay
import com.ventouxlabs.netlens.feature.devices.model.survivorAndLoser
import com.ventouxlabs.netlens.feature.devices.model.displayModel
import com.ventouxlabs.netlens.feature.devices.model.displayName
import com.ventouxlabs.netlens.feature.devices.model.displayOs
import com.ventouxlabs.netlens.feature.devices.model.displayType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
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
    private val watchScheduler: WatchScheduler,
    @DefaultDispatcher private val defaultDispatcher: CoroutineDispatcher,
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    private val _selectedDeviceId = MutableStateFlow<Long?>(null)
    private val _activeTags = MutableStateFlow<Set<String>>(emptySet())

    /** Bumped after a refused action so the pair list is rebuilt from the database, not trusted from memory. */
    private val suggestionRefresh = MutableStateFlow(0)

    /** Selected id whose disappearance was already looked up, so one vanish raises one notice. */
    private var vanishHandledFor: Long? = null
    private var draftToken = 0

    /**
     * Editor-session epoch: bumped on every (re)selection, close and rebase. A save captures it at
     * launch and drops its outcome if it changed, so a slow save never closes, redirects or drafts
     * over an editor the user opened after it.
     */
    private var editorSession = 0
    private var saveJob: Job? = null

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
                    selectedDevice = selectedId?.let { id -> allDevices.find { it.id == id } },
                    availableTags = available,
                    activeTags = liveTags,
                )
            }
                // Off the main thread: every keystroke re-parses the tag column of every device
                // (allTags, then matches and matchesAnyTag per row, each running a regex per tag).
                // On a large inventory that is enough work per character to drop frames.
                .flowOn(defaultDispatcher)
                .collect { next ->
                val previous = _uiState.value.selectedDevice?.takeIf { it.id == next.selectedDeviceId }
                val vanished = next.selectedDeviceId != null && next.selectedDevice == null && previous != null
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
                        selectedDevice = next.selectedDevice ?: previous,
                        detailDraft = it.detailDraft,
                        detailSaving = it.detailSaving,
                        detailNotice = it.detailNotice,
                    )
                }
                if (next.selectedDevice != null) vanishHandledFor = null
                val id = next.selectedDeviceId
                if (vanished && id != null && vanishHandledFor != id) {
                    vanishHandledFor = id
                    viewModelScope.launch { noticeVanished(id) }
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
        val identifiers = mutableMapOf<Long, List<DeviceIdentifierEntity>>() // one query per row per emission
        return pairs.mapNotNull { pair ->
            val (survivor, loser) = survivorAndLoser(pair, rows) ?: return@mapNotNull null
            buildMergeSuggestionUi(pair, survivor, loser, identifiers.getOrPut(loser.id) { inventory.identifiersFor(loser.id) })
        }
    }

    fun openSuggestions() { _uiState.update { it.copy(suggestionsOpen = true, suggestionMessage = null) } }

    fun closeSuggestions() { _uiState.update { it.copy(suggestionsOpen = false, suggestionMessage = null) } }

    /**
     * Merges the pair. When the two rows disagree on trust, [isKnown] is the user's explicit choice and
     * is required: without it nothing is sent, so a merge can never default into (or out of) a known row.
     * When the merge drops a value or decides trust ([MergeSuggestionUi.requiresConfirmation]) it also
     * needs [confirmed], set only by the confirmation dialog. The survivor and preview hash the user saw
     * are sent along, so the repository refuses a merge whose rows changed since.
     */
    fun acceptSuggestion(key: String, isKnown: Boolean?, confirmed: Boolean) {
        val suggestion = uiState.value.suggestions.find { it.key == key }
        if (uiState.value.suggestionBusyKey != null) return
        when {
            suggestion == null -> refuse(R.string.devices_suggestion_merge_refused)
            suggestion.trustDiffers && isKnown == null ->
                _uiState.update { it.copy(suggestionMessage = R.string.devices_suggestion_choose_trust) }
            suggestion.requiresConfirmation && !confirmed ->
                _uiState.update { it.copy(suggestionMessage = R.string.devices_suggestion_confirm_required) }
            else -> runSuggestionAction(key, R.string.devices_suggestion_merge_refused) {
                inventory.acceptSuggestion(
                    suggestion.pair,
                    if (suggestion.trustDiffers) isKnown else null,
                    suggestion.survivor.id,
                    suggestion.previewHash,
                )
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
            if (done) {
                // Stay busy while the list re-emits, so a tap aimed at the pair that was just here
                // cannot land on the card that slides into its place.
                _uiState.update { it.copy(suggestionMessage = null) }
                delay(SETTLE_MS)
                _uiState.update { it.copy(suggestionBusyKey = null) }
            } else {
                _uiState.update { it.copy(suggestionBusyKey = null, suggestionMessage = refusedMessage) }
                suggestionRefresh.update { it + 1 }
            }
        }
    }

    fun setSearchQuery(query: String) { _searchQuery.value = query }

    /** A user (re)selection: any draft or notice belonged to the previous editor session. */
    fun selectDevice(id: Long?) {
        editorSession += 1
        vanishHandledFor = null
        // detailSaving stays true while an earlier save is still in flight: Save stays disabled.
        _uiState.update { it.copy(detailDraft = null, detailNotice = null) }
        _selectedDeviceId.value = id
    }

    fun toggleTagFilter(tag: String) {
        _activeTags.update { current ->
            val existing = current.firstOrNull { it.equals(tag, ignoreCase = true) }
            if (existing != null) current - existing else current + tag
        }
    }

    fun clearTagFilters() { _activeTags.value = emptySet() }

    /**
     * Guarded save of the detail editor. [base] is the row's stored values when the editor opened,
     * [edit] what is on screen now. The sheet stays open until the outcome arrives:
     *  - Saved closes it.
     *  - Merged / Conflict NEVER write to another row automatically (a spoofed identifier could have
     *    redirected the merge): the target row is selected and the sheet gets a draft of the user's
     *    changed fields over the target's current values, to review and save again.
     *  - Gone keeps what was typed and says it was not saved.
     */
    fun saveDetails(id: Long, base: UserDetails, edit: DeviceDetailsEdit) {
        // One save at a time, across editor sessions: a reopened sheet cannot start a second one.
        if (saveJob?.isActive == true) return
        _uiState.update { it.copy(detailSaving = true, detailNotice = null) }
        val changes = edit.changesOver(base)
        val session = editorSession
        saveJob = viewModelScope.launch {
            val outcome = try {
                knownDeviceDao.saveUserDetails(id, base, changes)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logger.log(Level.WARNING, "device details save failed", e)
                null
            }
            if (session != editorSession) {
                // The user closed or reopened the editor meanwhile: the result belongs to nobody.
                _uiState.update { it.copy(detailSaving = false) }
                return@launch
            }
            when (outcome) {
                null -> notice(DetailNotice(R.string.devices_detail_save_failed))
                SaveOutcome.Saved -> {
                    editorSession += 1
                    _uiState.update { it.copy(detailSaving = false, detailDraft = null, detailNotice = null) }
                    _selectedDeviceId.value = null
                }
                is SaveOutcome.Merged -> rebaseOrFail(outcome.survivorId, base, changes, merged = true)
                is SaveOutcome.Conflict -> rebaseOrFail(id, base, changes, merged = false)
                SaveOutcome.Gone -> notice(DetailNotice(R.string.devices_detail_gone))
            }
        }
    }

    private fun notice(notice: DetailNotice) {
        _uiState.update { it.copy(detailSaving = false, detailNotice = notice) }
    }

    // A failed lookup must not escape the launch with Save stuck disabled: same mapping as the save.
    private suspend fun rebaseOrFail(targetId: Long, base: UserDetails, changes: UserDetails, merged: Boolean) {
        try {
            rebase(targetId, base, changes, merged)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logger.log(Level.WARNING, "redirecting a device details save failed", e)
            notice(DetailNotice(R.string.devices_detail_save_failed))
        }
    }

    /**
     * Opens [targetId] with a draft and writes nothing. A changed field is applied only where the
     * target still holds the value the user started from; where the target's value differs (a merge
     * combined or filled it, someone else edited it) the target's value stays and the typed text is
     * listed in the notice instead, so a draft never silently overwrites what the merge kept.
     */
    private suspend fun rebase(targetId: Long, base: UserDetails, changes: UserDetails, merged: Boolean) {
        val target = knownDeviceDao.getById(targetId) ?: return notice(DetailNotice(R.string.devices_detail_gone))
        val current = target.userDetails()
        val (applicable, blocked) = base.changedIn(changes).partition { current[it] == base[it] }
        val draft = current.withFieldsFrom(changes, applicable.toSet())
        val discarded = blocked.map { DiscardedEdit(UserField.of(it), safeDisplay(changes[it], MAX_DISCARDED_DISPLAY).orEmpty()) }
        val message = if (merged) {
            DetailNotice(R.string.devices_detail_merged, displayNameOf(target), discarded)
        } else {
            DetailNotice(R.string.devices_detail_changed, discarded = discarded)
        }
        editorSession += 1
        draftToken += 1
        vanishHandledFor = null
        _uiState.update {
            it.copy(
                detailSaving = false,
                detailNotice = message,
                detailDraft = DetailDraft(targetId, current, DeviceDetailsEdit.from(draft), draftToken),
                selectedDevice = target,
            )
        }
        _selectedDeviceId.value = targetId
    }

    /** The selected row vanished under an open editor: say where it went, keep the editor open. */
    private suspend fun noticeVanished(id: Long) {
        val target = try {
            knownDeviceDao.mergedInto(id)?.let { knownDeviceDao.getById(it) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logger.log(Level.WARNING, "merged-row lookup failed", e)
            null
        }
        if (_selectedDeviceId.value != id) return
        val message = target?.let { DetailNotice(R.string.devices_detail_vanished_merged, displayNameOf(it)) }
            ?: DetailNotice(R.string.devices_detail_vanished_gone)
        _uiState.update { it.copy(detailNotice = message) }
    }

    // Device names can carry a hostile hostname: flattened, format characters removed, isolated.
    private fun displayNameOf(device: KnownDeviceEntity): String =
        bidiIsolate(safeDisplay(device.displayName(), MAX_NOTICE_NAME) ?: device.ip)

    /**
     * Flips trust with a compare-and-set against the value the user saw, so a tap on stale state
     * cannot undo a change made since (a merge's trust choice, another screen).
     */
    fun toggleKnown(id: Long) {
        val state = uiState.value
        val device = state.selectedDevice?.takeIf { it.id == id } ?: state.devices.find { it.id == id } ?: return
        viewModelScope.launch {
            val missed = knownDeviceDao.setKnownIf(id, !device.isKnown, device.isKnown) == 0
            // Only that row's open editor explains a miss; anywhere else the list simply refreshes.
            if (missed && _selectedDeviceId.value == id) {
                _uiState.update { it.copy(detailNotice = DetailNotice(R.string.devices_detail_trust_changed)) }
            }
        }
    }

    fun delete(id: Long) {
        // Close first, so the row's disappearance is not reported to an editor as a lost save.
        if (_selectedDeviceId.value == id) selectDevice(null)
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

    internal companion object {
        /** How long taps stay ignored after a successful suggestion action. */
        const val SETTLE_MS = 400L
        private const val MAX_NOTICE_NAME = 63
        private const val MAX_DISCARDED_DISPLAY = 160
        private val logger: Logger = Logger.getLogger("DevicesViewModel")
    }
}
