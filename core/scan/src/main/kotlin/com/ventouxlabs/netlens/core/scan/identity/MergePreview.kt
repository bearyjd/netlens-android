package com.ventouxlabs.netlens.core.scan.identity

import com.ventouxlabs.netlens.core.data.model.DeviceIdentifierEntity
import com.ventouxlabs.netlens.core.data.model.KnownDeviceEntity

/**
 * What a user-confirmed merge will do, shared by the UI (which shows it) and the repository (which
 * re-checks it inside the merge transaction), so both always agree on the survivor and on the hash.
 */
object MergePreview {
    /** The row a merge keeps, then the one folded into it: older `firstSeen`, then lower id. */
    fun survivorAndLoser(a: KnownDeviceEntity, b: KnownDeviceEntity): Pair<KnownDeviceEntity, KnownDeviceEntity> =
        if (compareValuesBy(a, b, { it.firstSeen }, { it.id }) <= 0) a to b else b to a

    /**
     * Fingerprint of everything a merge decision is based on: both rows' user fields, trust and MACs
     * and the full set of identifiers that would move. A change to any of them after the user looked
     * changes the hash, so the merge is refused instead of applied to something they did not see.
     */
    fun hash(survivor: KnownDeviceEntity, loser: KnownDeviceEntity, loserIdentifiers: List<DeviceIdentifierEntity>): Int =
        listOf(
            survivor.id, loser.id, fingerprint(survivor), fingerprint(loser),
            loserIdentifiers.map { Triple(it.kind, it.value, it.scopeKey).toString() }.sorted(),
        ).hashCode()

    private fun fingerprint(row: KnownDeviceEntity): List<Any?> = listOf(
        row.customName, row.tags, row.notes, row.location, row.deviceTypeOverride, row.osOverride, row.modelOverride,
        row.isKnown, row.macAddress,
    )
}
