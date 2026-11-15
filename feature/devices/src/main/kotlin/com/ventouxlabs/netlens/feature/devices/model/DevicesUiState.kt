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
