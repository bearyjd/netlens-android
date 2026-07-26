package com.ventouxlabs.netlens.feature.devices.model

import com.ventouxlabs.netlens.core.data.model.DeviceTags
import com.ventouxlabs.netlens.core.data.model.KnownDeviceEntity

const val MAX_DEVICE_NAME_LENGTH = 60
const val MAX_DEVICE_LOCATION_LENGTH = 40
const val MAX_DEVICE_NOTES_LENGTH = 500

/**
 * The user-authored half of a device row, as typed in the detail sheet. Kept separate from
 * [KnownDeviceEntity] so the editor never carries the scan-derived columns a re-scan owns.
 */
data class DeviceDetailsEdit(
    val customName: String = "",
    val tagsInput: String = "",
    val location: String = "",
    val notes: String = "",
) {
    /** Normalises every field to its stored form; blank fields become null. */
    fun normalized(): NormalizedDeviceDetails = NormalizedDeviceDetails(
        customName = customName.trim().take(MAX_DEVICE_NAME_LENGTH).trim().ifBlank { null },
        tags = DeviceTags.formatFromInput(tagsInput),
        location = location.trim().take(MAX_DEVICE_LOCATION_LENGTH).trim().ifBlank { null },
        notes = notes.trim().take(MAX_DEVICE_NOTES_LENGTH).ifBlank { null },
    )

    companion object {
        fun from(device: KnownDeviceEntity): DeviceDetailsEdit = DeviceDetailsEdit(
            customName = device.customName.orEmpty(),
            tagsInput = DeviceTags.parse(device.tags).joinToString(", "),
            location = device.location.orEmpty(),
            notes = device.notes.orEmpty(),
        )
    }
}

data class NormalizedDeviceDetails(
    val customName: String?,
    val tags: String?,
    val location: String?,
    val notes: String?,
)
