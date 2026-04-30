package com.ventoux.netlens.feature.ping

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ventoux.netlens.core.network.export.ResultExporter
import com.ventoux.netlens.feature.ping.model.PingMode
import com.ventoux.netlens.feature.ping.model.PingResult
import com.ventoux.netlens.feature.ping.model.PingSummary
import com.ventoux.netlens.feature.ping.model.PingUiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PingScreen(
    onBack: () -> Unit = {},
    initialHost: String? = null,
    modifier: Modifier = Modifier,
    viewModel: PingViewModel = hiltViewModel(),
) {
    LaunchedEffect(initialHost) {
        if (initialHost != null) viewModel.onHostChange(initialHost)
    }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.ping_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.navigate_back),
                        )
                    }
                },
                actions = {
                    if (state.results.isNotEmpty()) {
                        IconButton(onClick = {
                            ResultExporter.copyToClipboard(context, "Ping", viewModel.buildExportText())
                        }) {
                            Icon(Icons.Default.ContentCopy, contentDescription = stringResource(R.string.ping_cd_copy_results))
                        }
                        IconButton(onClick = {
                            ResultExporter.shareAsText(context, "Ping Results", viewModel.buildExportText())
                        }) {
                            Icon(Icons.Default.Share, contentDescription = stringResource(R.string.ping_cd_share))
                        }
                    }
                },
            )
        },
    ) { innerPadding ->
        PingContent(
            state = state,
            onHostChange = viewModel::onHostChange,
            onModeChanged = viewModel::onModeChanged,
            onStartPing = viewModel::startPing,
            onStopPing = viewModel::stopPing,
            onCopyResults = {
                ResultExporter.copyToClipboard(context, "Ping", viewModel.buildExportText())
            },
            modifier = Modifier.padding(innerPadding),
        )
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun PingContent(
    state: PingUiState,
    onHostChange: (String) -> Unit,
    onModeChanged: (PingMode) -> Unit,
    onStartPing: (String, Int) -> Unit,
    onStopPing: () -> Unit,
    onCopyResults: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val countOptions = listOf(4, 8, 16)
    var selectedCount by rememberSaveable { mutableIntStateOf(4) }
    val listState = rememberLazyListState()

    var pendingHost by remember { mutableStateOf("") }
    var pendingCount by remember { mutableIntStateOf(0) }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { _ ->
        onStartPing(pendingHost, pendingCount)
    }

    LaunchedEffect(state.results.size) {
        if (state.results.isNotEmpty()) {
            listState.animateScrollToItem(state.results.lastIndex)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = state.host,
                onValueChange = onHostChange,
                label = { Text(stringResource(R.string.ping_label_host)) },
                placeholder = { Text(stringResource(R.string.ping_placeholder_host)) },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            if (state.results.isNotEmpty()) {
                IconButton(onClick = onCopyResults) {
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = stringResource(R.string.ping_cd_copy_results),
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        ModeSelector(
            selectedMode = state.mode,
            onModeChanged = onModeChanged,
            enabled = !state.isPinging,
        )

        Spacer(modifier = Modifier.height(12.dp))

        if (state.mode == PingMode.FIXED) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                countOptions.forEach { count ->
                    FilterChip(
                        selected = selectedCount == count,
                        onClick = { selectedCount = count },
                        label = { Text("$count") },
                    )
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = {
                    if (state.mode == PingMode.CONTINUOUS && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                            != PackageManager.PERMISSION_GRANTED
                    ) {
                        pendingHost = state.host
                        pendingCount = selectedCount
                        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    } else {
                        onStartPing(state.host, selectedCount)
                    }
                },
                enabled = state.host.isNotBlank() && !state.isPinging,
            ) {
                Text(stringResource(R.string.ping_button_start))
            }

            if (state.isPinging && state.mode == PingMode.CONTINUOUS) {
                Button(
                    onClick = onStopPing,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                    ),
                ) {
                    Text(stringResource(R.string.ping_button_stop))
                }
            } else if (state.isPinging) {
                OutlinedButton(onClick = onStopPing) {
                    Text(stringResource(R.string.ping_button_stop))
                }
            }
        }

        state.error?.let { error ->
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = error,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
            )
        }

        if (state.isPinging && state.mode == PingMode.CONTINUOUS && state.totalSent > 0) {
            Spacer(modifier = Modifier.height(12.dp))
            LiveStatsBar(state = state)
        }

        if (state.results.isNotEmpty()) {
            Spacer(modifier = Modifier.height(16.dp))
            LatencyBarChart(
                results = if (state.mode == PingMode.CONTINUOUS) {
                    state.results.takeLast(60)
                } else {
                    state.results
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(100.dp),
            )
        }

        if (!state.isPinging || state.mode == PingMode.FIXED) {
            state.summary?.let { summary ->
                Spacer(modifier = Modifier.height(12.dp))
                SummaryCard(summary = summary)
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            items(state.results, key = { it.sequenceNumber }) { result ->
                ResultRow(result = result)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ModeSelector(
    selectedMode: PingMode,
    onModeChanged: (PingMode) -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    SingleChoiceSegmentedButtonRow(modifier = modifier.fillMaxWidth()) {
        PingMode.entries.forEachIndexed { index, mode ->
            SegmentedButton(
                selected = selectedMode == mode,
                onClick = { onModeChanged(mode) },
                shape = SegmentedButtonDefaults.itemShape(index, PingMode.entries.size),
                enabled = enabled,
            ) {
                Text(
                    if (mode == PingMode.FIXED) {
                        stringResource(R.string.ping_mode_fixed)
                    } else {
                        stringResource(R.string.ping_mode_continuous)
                    },
                )
            }
        }
    }
}

@Composable
private fun LiveStatsBar(state: PingUiState, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
        ),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = stringResource(R.string.ping_continuous_elapsed, formatElapsed(state.elapsedMs)),
                style = MaterialTheme.typography.labelMedium,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                StatItem(label = stringResource(R.string.ping_stat_sent), value = "${state.totalSent}")
                StatItem(label = stringResource(R.string.ping_stat_recv), value = "${state.totalReceived}")
                StatItem(
                    label = stringResource(R.string.ping_stat_loss),
                    value = "%.0f%%".format(state.summary?.lossPercent ?: 0f),
                )
                StatItem(
                    label = stringResource(R.string.ping_stat_avg),
                    value = "%.1f ms".format(state.summary?.avgMs ?: 0f),
                )
            }
        }
    }
}

private fun formatElapsed(ms: Long): String {
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return if (minutes > 0) "${minutes}m ${seconds}s" else "${seconds}s"
}

@Composable
private fun LatencyBarChart(
    results: List<PingResult>,
    modifier: Modifier = Modifier,
) {
    val accentColor = MaterialTheme.colorScheme.primary
    val timeoutColor = MaterialTheme.colorScheme.error

    Canvas(modifier = modifier) {
        val latencies = results.map { it.latencyMs }
        val maxLatency = latencies.filterNotNull().maxOrNull() ?: return@Canvas
        if (maxLatency <= 0f) return@Canvas

        val barWidth = size.width / latencies.size.coerceAtLeast(1)
        val padding = 2.dp.toPx()

        latencies.forEachIndexed { index, latency ->
            val barHeight = if (latency != null) {
                (latency / maxLatency) * size.height
            } else {
                size.height
            }
            val color = if (latency != null) accentColor else timeoutColor

            drawRect(
                color = color,
                topLeft = Offset(
                    x = index * barWidth + padding,
                    y = size.height - barHeight,
                ),
                size = androidx.compose.ui.geometry.Size(
                    width = (barWidth - padding * 2).coerceAtLeast(1f),
                    height = barHeight,
                ),
            )
        }
    }
}

@Composable
private fun SummaryCard(
    summary: PingSummary,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = stringResource(R.string.ping_label_summary),
                style = MaterialTheme.typography.titleSmall,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                StatItem(label = stringResource(R.string.ping_stat_sent), value = "${summary.transmitted}")
                StatItem(label = stringResource(R.string.ping_stat_recv), value = "${summary.received}")
                StatItem(label = stringResource(R.string.ping_stat_loss), value = "%.1f%%".format(summary.lossPercent))
            }
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                StatItem(label = stringResource(R.string.ping_stat_min), value = "%.1f ms".format(summary.minMs))
                StatItem(label = stringResource(R.string.ping_stat_avg), value = "%.1f ms".format(summary.avgMs))
                StatItem(label = stringResource(R.string.ping_stat_max), value = "%.1f ms".format(summary.maxMs))
                StatItem(label = stringResource(R.string.ping_stat_jitter), value = "%.1f ms".format(summary.jitterMs))
            }
        }
    }
}

@Composable
private fun StatItem(
    label: String,
    value: String,
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
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun ResultRow(
    result: PingResult,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = "seq=${result.sequenceNumber}",
            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
        )
        if (result.isTimeout) {
            Text(
                text = stringResource(R.string.ping_result_timeout),
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                color = MaterialTheme.colorScheme.error,
            )
        } else {
            Row {
                result.ttl?.let { ttl ->
                    Text(
                        text = "ttl=$ttl",
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                }
                Text(
                    text = "%.1f ms".format(result.latencyMs ?: 0f),
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}
