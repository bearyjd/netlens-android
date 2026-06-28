package com.ventouxlabs.netlens.feature.speedtest

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ventouxlabs.netlens.core.data.model.SpeedTestHistoryEntry
import com.ventouxlabs.netlens.core.billing.LocalProStatus
import com.ventouxlabs.netlens.core.network.export.ResultExporter
import com.ventouxlabs.netlens.feature.speedtest.model.SpeedTestPhase
import com.ventouxlabs.netlens.feature.speedtest.model.SpeedTestUiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpeedTestScreen(
    onBack: () -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: SpeedTestViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val history by viewModel.history.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val proStatus = LocalProStatus.current
    val isPro by proStatus.isPro.collectAsStateWithLifecycle()
    val hasResults = state.phase == SpeedTestPhase.COMPLETE

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.speedtest_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.navigate_back),
                        )
                    }
                },
                actions = {
                    if (hasResults) {
                        IconButton(onClick = {
                            ResultExporter.copyToClipboard(context, "Speed Test", viewModel.buildExportText())
                        }) {
                            Icon(Icons.Default.ContentCopy, contentDescription = stringResource(R.string.speedtest_cd_copy_results))
                        }
                        if (isPro) {
                            IconButton(onClick = {
                                ResultExporter.shareAsText(context, "Speed Test Results", viewModel.buildExportText())
                            }) {
                                Icon(Icons.Default.Share, contentDescription = stringResource(R.string.speedtest_cd_share))
                            }
                        }
                    }
                },
            )
        },
    ) { innerPadding ->
        SpeedTestContent(
            state = state,
            history = history,
            onStartTest = viewModel::startTest,
            onCancelTest = viewModel::cancelTest,
            modifier = Modifier.padding(innerPadding),
        )
    }
}

@Composable
private fun SpeedTestContent(
    state: SpeedTestUiState,
    history: List<SpeedTestHistoryEntry>,
    onStartTest: () -> Unit,
    onCancelTest: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        item {
            SpeedGauge(
                speed = when (state.phase) {
                    SpeedTestPhase.DOWNLOAD -> state.downloadMbps
                    SpeedTestPhase.UPLOAD -> state.uploadMbps
                    SpeedTestPhase.COMPLETE -> maxOf(state.downloadMbps, state.uploadMbps)
                    else -> 0f
                },
                progress = state.progress,
                phase = state.phase,
                modifier = Modifier.size(200.dp),
            )
        }

        item {
            PhaseIndicator(phase = state.phase)
        }

        item {
            if (state.isRunning) {
                OutlinedButton(
                    onClick = onCancelTest,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.speedtest_button_cancel))
                }
            } else {
                Button(
                    onClick = onStartTest,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.speedtest_button_start))
                }
            }
        }

        state.error?.let { error ->
            item {
                Text(
                    text = error,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }

        if (state.phase == SpeedTestPhase.COMPLETE || state.downloadMbps > 0f || state.uploadMbps > 0f) {
            item {
                ResultsCard(state = state)
            }
        }

        if (history.size >= 2) {
            item {
                HistorySparklineCard(history = history)
            }
        }
    }
}

@Composable
private fun SpeedGauge(
    speed: Float,
    progress: Float,
    phase: SpeedTestPhase,
    modifier: Modifier = Modifier,
) {
    val animatedProgress by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 300),
        label = "gauge_progress",
    )

    val trackColor = MaterialTheme.colorScheme.surfaceVariant
    val activeColor = when (phase) {
        SpeedTestPhase.DOWNLOAD -> MaterialTheme.colorScheme.primary
        SpeedTestPhase.UPLOAD -> MaterialTheme.colorScheme.tertiary
        SpeedTestPhase.LATENCY -> MaterialTheme.colorScheme.secondary
        else -> MaterialTheme.colorScheme.primary
    }

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val strokeWidth = 16.dp.toPx()
            val arcSize = Size(size.width - strokeWidth, size.height - strokeWidth)
            val topLeft = Offset(strokeWidth / 2, strokeWidth / 2)

            drawArc(
                color = trackColor,
                startAngle = 135f,
                sweepAngle = 270f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
            )

            if (phase != SpeedTestPhase.IDLE) {
                drawArc(
                    color = activeColor,
                    startAngle = 135f,
                    sweepAngle = 270f * animatedProgress,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
                )
            }
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "%.1f".format(speed),
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = stringResource(R.string.speedtest_unit_mbps),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun PhaseIndicator(
    phase: SpeedTestPhase,
    modifier: Modifier = Modifier,
) {
    val text = when (phase) {
        SpeedTestPhase.IDLE -> ""
        SpeedTestPhase.LATENCY -> stringResource(R.string.speedtest_phase_latency)
        SpeedTestPhase.DOWNLOAD -> stringResource(R.string.speedtest_phase_download)
        SpeedTestPhase.UPLOAD -> stringResource(R.string.speedtest_phase_upload)
        SpeedTestPhase.COMPLETE -> stringResource(R.string.speedtest_label_results)
    }

    if (text.isNotEmpty()) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = modifier,
        )
    }
}

@Composable
private fun ResultsCard(
    state: SpeedTestUiState,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.speedtest_label_results),
                style = MaterialTheme.typography.titleSmall,
            )
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                StatItem(
                    label = stringResource(R.string.speedtest_label_download),
                    value = "%.1f".format(state.downloadMbps),
                    unit = stringResource(R.string.speedtest_unit_mbps),
                )
                StatItem(
                    label = stringResource(R.string.speedtest_label_upload),
                    value = "%.1f".format(state.uploadMbps),
                    unit = stringResource(R.string.speedtest_unit_mbps),
                )
                StatItem(
                    label = stringResource(R.string.speedtest_label_latency),
                    value = "${state.latencyMs}",
                    unit = stringResource(R.string.speedtest_unit_ms),
                )
            }
        }
    }
}

@Composable
private fun StatItem(
    label: String,
    value: String,
    unit: String,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = unit,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun HistorySparklineCard(
    history: List<SpeedTestHistoryEntry>,
    modifier: Modifier = Modifier,
) {
    val downloadColor = MaterialTheme.colorScheme.primary
    val uploadColor = MaterialTheme.colorScheme.tertiary

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.speedtest_label_history),
                style = MaterialTheme.typography.titleSmall,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                LegendDot(
                    color = downloadColor,
                    label = stringResource(R.string.speedtest_chart_download),
                )
                LegendDot(
                    color = uploadColor,
                    label = stringResource(R.string.speedtest_chart_upload),
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            SparklineChart(
                history = history,
                downloadColor = downloadColor,
                uploadColor = uploadColor,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp),
            )
        }
    }
}

@Composable
private fun LegendDot(
    color: Color,
    label: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Canvas(modifier = Modifier.size(8.dp)) {
            drawCircle(color = color)
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SparklineChart(
    history: List<SpeedTestHistoryEntry>,
    downloadColor: Color,
    uploadColor: Color,
    modifier: Modifier = Modifier,
) {
    val chronological = history.sortedBy { it.timestamp }
    val maxSpeed = chronological.maxOf { maxOf(it.downloadMbps, it.uploadMbps) }.coerceAtLeast(1f)

    Canvas(modifier = modifier) {
        val pointCount = chronological.size
        if (pointCount < 2) return@Canvas

        val stepX = size.width / (pointCount - 1)
        val chartHeight = size.height

        fun buildPath(values: List<Float>): Path = Path().apply {
            values.forEachIndexed { i, value ->
                val x = i * stepX
                val y = chartHeight - (value / maxSpeed) * chartHeight
                if (i == 0) moveTo(x, y) else lineTo(x, y)
            }
        }

        val downloadPath = buildPath(chronological.map { it.downloadMbps })
        val uploadPath = buildPath(chronological.map { it.uploadMbps })

        drawPath(
            path = downloadPath,
            color = downloadColor,
            style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round),
        )
        drawPath(
            path = uploadPath,
            color = uploadColor,
            style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round),
        )

        chronological.forEachIndexed { i, entry ->
            val x = i * stepX
            drawCircle(
                color = downloadColor,
                radius = 3.dp.toPx(),
                center = Offset(x, chartHeight - (entry.downloadMbps / maxSpeed) * chartHeight),
            )
            drawCircle(
                color = uploadColor,
                radius = 3.dp.toPx(),
                center = Offset(x, chartHeight - (entry.uploadMbps / maxSpeed) * chartHeight),
            )
        }
    }
}
