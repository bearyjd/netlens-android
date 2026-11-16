package com.ventouxlabs.netlens.feature.devices.model

import androidx.annotation.StringRes
import com.ventouxlabs.netlens.core.data.model.KnownDeviceEntity
import com.ventouxlabs.netlens.core.data.model.WatchedNetworkEntity

data class DevicesUiState(
    val devices: List<KnownDeviceEntity> = emptyList(),
    val searchQuery: String = "",
    val watchedNetworks: List<WatchedNetworkEntity> = emptyList(),
    val cadence: WatchCadence = WatchCadence.DEFAULT,
    val masterWatchEnabled: Boolean = false,
    /** The `autoMergeDevices` preference; null until it has loaded, so a stored "off" never shows as on. */
    val autoMergeEnabled: Boolean? = null,
    val selectedDeviceId: Long? = null,
    /**
     * The selected row, looked up in the unfiltered inventory (a search never closes the sheet). When
     * the row vanishes (merged away, deleted) this keeps its last copy so the open editor, and what
     * the user typed, survive until they save or close.
     */
    val selectedDevice: KnownDeviceEntity? = null,
    /** A draft for the detail sheet after a save was redirected or refused; see [DetailDraft]. */
    val detailDraft: DetailDraft? = null,
    /** A guarded save is in flight: the sheet stays open and Save is disabled until it answers. */
    val detailSaving: Boolean = false,
    val detailNotice: DetailNotice? = null,
    /** Every tag in the inventory, for the filter-chip row — not just the ones in [devices]. */
    val availableTags: List<String> = emptyList(),
    /** Tags currently toggled on; empty means unfiltered. */
    val activeTags: Set<String> = emptySet(),
    /** Pending duplicate pairs whose rows both exist; the banner count and the sheet list are this one list. */
    val suggestions: List<MergeSuggestionUi> = emptyList(),
    val suggestionsOpen: Boolean = false,
    /** Key of the pair being merged or rejected right now; blocks a second tap. */
    val suggestionBusyKey: String? = null,
    /** Transient string-resource id for a refused or incomplete suggestion action. */
    @param:StringRes val suggestionMessage: Int? = null,
    /** Transient string-resource id for a watch-add failure; shown once then cleared. */
    @param:StringRes val watchError: Int? = null,
)
