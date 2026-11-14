package com.ventouxlabs.netlens.core.data.model

/**
 * True when the user authored anything on this row: name, tags, notes, location, or any of the
 * three detection overrides. Blank counts as empty. `isKnown` is deliberately NOT part of this —
 * it is a trust flag with its own merge rule.
 */
fun KnownDeviceEntity.hasUserDetails(): Boolean = listOf(
    customName, tags, notes, location, deviceTypeOverride, osOverride, modelOverride,
).any { !it.isNullOrBlank() }

/**
 * Copies [other]'s user-authored fields into this row's *empty* fields only. A non-blank value
 * on this row always wins; `isKnown` and every scan-derived column are left untouched.
 */
fun KnownDeviceEntity.fillEmptyUserFieldsFrom(other: KnownDeviceEntity): KnownDeviceEntity = copy(
    customName = customName.orFill(other.customName),
    tags = tags.orFill(other.tags),
    notes = notes.orFill(other.notes),
    location = location.orFill(other.location),
    deviceTypeOverride = deviceTypeOverride.orFill(other.deviceTypeOverride),
    osOverride = osOverride.orFill(other.osOverride),
    modelOverride = modelOverride.orFill(other.modelOverride),
)

private fun String?.orFill(fallback: String?): String? = if (isNullOrBlank()) fallback else this
