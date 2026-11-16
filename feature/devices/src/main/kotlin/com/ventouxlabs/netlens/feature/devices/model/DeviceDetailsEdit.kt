package com.ventouxlabs.netlens.feature.devices.model

import androidx.annotation.StringRes
import com.ventouxlabs.netlens.core.data.model.DeviceTags
import com.ventouxlabs.netlens.core.data.model.KnownDeviceEntity
import com.ventouxlabs.netlens.core.data.model.UserDetailField
import com.ventouxlabs.netlens.core.data.model.UserDetails
import com.ventouxlabs.netlens.core.data.model.UserFieldLimits
import com.ventouxlabs.netlens.core.data.model.userDetails

/**
 * The user-authored half of a device row, as typed in the detail sheet. Kept separate from
 * [KnownDeviceEntity] so the editor never carries the scan-derived columns a re-scan owns.
 */
data class DeviceDetailsEdit(
    val customName: String = "",
    val tagsInput: String = "",
    val location: String = "",
    val notes: String = "",
    // Blank means "no override": the detected value shows again.
    val deviceTypeOverride: String = "",
    val osOverride: String = "",
    val modelOverride: String = "",
) {
    /** Normalises every field to its stored form; blank fields become null. */
    fun normalized(): UserDetails = UserDetails(
        customName = customName.trim().take(UserFieldLimits.MAX_NAME_LENGTH).trim().ifBlank { null },
        tags = DeviceTags.formatFromInput(tagsInput),
        location = location.trim().take(UserFieldLimits.MAX_LOCATION_LENGTH).trim().ifBlank { null },
        notes = notes.trim().take(UserFieldLimits.MAX_NOTES_LENGTH).ifBlank { null },
        deviceTypeOverride = deviceTypeOverride.normalizedOverride(),
        osOverride = osOverride.normalizedOverride(),
        modelOverride = modelOverride.normalizedOverride(),
    )

    /**
     * What to send to the guarded save: [base] (the raw stored values the editor opened with) with
     * only the fields the user actually changed replaced. A field counts as changed when its
     * normalised value differs from the normalised [base] value, so a stored form that merely
     * normalises differently (`"  "` notes, a legacy tag column) is never mistaken for an edit.
     */
    fun changesOver(base: UserDetails): UserDetails {
        val before = from(base).normalized()
        val after = normalized()
        return UserDetailField.entries.fold(base) { acc, field ->
            if (after[field] != before[field]) acc.with(field, after[field]) else acc
        }
    }

    private fun String.normalizedOverride(): String? =
        trim().take(UserFieldLimits.MAX_OVERRIDE_LENGTH).trim().ifBlank { null }

    companion object {
        fun from(device: KnownDeviceEntity): DeviceDetailsEdit = from(device.userDetails())

        fun from(details: UserDetails): DeviceDetailsEdit = DeviceDetailsEdit(
            customName = details.customName.orEmpty(),
            tagsInput = DeviceTags.parse(details.tags).joinToString(", "),
            location = details.location.orEmpty(),
            notes = details.notes.orEmpty(),
            deviceTypeOverride = details.deviceTypeOverride.orEmpty(),
            osOverride = details.osOverride.orEmpty(),
            modelOverride = details.modelOverride.orEmpty(),
        )
    }
}

/**
 * An editor draft the view model hands back to the sheet after a save could not be applied (the
 * row was merged away or changed underneath). [token] changes with every draft so the sheet resets
 * its state to it; [base] is the target row's values at that moment.
 */
data class DetailDraft(val deviceId: Long, val base: UserDetails, val edit: DeviceDetailsEdit, val token: Int)

/**
 * A message shown in the detail sheet; [arg] is already sanitised and bidi-isolated device text.
 * [discarded] lists typed values a redirected draft could not apply, so nothing typed is lost silently.
 */
data class DetailNotice(
    @param:StringRes val message: Int,
    val arg: String? = null,
    val discarded: List<DiscardedEdit> = emptyList(),
)

/** A field whose typed [text] (flattened, clamped) was not applied because the row's value changed. */
data class DiscardedEdit(val field: UserField, val text: String)
