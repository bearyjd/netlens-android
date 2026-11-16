package com.ventouxlabs.netlens.core.data.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** Who decided a merge: the resolver on its own, or the user accepting a suggestion. */
enum class MergeKind { AUTO, USER }

/**
 * Write-only audit row for one merge, written inside `DeviceIdentityDao.mergeDevices` so the
 * deleted loser can be reconstructed later. No foreign keys on purpose: the loser row is gone by
 * the time the merge commits, and the survivor may be merged away in turn.
 *
 * The `*Json` columns hold device-supplied and user-authored text byte-exact (built with org.json,
 * never string-concatenated). They are hostile: flatten with DisplayText before showing any of it,
 * and never include this table in an export. Observations are not snapshotted (rebuilt by scans).
 */
@Entity(
    tableName = "merge_log",
    indices = [Index("mergedAt"), Index("survivorId")],
)
data class MergeLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val mergedAt: Long,
    val survivorId: Long,
    val loserId: Long,
    // MergeKind name: 'AUTO' | 'USER'.
    val kind: String,
    val survivorIsKnownBefore: Boolean,
    // From the pair's SUGGESTED decision row, when there was one.
    val decisionScore: Int?,
    val decisionReasons: String?,
    // Version of the JSON snapshot layout below.
    val snapshotVersion: Int,
    val loserRowJson: String,
    val loserIdentifiersJson: String,
    val loserSightingsJson: String,
    val survivorUserFieldsBeforeJson: String,
)
