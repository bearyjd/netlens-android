package com.ventouxlabs.netlens.core.scan.identity

import com.ventouxlabs.netlens.core.data.model.KnownDeviceEntity
import com.ventouxlabs.netlens.core.scan.model.LanDevice
import com.ventouxlabs.netlens.core.scan.model.identity.DeviceIdentifier

/** What one scanned device is, relative to the inventory. */
sealed interface Resolution {
    /** The device is this row. [mergedFrom] lists rows the resolver folded into it on the way. */
    data class Existing(val deviceId: Long, val mergedFrom: List<Long> = emptyList()) : Resolution

    /** No row matches; the caller inserts one (see [ResolvedDevice.ownedIdentifiers]). */
    data object New : Resolution

    /** Several rows match but may not be auto-merged: scan data goes to [attachedTo], the user is asked. */
    data class Ambiguous(val deviceIds: List<Long>, val attachedTo: Long, val blockedBy: MergeBlock) : Resolution

    /**
     * An unauthenticated sighting claimed [claimedDeviceId]'s identifier from a second simultaneous
     * address. It is persisted as its own row [deviceId] (claims kept as untrusted evidence); the
     * caller notifies when [inserted] is true.
     */
    data class Conflict(val deviceId: Long, val claimedDeviceId: Long, val inserted: Boolean) : Resolution
}

/** Why auto-merge was refused for several candidate rows. */
enum class MergeBlock(val code: String) {
    WEAK_LINK("weak_link"),
    CONTRADICTORY_IDENTIFIER("contradictory_identifier"),
    DISTINCT_DECISION("distinct_decision"),
    USER_DETAILS("user_details"),
    TRUST_DIFFERS("trust_differs"),

    /** The DAO refused inside its transaction: a user edit or decision landed after the resolver read the rows. */
    CHANGED_SINCE_READ("changed_since_read"),
}

/**
 * One scanned device after resolution. [device] is flattened, capped and (for a grouped
 * multi-homed device) carries [LanDevice.additionalIps]. [ownedIdentifiers] are safe to store as
 * unique-identifier ownership; [untrustedClaims] must only ever be stored as evidence.
 * [sharesIdentifier] flags a device that shares an identifier with another sighting in the same scan.
 */
data class ResolvedDevice(
    val device: LanDevice,
    val resolution: Resolution,
    val ownedIdentifiers: Set<DeviceIdentifier>,
    val untrustedClaims: Set<DeviceIdentifier>,
    val sharesIdentifier: Boolean,
)

sealed interface MergeSuggestion {
    val score: Int?
    val reasons: List<String>

    /** Between two existing rows; already persisted as a `SUGGESTED` decision by the resolver. */
    data class BetweenRows(
        val deviceIdLow: Long,
        val deviceIdHigh: Long,
        override val score: Int?,
        override val reasons: List<String>,
    ) : MergeSuggestion

    /**
     * A [Resolution.New] device that weakly resembles an existing row. It has no id yet, so the
     * caller persists it with [DeviceIdentityResolver.persistSuggestion] after inserting the row.
     */
    data class ForNewDevice(
        val device: LanDevice,
        val existingDeviceId: Long,
        override val score: Int,
        override val reasons: List<String>,
    ) : MergeSuggestion
}

data class ResolveResult(
    val devices: List<ResolvedDevice>,
    val suggestions: List<MergeSuggestion>,
    /** Conflict rows inserted by this call: the `NewDeviceNotifier` contract fires for each. */
    val newConflictRows: List<KnownDeviceEntity>,
)

/** [autoMerge] is the Task 1.7 kill switch: off means no merge, no weak scoring, MAC-or-IP lookup only. */
data class ResolveOptions(val autoMerge: Boolean = true)

/** Follows loser -> survivor links (a chain is possible when one scan merges twice); a cycle stops. */
internal fun Map<Long, Long>.resolve(id: Long): Long {
    var current = id
    val seen = mutableSetOf(id)
    while (true) current = this[current]?.takeIf { seen.add(it) } ?: return current
}

internal fun Resolution.redirected(redirect: Map<Long, Long>): Resolution = when (this) {
    is Resolution.Existing -> copy(deviceId = redirect.resolve(deviceId))
    is Resolution.Ambiguous -> Resolution.Ambiguous(
        deviceIds.map(redirect::resolve).distinct(), redirect.resolve(attachedTo), blockedBy,
    )
    is Resolution.Conflict -> copy(claimedDeviceId = redirect.resolve(claimedDeviceId))
    Resolution.New -> this
}

/** Null when the redirect collapses a pair onto one row (nothing left to suggest). */
internal fun MergeSuggestion.redirected(redirect: Map<Long, Long>): MergeSuggestion? = when (this) {
    is MergeSuggestion.ForNewDevice -> copy(existingDeviceId = redirect.resolve(existingDeviceId))
    is MergeSuggestion.BetweenRows -> {
        val a = redirect.resolve(deviceIdLow)
        val b = redirect.resolve(deviceIdHigh)
        if (a == b) null else copy(deviceIdLow = minOf(a, b), deviceIdHigh = maxOf(a, b))
    }
}
