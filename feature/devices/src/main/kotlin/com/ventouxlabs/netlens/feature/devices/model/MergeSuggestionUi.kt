package com.ventouxlabs.netlens.feature.devices.model

import androidx.annotation.StringRes
import com.ventouxlabs.netlens.core.data.model.DeviceIdentifierEntity
import com.ventouxlabs.netlens.core.data.model.FieldMerge
import com.ventouxlabs.netlens.core.data.model.FieldMergeOutcome
import com.ventouxlabs.netlens.core.data.model.KnownDeviceEntity
import com.ventouxlabs.netlens.core.data.model.UserDetailField
import com.ventouxlabs.netlens.core.data.model.UserFieldLimits
import com.ventouxlabs.netlens.core.data.model.mergeUserFields
import com.ventouxlabs.netlens.core.data.model.userDetails
import com.ventouxlabs.netlens.core.network.DisplayText
import com.ventouxlabs.netlens.core.scan.identity.MergePreview
import com.ventouxlabs.netlens.core.scan.identity.MergeSuggestion
import com.ventouxlabs.netlens.feature.devices.R

/** Why the resolver paired two rows: the weak-score signals and the reasons auto-merge was refused. */
enum class SuggestionReason(@param:StringRes val label: Int) {
    MODEL(R.string.devices_reason_model),
    HOSTNAME(R.string.devices_reason_hostname),
    VENDOR(R.string.devices_reason_vendor),
    DEVICE_TYPE(R.string.devices_reason_device_type),
    SHARED_IDENTIFIER(R.string.devices_reason_shared_identifier),
    WEAK_LINK(R.string.devices_reason_weak_link),
    CONTRADICTORY_IDENTIFIER(R.string.devices_reason_contradictory_identifier),
    DISTINCT_DECISION(R.string.devices_reason_distinct_decision),
    USER_DETAILS(R.string.devices_reason_user_details),
    TRUST_DIFFERS(R.string.devices_reason_trust_differs),
    CHANGED_SINCE_READ(R.string.devices_reason_changed_since_read),
    UNKNOWN(R.string.devices_reason_unknown),
    ;

    companion object {
        /** Stored codes are free text in the database; anything unrecognised maps to [UNKNOWN], never shown raw. */
        fun fromCode(code: String): SuggestionReason = when (code.trim()) {
            "model" -> MODEL
            "hostname" -> HOSTNAME
            "vendor" -> VENDOR
            "device_type" -> DEVICE_TYPE
            "shared_identifier" -> SHARED_IDENTIFIER
            "weak_link" -> WEAK_LINK
            "contradictory_identifier" -> CONTRADICTORY_IDENTIFIER
            "distinct_decision" -> DISTINCT_DECISION
            "user_details" -> USER_DETAILS
            "trust_differs" -> TRUST_DIFFERS
            "changed_since_read" -> CHANGED_SINCE_READ
            else -> UNKNOWN
        }
    }
}

/** The user-authored columns a merge keeps, fills, combines or drops; [key] is the core:data column. */
enum class UserField(@param:StringRes val label: Int, val key: UserDetailField) {
    NAME(R.string.devices_field_name, UserDetailField.CUSTOM_NAME),
    TAGS(R.string.devices_field_tags, UserDetailField.TAGS),
    NOTES(R.string.devices_field_notes, UserDetailField.NOTES),
    LOCATION(R.string.devices_field_location, UserDetailField.LOCATION),
    TYPE_OVERRIDE(R.string.devices_field_type, UserDetailField.DEVICE_TYPE_OVERRIDE),
    OS_OVERRIDE(R.string.devices_field_os, UserDetailField.OS_OVERRIDE),
    MODEL_OVERRIDE(R.string.devices_field_model, UserDetailField.MODEL_OVERRIDE),
    ;

    companion object {
        fun of(key: UserDetailField): UserField = entries.first { it.key == key }
    }
}

/** One side of a pair, with every network-supplied string already flattened for display. */
data class SuggestionRow(
    val id: Long,
    val name: String,
    val ip: String,
    val mac: String?,
    val isKnown: Boolean,
)

data class MovedIdentifier(val kind: String, val value: String)

/** A field both rows have with different values: the removed row's value is lost, so it is shown before merging. */
data class DroppedValue(val field: UserField, val value: String)

/** A field whose two values are combined (tags, notes): [value] is the sanitised result the kept row will hold. */
data class CombinedValue(val field: UserField, val value: String)

/**
 * A pending pair as the sheet shows it. [survivor] is the row that stays (mirrors
 * `DeviceInventoryRepositoryImpl.acceptSuggestion`: older `firstSeen`, then lower id); [loser] is
 * merged into it and deleted.
 */
data class MergeSuggestionUi(
    val pair: MergeSuggestion.BetweenRows,
    val survivor: SuggestionRow,
    val loser: SuggestionRow,
    val reasons: List<SuggestionReason>,
    /** Survivor's own non-blank values that stay exactly as they are. */
    val keptFields: List<UserField>,
    /** Fields only the loser has: copied onto the survivor. */
    val filledFields: List<UserField>,
    /** Fields both have that are combined into one value (tags, notes that fit). */
    val unionedFields: List<UserField>,
    /** Fields where (some of) the loser's value is lost, with no undo. */
    val droppedFields: List<UserField>,
    val movedIdentifiers: List<MovedIdentifier>,
    val movedIdentifierCount: Int,
    /** The removed row's lost value for each of [droppedFields], sanitised, for the confirmation dialog. */
    val droppedValues: List<DroppedValue>,
    /** The combined value for each of [unionedFields], sanitised, for the confirmation dialog. */
    val combinedValues: List<CombinedValue>,
    /** [MergePreview.hash] of what this preview was built from; the repository re-checks it at merge time. */
    val previewHash: Int,
) {
    val key: String get() = suggestionKey(pair.deviceIdLow, pair.deviceIdHigh)

    /** The user must choose the merged trust value: it is never defaulted for them. */
    val trustDiffers: Boolean get() = survivor.isKnown != loser.isKnown

    /** Dropping or combining a value, or choosing trust, cannot be undone, so Merge asks first. */
    val requiresConfirmation: Boolean get() = droppedFields.isNotEmpty() || unionedFields.isNotEmpty() || trustDiffers

    /** Choosing "known" would carry the removed row's different MAC onto a trusted row. */
    val escalatesTrust: Boolean get() = trustDiffers && macsDiffer

    val macsDiffer: Boolean get() = !survivor.mac.equals(loser.mac, ignoreCase = true)
}

/** Lazy-list key, namespaced so it can never collide with a device row key. */
fun suggestionKey(low: Long, high: Long): String = "suggestion:$low:$high"

private const val MAX_SHOWN_IDENTIFIERS = 5
private const val MAX_IDENTIFIER_DISPLAY = 64
private const val MAX_NAME_DISPLAY = 63
// Both at the notes cap: the confirm dialog scrolls, so a lost or combined value is never a silent prefix.
private const val MAX_DROPPED_DISPLAY = UserFieldLimits.MAX_NOTES_LENGTH
private const val MAX_COMBINED_DISPLAY = UserFieldLimits.MAX_NOTES_LENGTH

private val FSI = Char(0x2068)
private val PDI = Char(0x2069)

/**
 * [DisplayText.flatten] (the single sanitising rule), clamped to [max] UTF-16 chars, then flattened again so a
 * clamp that left half a surrogate pair, a trailing ZWJ/ZWNJ or a dangling variation selector cannot leak:
 * the result is always flatten-idempotent. Null when nothing is left.
 */
internal fun safeDisplay(value: String?, max: Int): String? =
    DisplayText.flatten(value)?.let { text ->
        val clamped = text.take(max)
        DisplayText.flatten(if (clamped.isNotEmpty() && clamped.last().isHighSurrogate()) clamped.dropLast(1) else clamped)
    }

/** Wraps already-flattened device-derived text (see [safeDisplay]) so its direction cannot leak into the surrounding translated sentence. */
fun bidiIsolate(text: String): String = "$FSI$text$PDI"

/** The row [pair] keeps when accepted, then the one folded into it; null if either row is missing. */
fun survivorAndLoser(pair: MergeSuggestion.BetweenRows, rows: Map<Long, KnownDeviceEntity>): Pair<KnownDeviceEntity, KnownDeviceEntity>? {
    val low = rows[pair.deviceIdLow] ?: return null
    val high = rows[pair.deviceIdHigh] ?: return null
    return MergePreview.survivorAndLoser(low, high)
}

fun buildMergeSuggestionUi(
    pair: MergeSuggestion.BetweenRows,
    survivor: KnownDeviceEntity,
    loser: KnownDeviceEntity,
    loserIdentifiers: List<DeviceIdentifierEntity>,
): MergeSuggestionUi {
    // The same function the DAO's merge writes with, so the preview cannot disagree with the result.
    val merge = mergeUserFields(survivor.userDetails(), loser.userDetails())
    fun fieldsWhere(predicate: (FieldMerge) -> Boolean) =
        merge.fields.filter(predicate).map { UserField.of(it.field) }
    return MergeSuggestionUi(
        pair = pair,
        survivor = survivor.toSuggestionRow(),
        loser = loser.toSuggestionRow(),
        reasons = pair.reasons.map(SuggestionReason::fromCode).distinct(),
        keptFields = fieldsWhere { it.outcome == FieldMergeOutcome.KEPT || it.outcome == FieldMergeOutcome.DROPPED },
        filledFields = fieldsWhere { it.outcome == FieldMergeOutcome.FILLED },
        unionedFields = fieldsWhere { it.outcome == FieldMergeOutcome.UNIONED },
        droppedFields = fieldsWhere { it.dropped != null },
        movedIdentifiers = loserIdentifiers.take(MAX_SHOWN_IDENTIFIERS).map {
            MovedIdentifier(
                kind = safeDisplay(it.kind, MAX_IDENTIFIER_DISPLAY).orEmpty(),
                value = safeDisplay(it.value, MAX_IDENTIFIER_DISPLAY).orEmpty(),
            )
        },
        movedIdentifierCount = loserIdentifiers.size,
        droppedValues = merge.fields.mapNotNull { f ->
            f.dropped?.let { DroppedValue(UserField.of(f.field), safeDisplay(it, MAX_DROPPED_DISPLAY).orEmpty()) }
        },
        combinedValues = merge.fields.filter { it.outcome == FieldMergeOutcome.UNIONED }.map {
            CombinedValue(UserField.of(it.field), safeDisplay(merge.merged[it.field], MAX_COMBINED_DISPLAY).orEmpty())
        },
        previewHash = MergePreview.hash(survivor, loser, loserIdentifiers),
    )
}

/** `customName ?: hostname ?: vendor ?: ip`, each candidate flattened first: device text is hostile. */
private fun KnownDeviceEntity.toSuggestionRow() = SuggestionRow(
    id = id,
    name = listOf(customName, hostname, vendor, ip).firstNotNullOfOrNull { safeDisplay(it, MAX_NAME_DISPLAY) }.orEmpty(),
    ip = safeDisplay(ip, MAX_NAME_DISPLAY).orEmpty(),
    mac = safeDisplay(macAddress, MAX_NAME_DISPLAY),
    isKnown = isKnown,
)
