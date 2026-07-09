package com.ventouxlabs.netlens.ui.home.dashboard

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ventouxlabs.netlens.R
import com.ventouxlabs.netlens.core.ui.LocalStatusColors
import com.ventouxlabs.netlens.core.ui.Spacing
import com.ventouxlabs.netlens.core.ui.withTabularFigures
import com.ventouxlabs.netlens.ui.home.latency.LatencyDataPoint
import com.ventouxlabs.netlens.ui.theme.MonoFontFamily

/**
 * Glanceable dashboard tiles: one label, one big value, optional sparkline.
 * Deliberately no raw detail — a tap goes one level deeper. Value colors use
 * the three-state status system only.
 */
@Composable
fun MetricTile(
    label: String,
    value: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    valueColor: Color = MaterialTheme.colorScheme.onSurface,
    useMonoValue: Boolean = false,
    secondaryValue: String? = null,
    sparkline: (@Composable () -> Unit)? = null,
) {
    Card(
        onClick = onClick,
        modifier = modifier.defaultMinSize(minHeight = 88.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(modifier = Modifier.padding(Spacing.md)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(Spacing.xs))
            Text(
                text = value,
                style = if (useMonoValue) {
                    MaterialTheme.typography.titleMedium.copy(fontFamily = MonoFontFamily)
                } else {
                    MaterialTheme.typography.titleLarge.withTabularFigures()
                },
                color = valueColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (secondaryValue != null) {
                Text(
                    text = secondaryValue,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (sparkline != null) {
                Spacer(modifier = Modifier.height(Spacing.xs))
                sparkline()
            }
        }
    }
}

/**
 * Latency tile: latest round-trip value colored by the alert threshold, with
 * a mini sparkline of recent samples. Shows "Off" when the monitor is
 * disabled; the tap behavior (enable vs. expand) is decided by the caller.
 */
@Composable
fun LatencyTile(
    latestLatencyMs: Float?,
    isEnabled: Boolean,
    alertThresholdMs: Int,
    dataPoints: List<LatencyDataPoint>,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val status = LocalStatusColors.current
    val valueColor = when {
        !isEnabled || latestLatencyMs == null -> status.muted
        latestLatencyMs > alertThresholdMs -> status.fail
        latestLatencyMs > WARN_LATENCY_MS -> status.warn
        else -> status.pass
    }
    val value = when {
        !isEnabled -> stringResource(R.string.metric_latency_off)
        latestLatencyMs == null -> "—"
        else -> stringResource(R.string.latency_ms_format, latestLatencyMs)
    }
    MetricTile(
        label = stringResource(R.string.metric_latency),
        value = value,
        onClick = onClick,
        modifier = modifier,
        valueColor = valueColor,
        sparkline = if (isEnabled && dataPoints.isNotEmpty()) {
            { LatencySparkline(dataPoints = dataPoints, color = valueColor) }
        } else {
            null
        },
    )
}

@Composable
private fun LatencySparkline(
    dataPoints: List<LatencyDataPoint>,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(20.dp),
    ) {
        val samples = dataPoints.mapNotNull { it.latencyMs }
        if (samples.size < 2) return@Canvas
        val max = samples.max().coerceAtLeast(1f)
        val min = samples.min()
        val range = (max - min).coerceAtLeast(1f)
        val stepX = size.width / (samples.size - 1)
        val path = Path()
        samples.forEachIndexed { index, sample ->
            val x = index * stepX
            val y = size.height - ((sample - min) / range) * size.height
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(
            path = path,
            color = color,
            style = Stroke(width = 1.5.dp.toPx(), cap = StrokeCap.Round),
        )
    }
}

/**
 * The dashboard's metrics row: latency, VPN presence (detection only — this
 * app does not provide a VPN), and local IP.
 */
@Composable
fun MetricsRow(
    latestLatencyMs: Float?,
    latencyEnabled: Boolean,
    alertThresholdMs: Int,
    latencyDataPoints: List<LatencyDataPoint>,
    isVpnActive: Boolean,
    localIp: String?,
    gatewayIp: String?,
    onLatencyClick: () -> Unit,
    onVpnClick: () -> Unit,
    onIpClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val status = LocalStatusColors.current
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        LatencyTile(
            latestLatencyMs = latestLatencyMs,
            isEnabled = latencyEnabled,
            alertThresholdMs = alertThresholdMs,
            dataPoints = latencyDataPoints,
            onClick = onLatencyClick,
            modifier = Modifier.weight(1f),
        )
        MetricTile(
            label = stringResource(R.string.metric_vpn),
            value = stringResource(
                if (isVpnActive) R.string.metric_vpn_active else R.string.metric_vpn_none,
            ),
            onClick = onVpnClick,
            modifier = Modifier.weight(1f),
            // Neutral tint on purpose: NetLens only DETECTS a VPN. Coloring
            // presence with the "secure" teal would frame it as protection
            // the app is party to.
            valueColor = if (isVpnActive) {
                MaterialTheme.colorScheme.onSurface
            } else {
                status.muted
            },
        )
        MetricTile(
            label = stringResource(R.string.metric_local_ip),
            value = localIp ?: "—",
            onClick = onIpClick,
            modifier = Modifier.weight(1f),
            useMonoValue = true,
            secondaryValue = gatewayIp?.let {
                stringResource(R.string.metric_gateway_format, it)
            },
        )
    }
}

// Above this round-trip (and below the user's alert threshold) latency is
// shown as "attention" amber rather than teal.
private const val WARN_LATENCY_MS = 100f
