package com.ventouxlabs.netlens.core.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable

/**
 * Copy + Share actions for a tool's `TopAppBar`, emitted straight into the bar's
 * `actions` row. Renders nothing when [hasResults] is false, so callers pass
 * their own "is there anything to export yet" predicate rather than wrapping the
 * call in an `if`.
 *
 * Share is Pro-only and [isPro] has **no default** — a caller must state the
 * gate. A `Boolean = true` default would let a new call site silently expose the
 * Pro action, which is exactly how `WifiContent` drifted.
 *
 * Screens that hand their export callbacks down to a separate stateless
 * `Content` composable (lanscan, mdns, devices) gate with a nullable lambda
 * instead; they render their own buttons and do not use this.
 */
@Composable
fun ResultActions(
    hasResults: Boolean,
    isPro: Boolean,
    onCopy: () -> Unit,
    copyContentDescription: String,
    onShare: () -> Unit,
    shareContentDescription: String,
) {
    if (!hasResults) return

    IconButton(onClick = onCopy) {
        Icon(Icons.Default.ContentCopy, contentDescription = copyContentDescription)
    }
    if (isPro) {
        IconButton(onClick = onShare) {
            Icon(Icons.Default.Share, contentDescription = shareContentDescription)
        }
    }
}
