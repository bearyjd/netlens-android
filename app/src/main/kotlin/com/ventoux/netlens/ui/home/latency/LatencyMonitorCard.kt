package com.ventoux.netlens.ui.home.latency

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ElevatedCard
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.ventoux.netlens.R
import com.ventoux.netlens.ui.theme.MonoFontFamily

@Composable
fun LatencyMonitorCard(
    state: LatencyMonitorState,
    onToggleExpanded: () -> Unit,
    onToggleEnabled: () -> Unit,
    onConfigure: () -> Unit,
    onDismissConfig: () -> Unit,
    onSaveConfig: (host: String, thresholdMs: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    ElevatedCard(modifier = modifier.fillMaxWidth()) {
        if (!state.isEnabled) {
            DisabledRow(onToggleEnabled = onToggleEnabled)
        } else {
            EnabledContent(
                state = state,
                onToggleExpanded = onToggleExpanded,
                onToggleEnabled = onToggleEnabled,
                onConfigure = onConfigure,
            )
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
private fun DisabledRow(
    onToggleEnabled: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.latency_monitor_enable),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
        Switch(checked = false, onCheckedChange = { onToggleEnabled() })
    }
}

@Composable
private fun EnabledContent(
    state: LatencyMonitorState,
    onToggleExpanded: () -> Unit,
    onToggleEnabled: () -> Unit,
    onConfigure: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        HeaderRow(
            state = state,
            onToggleExpanded = onToggleExpanded,
        )
        AnimatedVisibility(visible = state.isExpanded) {
            ExpandedContent(
                state = state,
                onToggleEnabled = onToggleEnabled,
                onConfigure = onConfigure,
            )
        }
    }
}

@Composable
private fun HeaderRow(
    state: LatencyMonitorState,
    onToggleExpanded: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val latestLatency = state.dataPoints.lastOrNull { it.latencyMs != null }?.latencyMs
    val latencyColor = when {
        latestLatency == null -> MaterialTheme.colorScheme.onSurfaceVariant
        latestLatency > state.alertThresholdMs -> MaterialTheme.colorScheme.error
        latestLatency > 100f -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.primary
    }
    val chevronRotation by animateFloatAsState(
        targetValue = if (state.isExpanded) 180f else 0f,
        label = "chevron",
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.latency_monitor_title),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
        if (latestLatency != null) {
            Text(
                text = stringResource(R.string.latency_ms_format, latestLatency),
                style = MaterialTheme.typography.titleMedium.copy(fontFamily = MonoFontFamily),
                color = latencyColor,
            )
            Spacer(modifier = Modifier.width(4.dp))
        }
        IconButton(onClick = onToggleExpanded) {
            Icon(
                imageVector = Icons.Default.ExpandMore,
                contentDescription = null,
                modifier = Modifier.rotate(chevronRotation),
            )
        }
    }
}

@Composable
private fun ExpandedContent(
    state: LatencyMonitorState,
    onToggleEnabled: () -> Unit,
    onConfigure: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
    ) {
        if (state.dataPoints.isNotEmpty()) {
            LatencyChart(
                dataPoints = state.dataPoints,
                thresholdMs = state.alertThresholdMs,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp),
            )
            Spacer(modifier = Modifier.height(12.dp))
        }

        state.summary?.let { summary ->
            StatsRow(summary = summary)
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.latency_loss_format, summary.lossPercent),
                style = MaterialTheme.typography.labelSmall,
                color = if (summary.lossPercent > 0f) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(8.dp))
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
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = stringResource(R.string.latency_ms_format, valueMs),
            style = MaterialTheme.typography.bodySmall.copy(fontFamily = MonoFontFamily),
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
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
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
