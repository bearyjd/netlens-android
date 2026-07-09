package com.ventouxlabs.netlens.widget.util

/**
 * Formats a timestamp as a short "time ago" label (e.g. "5m", "2h", "3d") for
 * widget footers/headers. Returns an empty string for a zero or negative
 * timestamp (no scan has happened yet).
 */
fun relativeTimeLabel(timestampMs: Long, now: Long = System.currentTimeMillis()): String {
    if (timestampMs <= 0L) return ""
    val diffMs = now - timestampMs
    val minutes = (diffMs / 60_000).toInt()
    return when {
        minutes < 1 -> "just now"
        minutes < 60 -> "${minutes}m"
        minutes < 1440 -> "${minutes / 60}h"
        else -> "${minutes / 1440}d"
    }
}
