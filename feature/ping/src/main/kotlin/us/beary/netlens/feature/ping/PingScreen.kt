package us.beary.netlens.feature.ping

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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import us.beary.netlens.feature.ping.model.PingResult
import us.beary.netlens.feature.ping.model.PingSummary
import us.beary.netlens.feature.ping.model.PingUiState

@Composable
fun PingScreen(
    modifier: Modifier = Modifier,
    viewModel: PingViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    PingContent(
        state = state,
        onHostChange = viewModel::onHostChange,
        onStartPing = viewModel::startPing,
        onStopPing = viewModel::stopPing,
        modifier = modifier,
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PingContent(
    state: PingUiState,
    onHostChange: (String) -> Unit,
    onStartPing: (String, Int) -> Unit,
    onStopPing: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val countOptions = listOf(4, 8, 16)
    var selectedCount by rememberSaveable { mutableIntStateOf(4) }
    val listState = rememberLazyListState()

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
        OutlinedTextField(
            value = state.host,
            onValueChange = onHostChange,
            label = { Text("Host") },
            placeholder = { Text("e.g. 8.8.8.8") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(modifier = Modifier.height(12.dp))

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

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = { onStartPing(state.host, selectedCount) },
                enabled = state.host.isNotBlank() && !state.isPinging,
            ) {
                Text("Ping")
            }

            if (state.isPinging) {
                OutlinedButton(onClick = onStopPing) {
                    Text("Stop")
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

        if (state.results.isNotEmpty()) {
            Spacer(modifier = Modifier.height(16.dp))
            LatencyBarChart(
                results = state.results,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(100.dp),
            )
        }

        state.summary?.let { summary ->
            Spacer(modifier = Modifier.height(12.dp))
            SummaryCard(summary = summary)
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
                text = "Summary",
                style = MaterialTheme.typography.titleSmall,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                StatItem(label = "Sent", value = "${summary.transmitted}")
                StatItem(label = "Recv", value = "${summary.received}")
                StatItem(label = "Loss", value = "%.1f%%".format(summary.lossPercent))
            }
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                StatItem(label = "Min", value = "%.1f ms".format(summary.minMs))
                StatItem(label = "Avg", value = "%.1f ms".format(summary.avgMs))
                StatItem(label = "Max", value = "%.1f ms".format(summary.maxMs))
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
                text = "timeout",
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
