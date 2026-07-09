package com.ventouxlabs.netlens.ui.home.latency

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.ventouxlabs.netlens.R
import com.ventouxlabs.netlens.core.ui.LocalStatusColors
import com.ventouxlabs.netlens.core.ui.Spacing
import com.ventouxlabs.netlens.core.ui.withTabularFigures
import com.ventouxlabs.netlens.ui.theme.MonoFontFamily

/**
 * Drill-down detail for the dashboard's latency tile: chart, stats, host,
 * and monitor controls. Shown below the metrics row when the tile is
 * expanded. The glanceable summary lives in the tile; this card is the
 * "one tap deeper" technical breakdown.
 */
@Composable
fun LatencyDetailCard(
    state: LatencyMonitorState,
    onToggleEnabled: () -> Unit,
    onConfigure: () -> Unit,
    onDismissConfig: () -> Unit,
    onSaveConfig: (host: String, thresholdMs: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.lg),
        ) {
            if (state.dataPoints.isNotEmpty()) {
                LatencyChart(
                    dataPoints = state.dataPoints,
                    thresholdMs = state.alertThresholdMs,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp),
                )
                Spacer(modifier = Modifier.height(Spacing.md))
            }

            state.summary?.let { summary ->
                StatsRow(summary = summary)
                Spacer(modifier = Modifier.height(Spacing.xs))
                Text(
                    text = stringResource(R.string.latency_loss_format, summary.lossPercent),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (summary.lossPercent > 0f) {
                        LocalStatusColors.current.fail
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
                Spacer(modifier = Modifier.height(Spacing.sm))
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = state.host,
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = MonoFontFamily),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onConfigure) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = stringResource(R.string.latency_configure),
                    )
                }
                Switch(
                    checked = state.isEnabled,
                    onCheckedChange = { onToggleEnabled() },
                )
            }
        }
    }

    if (state.isConfiguring) {
        ConfigDialog(
            initialHost = state.host,
            initialThresholdMs = state.alertThresholdMs,
            onDismiss = onDismissConfig,
            onSave = onSaveConfig,
        )
    }
}

@Composable
private fun StatsRow(
    summary: LatencySummary,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        StatCell(label = stringResource(R.string.latency_min), valueMs = summary.minMs)
        StatCell(label = stringResource(R.string.latency_avg), valueMs = summary.avgMs)
        StatCell(label = stringResource(R.string.latency_max), valueMs = summary.maxMs)
        StatCell(label = stringResource(R.string.latency_jitter), valueMs = summary.jitterMs)
    }
}

@Composable
private fun StatCell(
    label: String,
    valueMs: Float,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = stringResource(R.string.latency_ms_format, valueMs),
            style = MaterialTheme.typography.bodyMedium.withTabularFigures(),
        )
    }
}

@Composable
private fun ConfigDialog(
    initialHost: String,
    initialThresholdMs: Int,
    onDismiss: () -> Unit,
    onSave: (host: String, thresholdMs: Int) -> Unit,
) {
    var host by remember { mutableStateOf(initialHost) }
    var thresholdSlider by remember {
        mutableFloatStateOf(initialThresholdMs.toFloat().coerceIn(50f, 1000f))
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.latency_configure)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                OutlinedTextField(
                    value = host,
                    onValueChange = { host = it },
                    label = { Text(stringResource(R.string.latency_host)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    text = stringResource(R.string.latency_alert_threshold),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Slider(
                    value = thresholdSlider,
                    onValueChange = { thresholdSlider = it },
                    valueRange = 50f..1000f,
                    steps = 18, // (1000 - 50) / 50 - 1 = 18 steps for step size 50
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    text = stringResource(R.string.latency_threshold_format, thresholdSlider.toInt()),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(host.trim(), thresholdSlider.toInt()) },
                enabled = host.isNotBlank(),
            ) {
                Text(stringResource(R.string.latency_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.latency_cancel))
            }
        },
    )
}
