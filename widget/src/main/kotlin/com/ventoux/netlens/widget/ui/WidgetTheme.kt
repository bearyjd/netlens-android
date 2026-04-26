package com.ventoux.netlens.widget.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

object WidgetTheme {

    val CORNER_RADIUS = 16.dp
    val PADDING = 12.dp

    val BACKGROUND = Color(0xE6161616)
    val TEXT_PRIMARY = Color.White
    val TEXT_SECONDARY = Color.White.copy(alpha = 0.7f)
    val TEXT_MUTED = Color.White.copy(alpha = 0.45f)

    val SCORE_GREEN = Color(0xFF4CAF50)
    val SCORE_AMBER = Color(0xFFFFC107)
    val SCORE_RED = Color(0xFFF44336)
    val SCORE_GRAY = Color(0xFF9E9E9E)

    fun scoreColor(grade: String): Color = when (grade.uppercase()) {
        "A", "B" -> SCORE_GREEN
        "C" -> SCORE_AMBER
        "D", "F" -> SCORE_RED
        else -> SCORE_GRAY
    }

    fun encryptionSuffix(type: String): String = when (type.uppercase()) {
        "WPA3" -> " ✓"
        "WEP" -> " ⚠"
        else -> ""
    }

    fun encryptionColor(type: String): Color = when (type.uppercase()) {
        "WPA3" -> SCORE_GREEN
        "WEP" -> SCORE_RED
        else -> TEXT_SECONDARY
    }

    fun speedColor(label: String): Color = when (label.lowercase()) {
        "fast" -> SCORE_GREEN
        "medium" -> SCORE_AMBER
        "slow" -> SCORE_RED
        else -> SCORE_GRAY
    }

    fun speedIcon(label: String): String = when (label.lowercase()) {
        "fast" -> "���"
        "medium" -> "~"
        "slow" -> "▼"
        else -> ""
    }

    fun relativeTime(timestampMs: Long, now: Long = System.currentTimeMillis()): String {
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
}
