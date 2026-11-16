package com.ventouxlabs.netlens.core.data.model

/**
 * True when the user authored anything on this row: name, tags, notes, location, or any of the
 * three detection overrides. Blank counts as empty. `isKnown` is deliberately NOT part of this —
 * it is a trust flag with its own merge rule.
 */
fun KnownDeviceEntity.hasUserDetails(): Boolean = listOf(
    customName, tags, notes, location, deviceTypeOverride, osOverride, modelOverride,
).any { !it.isNullOrBlank() }

/** What a merge does to one user-authored field. */
enum class FieldMergeOutcome {
    /** The survivor's value stays as it is (the loser had nothing new). */
    KEPT,

    /** The survivor had nothing; the loser's value is copied over. */
    FILLED,

    /** Both values are combined (tags, notes only). */
    UNIONED,

    /** The loser's value (or part of it, see [FieldMerge.dropped]) is lost. */
    DROPPED,
}

/**
 * One field's merge result. [dropped] is the loser's text that does not survive; it can be set
 * alongside [FieldMergeOutcome.UNIONED] when tags over [DeviceTags.MAX_TAGS] are cut.
 */
data class FieldMerge(val field: UserDetailField, val outcome: FieldMergeOutcome, val dropped: String? = null)

/** [merged] is what the survivor row stores; [fields] lists every field either side has a value for. */
data class UserFieldMerge(val merged: UserDetails, val fields: List<FieldMerge>)

private const val NOTES_SEPARATOR = "\n\n"

/**
 * Combines [survivor]'s and [loser]'s user-authored fields. Tags are unioned (survivor first, its
 * casing wins on a case-insensitive duplicate, anything past [DeviceTags.MAX_TAGS] is reported
 * dropped); notes are unioned by paragraph (blank-line separated, trimmed, case-insensitive): the
 * loser's paragraphs the survivor lacks are appended when the result fits
 * [UserFieldLimits.MAX_NOTES_LENGTH], else the loser's notes are dropped (never truncated); every other field fills only when the survivor's is blank. Blank counts as empty.
 * Idempotent: merging the same loser text again changes nothing.
 */
fun mergeUserFields(survivor: UserDetails, loser: UserDetails): UserFieldMerge {
    val results = UserDetailField.entries.mapNotNull { field ->
        when (field) {
            UserDetailField.TAGS -> mergeTags(survivor.tags, loser.tags)
            UserDetailField.NOTES -> mergeNotes(survivor.notes, loser.notes)
            else -> mergeScalar(survivor[field], loser[field])
        }?.let { (value, outcome, dropped) -> Triple(field, value, FieldMerge(field, outcome, dropped)) }
    }
    val merged = results.fold(survivor) { acc, (field, value, _) -> acc.with(field, value) }
    return UserFieldMerge(merged, results.map { it.third })
}

private typealias FieldResult = Triple<String?, FieldMergeOutcome, String?>

private fun mergeScalar(s: String?, l: String?): FieldResult? = when {
    s.isNullOrBlank() && l.isNullOrBlank() -> null
    s.isNullOrBlank() -> FieldResult(l, FieldMergeOutcome.FILLED, null)
    // Same value up to case and surrounding spaces is not a loss: the survivor's spelling stays.
    l.isNullOrBlank() || s.trim().equals(l.trim(), ignoreCase = true) -> FieldResult(s, FieldMergeOutcome.KEPT, null)
    else -> FieldResult(s, FieldMergeOutcome.DROPPED, l)
}

private val PARAGRAPH_BREAK = Regex("\\n\\s*\\n")

/** Trimmed, non-empty paragraphs (split on blank lines), without case-insensitive repeats. */
private fun paragraphs(text: String?): List<String> {
    val seen = HashSet<String>()
    return text.orEmpty().split(PARAGRAPH_BREAK).map { it.trim() }.filter { it.isNotEmpty() && seen.add(it.lowercase()) }
}

// Paragraph semantics, not substring: "TV" is not already present inside "Living room TV".
private fun mergeNotes(s: String?, l: String?): FieldResult? {
    val mine = paragraphs(s)
    val theirs = paragraphs(l)
    if (mine.isEmpty() && theirs.isEmpty()) return null
    if (mine.isEmpty()) return FieldResult(l, FieldMergeOutcome.FILLED, null)
    val present = mine.mapTo(HashSet()) { it.lowercase() }
    val added = theirs.filterNot { it.lowercase() in present }
    if (added.isEmpty()) return FieldResult(s, FieldMergeOutcome.KEPT, null)
    // The survivor's own text stays byte-for-byte (only trimmed); just the new paragraphs are appended.
    val union = (listOf(s.orEmpty().trim()) + added).joinToString(NOTES_SEPARATOR)
    return if (union.length <= UserFieldLimits.MAX_NOTES_LENGTH) {
        FieldResult(union, FieldMergeOutcome.UNIONED, null)
    } else {
        FieldResult(s, FieldMergeOutcome.DROPPED, l)
    }
}

private fun mergeTags(s: String?, l: String?): FieldResult? {
    val mine = DeviceTags.parse(s)
    val theirs = DeviceTags.parse(l)
    if (mine.isEmpty() && theirs.isEmpty()) return null
    val seen = HashSet<String>()
    val combined = (mine + theirs).filter { seen.add(it.lowercase()) }
    // Nothing new from the loser: the survivor's column stays byte-identical.
    if (combined.size == mine.size) return FieldResult(s, FieldMergeOutcome.KEPT, null)
    val kept = combined.take(DeviceTags.MAX_TAGS)
    // Only the loser's new tags can be lost: a legacy survivor over the cap keeps its column untouched.
    val dropped = combined.drop(mine.size).filterNot { it in kept }.takeIf { it.isNotEmpty() }?.joinToString(", ")
    val outcome = when {
        kept.size <= mine.size -> return FieldResult(s, FieldMergeOutcome.DROPPED, dropped)
        mine.isEmpty() -> FieldMergeOutcome.FILLED
        else -> FieldMergeOutcome.UNIONED
    }
    return FieldResult(DeviceTags.format(kept), outcome, dropped)
}
