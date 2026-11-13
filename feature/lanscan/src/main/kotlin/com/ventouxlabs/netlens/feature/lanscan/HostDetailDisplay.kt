package com.ventouxlabs.netlens.feature.lanscan

import com.ventouxlabs.netlens.core.network.DisplayText

/** Fingerprint evidence is derived from device responses; flatten each line and drop blanks. */
internal fun hostDetailEvidenceLines(evidence: List<String>): List<String> =
    evidence.mapNotNull { DisplayText.flatten(it) }

/** Type, OS, vendor and model chip labels in display order, flattened, blanks omitted. */
internal fun hostDetailChipLabels(
    type: String?,
    os: String?,
    vendor: String?,
    model: String?,
): List<String> = listOfNotNull(type, os, vendor, model).mapNotNull { DisplayText.flatten(it) }

private val SERVICE_PROTOCOL_SUFFIX = Regex("\\s*\\._(tcp|udp)$")

/**
 * mDNS service types are device-supplied. Flatten before stripping affixes, drop blanks, and
 * return every cleaned name so the caller's overflow count matches what is actually displayed.
 */
internal fun deviceCardServiceNames(services: List<String>): List<String> =
    services.mapNotNull { service ->
        DisplayText.flatten(service)
            ?.replace(SERVICE_PROTOCOL_SUFFIX, "")
            ?.trim('.', ' ')
            ?.removePrefix("_")
            ?.takeIf { it.isNotBlank() }
    }

internal data class InventoryTitle(val title: String, val showIp: Boolean)

/** A typed name wins over the scanned hostname; a blank one falls through. IP row only when not the title. */
internal fun inventoryTitle(customName: String?, hostname: String?, ip: String): InventoryTitle {
    val name = DisplayText.flatten(customName) ?: DisplayText.flatten(hostname)
    return InventoryTitle(title = name ?: ip, showIp = name != null)
}
