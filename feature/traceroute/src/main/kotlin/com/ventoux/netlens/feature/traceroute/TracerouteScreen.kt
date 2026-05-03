package com.ventoux.netlens.feature.traceroute

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ventoux.netlens.core.billing.LocalProStatus
import com.ventoux.netlens.core.network.export.ResultExporter
import com.ventoux.netlens.feature.traceroute.model.HopAnomaly
import com.ventoux.netlens.feature.traceroute.model.TracerouteHop
import com.ventoux.netlens.feature.traceroute.model.TracerouteUiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TracerouteScreen(
    onBack: () -> Unit = {},
    initialHost: String? = null,
    onNavigateToTool: (String, String) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier,
    viewModel: TracerouteViewModel = hiltViewModel(),
) {
    LaunchedEffect(initialHost) {
        if (initialHost != null) viewModel.onHostChange(initialHost)
    }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val proStatus = LocalProStatus.current
    val isPro by proStatus.isPro.collectAsStateWithLifecycle()

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.traceroute_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.navigate_back),
                        )
                    }
                },
                actions = {
                    if (state.hops.isNotEmpty()) {
                        IconButton(onClick = {
                            ResultExporter.copyToClipboard(context, "Traceroute", viewModel.buildExportText())
                        }) {
                            Icon(Icons.Default.ContentCopy, contentDescription = stringResource(R.string.traceroute_cd_copy_results))
                        }
                        if (isPro) {
                            IconButton(onClick = {
                                ResultExporter.shareAsText(context, "Traceroute Results", viewModel.buildExportText())
                            }) {
                                Icon(Icons.Default.Share, contentDescription = stringResource(R.string.traceroute_cd_share))
                            }
                        }
                    }
                },
            )
        },
    ) { innerPadding ->
        TracerouteContent(
            state = state,
            onHostChange = viewModel::onHostChange,
            onStartTrace = viewModel::startTrace,
            onStopTrace = viewModel::stopTrace,
            onNavigateToTool = onNavigateToTool,
            modifier = Modifier.padding(innerPadding),
        )
    }
}

@Composable
private fun TracerouteContent(
    state: TracerouteUiState,
    onHostChange: (String) -> Unit,
    onStartTrace: (String) -> Unit,
    onStopTrace: () -> Unit,
    onNavigateToTool: (String, String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    val maxRtt = state.hops.maxOfOrNull { it.rttMs.firstOrNull() ?: 0f } ?: 1f

    LaunchedEffect(state.hops.size) {
        if (state.hops.isNotEmpty()) {
            listState.animateScrollToItem(state.hops.lastIndex)
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
                label = { Text(stringResource(R.string.traceroute_label_host)) },
                placeholder = { Text(stringResource(R.string.traceroute_hint_host)) },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Button(
                onClick = { onStartTrace(state.host) },
                enabled = state.host.isNotBlank() && !state.isTracing,
                modifier = Modifier.weight(1f),
            ) {
                Text(stringResource(R.string.traceroute_button_trace))
            }

            if (state.isTracing) {
                OutlinedButton(
                    onClick = onStopTrace,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(R.string.traceroute_button_stop))
                }
            }
        }

        if (state.isTracing) {
            Spacer(modifier = Modifier.height(8.dp))
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }

        if (state.isGeoLoading) {
            Spacer(modifier = Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                LinearProgressIndicator(modifier = Modifier.weight(1f))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.traceroute_geo_loading),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
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

        Spacer(modifier = Modifier.height(12.dp))

        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            items(state.hops, key = { it.hopNumber }) { hop ->
                HopRow(hop = hop, maxRtt = maxRtt, onNavigateToTool = onNavigateToTool)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HopRow(
    hop: TracerouteHop,
    maxRtt: Float,
    onNavigateToTool: (String, String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val hasAnomaly = hop.anomalies.isNotEmpty()
    val containerColor = when {
        hop.isTimeout -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
        hasAnomaly -> MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f)
        else -> MaterialTheme.colorScheme.surfaceVariant
    }

    Column(modifier = modifier) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = containerColor),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "%2d".format(hop.hopNumber),
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontFamily = MaterialTheme.typography.labelSmall.fontFamily,
                        ),
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.width(28.dp),
                    )

                    Spacer(modifier = Modifier.width(12.dp))

                    if (hop.isTimeout) {
                        Text(
                            text = "*  *  *",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontFamily = MaterialTheme.typography.labelSmall.fontFamily,
                            ),
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.weight(1f),
                        )
                    } else {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = hop.ip ?: "*",
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontFamily = MaterialTheme.typography.labelSmall.fontFamily,
                                ),
                            )
                            hop.location?.let { loc ->
                                val locationText = listOfNotNull(
                                    loc.city.ifEmpty { null },
                                    loc.country.ifEmpty { null },
                                ).joinToString(", ")
                                if (locationText.isNotEmpty()) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Default.Place,
                                            contentDescription = null,
                                            modifier = Modifier.size(12.dp),
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                        Spacer(modifier = Modifier.width(2.dp))
                                        Text(
                                            text = locationText,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                        if (loc.org.isNotEmpty()) {
                                            Text(
                                                text = " · ${loc.org}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.outline,
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        hop.rttMs.firstOrNull()?.let { rtt ->
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = "%.1f ms".format(rtt),
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontFamily = MaterialTheme.typography.labelSmall.fontFamily,
                                    ),
                                    fontWeight = FontWeight.Medium,
                                    color = rttColor(rtt),
                                )
                                if (maxRtt > 0f) {
                                    Spacer(modifier = Modifier.height(2.dp))
                                    LatencyBar(
                                        fraction = (rtt / maxRtt).coerceIn(0f, 1f),
                                        rtt = rtt,
                                    )
                                }
                            }
                        }
                    }
                }

                if (hop.anomalies.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.padding(start = 40.dp),
                    ) {
                        hop.anomalies.forEach { anomaly ->
                            AnomalyBadge(anomaly)
                        }
                    }
                }
            }
        }

        if (!hop.isTimeout && hop.ip != null) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(start = 40.dp, top = 2.dp, bottom = 4.dp),
            ) {
                AssistChip(
                    onClick = { onNavigateToTool("ping", hop.ip) },
                    label = { Text(stringResource(R.string.traceroute_action_ping)) },
                )
                AssistChip(
                    onClick = { onNavigateToTool("whois", hop.ip) },
                    label = { Text(stringResource(R.string.traceroute_action_whois)) },
                )
            }
        }
    }
}

@Composable
private fun LatencyBar(fraction: Float, rtt: Float) {
    Box(
        modifier = Modifier
            .width(60.dp)
            .height(4.dp)
            .clip(RoundedCornerShape(2.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(fraction)
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(rttColor(rtt)),
        )
    }
}

@Composable
private fun AnomalyBadge(anomaly: HopAnomaly) {
    val (icon, label, color) = when (anomaly) {
        HopAnomaly.LatencySpike -> Triple(
            Icons.AutoMirrored.Filled.TrendingUp,
            stringResource(R.string.traceroute_anomaly_latency_spike),
            MaterialTheme.colorScheme.error,
        )
        HopAnomaly.GeoJump -> Triple(
            Icons.Default.Place,
            stringResource(R.string.traceroute_anomaly_geo_jump),
            MaterialTheme.colorScheme.tertiary,
        )
        HopAnomaly.ConsecutiveTimeout -> Triple(
            Icons.Default.Warning,
            stringResource(R.string.traceroute_anomaly_timeout),
            MaterialTheme.colorScheme.error,
        )
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(color.copy(alpha = 0.12f))
            .padding(horizontal = 6.dp, vertical = 2.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(12.dp),
            tint = color,
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = color,
        )
    }
}

@Composable
private fun rttColor(rtt: Float) = when {
    rtt < 50f -> MaterialTheme.colorScheme.primary
    rtt < 150f -> MaterialTheme.colorScheme.tertiary
    else -> MaterialTheme.colorScheme.error
}
