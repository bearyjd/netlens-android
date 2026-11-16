package com.ventouxlabs.netlens.core.data.model

/** The seven user-authored columns of a [KnownDeviceEntity], in column order. */
enum class UserDetailField(val read: (UserDetails) -> String?) {
    CUSTOM_NAME({ it.customName }),
    TAGS({ it.tags }),
    NOTES({ it.notes }),
    LOCATION({ it.location }),
    DEVICE_TYPE_OVERRIDE({ it.deviceTypeOverride }),
    OS_OVERRIDE({ it.osOverride }),
    MODEL_OVERRIDE({ it.modelOverride }),
}

/** The user-authored block of a device row, without the scan-derived columns or `isKnown`. */
data class UserDetails(
    val customName: String? = null,
    val tags: String? = null,
    val notes: String? = null,
    val location: String? = null,
    val deviceTypeOverride: String? = null,
    val osOverride: String? = null,
    val modelOverride: String? = null,
) {
    operator fun get(field: UserDetailField): String? = field.read(this)

    /** A copy with [field] set to [value]. */
    fun with(field: UserDetailField, value: String?): UserDetails = when (field) {
        UserDetailField.CUSTOM_NAME -> copy(customName = value)
        UserDetailField.TAGS -> copy(tags = value)
        UserDetailField.NOTES -> copy(notes = value)
        UserDetailField.LOCATION -> copy(location = value)
        UserDetailField.DEVICE_TYPE_OVERRIDE -> copy(deviceTypeOverride = value)
        UserDetailField.OS_OVERRIDE -> copy(osOverride = value)
        UserDetailField.MODEL_OVERRIDE -> copy(modelOverride = value)
    }

    /** Fields whose value in [edit] differs from this (the base the editor opened with). */
    fun changedIn(edit: UserDetails): Set<UserDetailField> =
        UserDetailField.entries.filterTo(LinkedHashSet()) { this[it] != edit[it] }

    /** This row's values with only [fields] taken from [edit]. */
    fun withFieldsFrom(edit: UserDetails, fields: Set<UserDetailField>): UserDetails =
        fields.fold(this) { acc, field -> acc.with(field, edit[field]) }
}

fun KnownDeviceEntity.userDetails(): UserDetails = UserDetails(
    customName = customName,
    tags = tags,
    notes = notes,
    location = location,
    deviceTypeOverride = deviceTypeOverride,
    osOverride = osOverride,
    modelOverride = modelOverride,
)

/** What a guarded user-details save did; see `KnownDeviceDao.saveUserDetails`. Nothing is written unless [Saved]. */
sealed interface SaveOutcome {
    data object Saved : SaveOutcome

    /** The row was merged away (possibly through a chain); [survivorId] is the row that holds it now. */
    data class Merged(val survivorId: Long) : SaveOutcome

    /** The user changed [fields] that something else also changed since the editor opened. */
    data class Conflict(val fields: Set<UserDetailField>) : SaveOutcome

    /** The row was deleted and no surviving merge target is known. */
    data object Gone : SaveOutcome
}
