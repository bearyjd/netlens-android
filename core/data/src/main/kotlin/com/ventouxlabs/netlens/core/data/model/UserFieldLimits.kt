package com.ventouxlabs.netlens.core.data.model

/**
 * Length caps for the user-authored columns of [KnownDeviceEntity]. The editor enforces them while
 * typing and on save; a merge never stores a combined value over them. Tag limits live in [DeviceTags].
 */
object UserFieldLimits {
    const val MAX_NAME_LENGTH = 60
    const val MAX_LOCATION_LENGTH = 40
    const val MAX_NOTES_LENGTH = 500
    const val MAX_OVERRIDE_LENGTH = 60
}
