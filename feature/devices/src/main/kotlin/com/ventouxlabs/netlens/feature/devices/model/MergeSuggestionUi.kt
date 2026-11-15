package com.ventouxlabs.netlens.feature.devices.model

import androidx.annotation.StringRes
import com.ventouxlabs.netlens.core.data.model.DeviceIdentifierEntity
import com.ventouxlabs.netlens.core.data.model.KnownDeviceEntity
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

/** The user-authored columns a merge keeps, fills or drops. */
enum class UserField(@param:StringRes val label: Int, val read: (KnownDeviceEntity) -> String?) {
    NAME(R.string.devices_field_name, { it.customName }),
    TAGS(R.string.devices_field_tags, { it.tags }),
    NOTES(R.string.devices_field_notes, { it.notes }),
    LOCATION(R.string.devices_field_location, { it.location }),
    TYPE_OVERRIDE(R.string.devices_field_type, { it.deviceTypeOverride }),
    OS_OVERRIDE(R.string.devices_field_os, { it.osOverride }),
    MODEL_OVERRIDE(R.string.devices_field_model, { it.modelOverride }),
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
    /** Survivor's own non-blank values: they stay. */
    val keptFields: List<UserField>,
    /** Fields only the loser has: copied onto the survivor. */
    val filledFields: List<UserField>,
    /** Fields both have with different values: the loser's value is lost, with no undo. */
    val droppedFields: List<UserField>,
    val movedIdentifiers: List<MovedIdentifier>,
    val movedIdentifierCount: Int,
    /** The removed row's value for each of [droppedFields], sanitised, for the confirmation dialog. */
    val droppedValues: List<DroppedValue>,
    /** [MergePreview.hash] of what this preview was built from; the repository re-checks it at merge time. */
    val previewHash: Int,
) {
    val key: String get() = suggestionKey(pair.deviceIdLow, pair.deviceIdHigh)

    /** The user must choose the merged trust value: it is never defaulted for them. */
    val trustDiffers: Boolean get() = survivor.isKnown != loser.isKnown

    /** Dropping a value or choosing trust cannot be undone, so Merge asks first. */
    val requiresConfirmation: Boolean get() = droppedFields.isNotEmpty() || trustDiffers

    /** Choosing "known" would carry the removed row's different MAC onto a trusted row. */
    val escalatesTrust: Boolean get() = trustDiffers && macsDiffer

    val macsDiffer: Boolean get() = !survivor.mac.equals(loser.mac, ignoreCase = true)
}

/** Lazy-list key, namespaced so it can never collide with a device row key. */
fun suggestionKey(low: Long, high: Long): String = "suggestion:$low:$high"

private const val MAX_SHOWN_IDENTIFIERS = 5
private const val MAX_IDENTIFIER_DISPLAY = 64
private const val MAX_NAME_DISPLAY = 63
private const val MAX_DROPPED_DISPLAY = 120

// Format characters (bidi overrides and isolates, zero-width, BOM, tag characters) plus the Unicode
// line and paragraph separators. DisplayText.flatten strips only C0/C1 controls, so these are mapped to
// a space first; they could otherwise reorder or hide text, or fake a line break.
private val INVISIBLE = Regex("[\\p{Cf}" + Char(0x2028) + Char(0x2029) + "]")

private val FSI = Char(0x2068)
private val PDI = Char(0x2069)

/** Flattened, format-character-free and clamped (never ending on half a surrogate pair); null when nothing is left. */
internal fun safeDisplay(value: String?, max: Int): String? =
    DisplayText.flatten(value?.replace(INVISIBLE, " "))?.let { text ->
        val clamped = text.take(max)
        (if (clamped.isNotEmpty() && clamped.last().isHighSurrogate()) clamped.dropLast(1) else clamped).trim().ifEmpty { null }
    }

/** Wraps device-derived text so its direction cannot leak into the surrounding translated sentence. */
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
    val fields = UserField.entries.map { it to (it.read(survivor).orBlankNull() to it.read(loser).orBlankNull()) }
    return MergeSuggestionUi(
        pair = pair,
        survivor = survivor.toSuggestionRow(),
        loser = loser.toSuggestionRow(),
        reasons = pair.reasons.map(SuggestionReason::fromCode).distinct(),
        keptFields = fields.filter { (_, v) -> v.first != null }.map { it.first },
        filledFields = fields.filter { (_, v) -> v.first == null && v.second != null }.map { it.first },
        droppedFields = fields.filter { (_, v) -> v.first != null && v.second != null && v.first != v.second }.map { it.first },
        movedIdentifiers = loserIdentifiers.take(MAX_SHOWN_IDENTIFIERS).map {
            MovedIdentifier(
                kind = safeDisplay(it.kind, MAX_IDENTIFIER_DISPLAY).orEmpty(),
                value = safeDisplay(it.value, MAX_IDENTIFIER_DISPLAY).orEmpty(),
            )
        },
        movedIdentifierCount = loserIdentifiers.size,
        droppedValues = fields.filter { (_, v) -> v.first != null && v.second != null && v.first != v.second }
            .map { (field, v) -> DroppedValue(field, safeDisplay(v.second, MAX_DROPPED_DISPLAY).orEmpty()) },
        previewHash = MergePreview.hash(survivor, loser, loserIdentifiers),
    )
}

private fun String?.orBlankNull(): String? = if (isNullOrBlank()) null else this

/** `customName ?: hostname ?: vendor ?: ip`, each candidate flattened first: device text is hostile. */
private fun KnownDeviceEntity.toSuggestionRow() = SuggestionRow(
    id = id,
    name = listOf(customName, hostname, vendor, ip).firstNotNullOfOrNull { safeDisplay(it, MAX_NAME_DISPLAY) }.orEmpty(),
    ip = safeDisplay(ip, MAX_NAME_DISPLAY).orEmpty(),
    mac = safeDisplay(macAddress, MAX_NAME_DISPLAY),
    isKnown = isKnown,
)
