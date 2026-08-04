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
 * `lanscan` and `mdns` still render their own buttons. They gate with a nullable
 * `onShare` lambda because their export callbacks are threaded through a stateless
 * `Content` composable, and lanscan has three separate export targets (results,
 * event, saved inventory). Add an `onShare: (() -> Unit)?` overload here if you
 * want to fold them in as well.
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
