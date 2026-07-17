package com.ventouxlabs.netlens.feature.devices.model

enum class WatchCadence(val minutes: Int) {
    FIFTEEN_MIN(15),
    THIRTY_MIN(30),
    ONE_HOUR(60),
    SIX_HOURS(360),
    ;

    companion object {
        val DEFAULT = ONE_HOUR
        fun fromMinutes(minutes: Int): WatchCadence =
            entries.firstOrNull { it.minutes == minutes } ?: DEFAULT
    }
}
