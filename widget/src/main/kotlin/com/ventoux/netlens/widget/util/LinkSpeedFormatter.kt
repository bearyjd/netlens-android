package com.ventoux.netlens.widget.util

fun formatLinkSpeed(mbps: Int): String = when {
    mbps <= 0 -> "—"
    mbps >= 1000 -> "%.1fG".format(mbps / 1000f)
    else -> "${mbps}M"
}
