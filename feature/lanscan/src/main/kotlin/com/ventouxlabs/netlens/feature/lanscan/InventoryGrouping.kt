package com.ventouxlabs.netlens.feature.lanscan

import com.ventouxlabs.netlens.core.data.model.DeviceTags
import com.ventouxlabs.netlens.core.data.model.KnownDeviceEntity

/**
 * Inventory rows split into identified and unidentified devices, each in input order.
 * [grouped] is true only when the unidentified rows should collapse behind one header; when
 * false the caller renders the original list unchanged.
 */
internal data class InventoryGroups(
    val identified: List<KnownDeviceEntity>,
    val unidentified: List<KnownDeviceEntity>,
    val grouped: Boolean,
)

/** A blank query is no filter (the ViewModel trims it before matching), so it still groups. */
internal fun groupInventory(devices: List<KnownDeviceEntity>, searchQuery: String): InventoryGroups {
    val (identified, unidentified) = devices.partition(::isIdentified)
    val grouped = searchQuery.isBlank() && identified.isNotEmpty() && unidentified.isNotEmpty()
    return InventoryGroups(identified, unidentified, grouped)
}

/**
 * Anything the user authored or the scan resolved; user-annotated devices are never hidden.
 * Overrides and detected values are checked independently so a blank override cannot mask a
 * detected value, and tags use the card's parser so ", ," does not count.
 */
private fun isIdentified(d: KnownDeviceEntity): Boolean =
    d.isKnown ||
        DeviceTags.parse(d.tags).isNotEmpty() ||
        listOf(
            d.customName, d.notes, d.location, d.hostname, d.vendor,
            d.deviceTypeOverride, d.deviceType, d.osOverride, d.osGuess, d.modelOverride, d.model,
        ).any { !it.isNullOrBlank() }
