package com.ventouxlabs.netlens.core.data.model

/**
 * Free-text matching over an inventory row, shared by every screen that lists devices so a
 * search for "printer" behaves identically whether it is typed in Devices or in the LAN scan
 * inventory tab. Matching is in-memory: the full inventory is already loaded for the list, and
 * a LIKE query would miss the user-authored fields unless every call site repeated the same
 * seven-column WHERE clause.
 */
object KnownDeviceSearch {

    /** True when [query] is blank (everything matches) or hits any searchable field. */
    fun matches(device: KnownDeviceEntity, query: String): Boolean {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return true
        return device.customName.hit(trimmed) ||
            device.hostname.hit(trimmed) ||
            device.ip.hit(trimmed) ||
            device.macAddress.hit(trimmed) ||
            device.vendor.hit(trimmed) ||
            device.deviceType.hit(trimmed) ||
            device.location.hit(trimmed) ||
            device.notes.hit(trimmed) ||
            DeviceTags.matches(device.tags, trimmed)
    }

    /**
     * Every distinct tag in [devices], case-insensitively de-duplicated and sorted for a stable
     * filter-chip row.
     */
    fun allTags(devices: List<KnownDeviceEntity>): List<String> =
        devices.flatMap { DeviceTags.parse(it.tags) }
            .groupBy { it.lowercase() }
            .map { (_, variants) -> variants.first() }
            .sortedBy { it.lowercase() }

    /**
     * Tag-chip filtering with OR semantics: selecting "printer" and "camera" widens the list to
     * both, which is what a row of independently-toggled chips reads as. An empty [tags] set
     * means "no tag filter" rather than "match nothing".
     */
    fun matchesAnyTag(device: KnownDeviceEntity, tags: Set<String>): Boolean {
        if (tags.isEmpty()) return true
        return tags.any { DeviceTags.hasTag(device.tags, it) }
    }

    private fun String?.hit(query: String): Boolean = this?.contains(query, ignoreCase = true) == true
}
