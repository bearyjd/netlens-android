package com.ventouxlabs.netlens.feature.portscan

import androidx.compose.animation.animateColorAsState
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AssistChip
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.ventouxlabs.netlens.core.billing.LocalProStatus
import com.ventouxlabs.netlens.core.network.export.ResultExporter
import com.ventouxlabs.netlens.core.ui.LocalStatusColors
import com.ventouxlabs.netlens.core.ui.StatusColors
import com.ventouxlabs.netlens.core.ui.resolve
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ventouxlabs.netlens.feature.portscan.model.PortResult
import com.ventouxlabs.netlens.feature.portscan.model.PortRiskLevel
import com.ventouxlabs.netlens.feature.portscan.model.PortScanUiState
import com.ventouxlabs.netlens.feature.portscan.model.WellKnownPorts

private const val PRESET_COMMON = 0
private const val PRESET_ALL = 1
private const val PRESET_CUSTOM = 2

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PortScanScreen(
    onBack: () -> Unit = {},
    initialHost: String? = null,
    viewModel: PortScanViewModel = hiltViewModel(),
    onNavigateToTool: (String, String) -> Unit = { _, _ -> },
) {
    LaunchedEffect(initialHost) {
        if (initialHost != null) viewModel.onHostChanged(initialHost)
    }
    val uiState by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val proStatus = LocalProStatus.current
    val isPro by proStatus.isPro.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.portscan_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.navigate_back),
                        )
                    }
                },
                actions = {
                    if (uiState.results.isNotEmpty()) {
                        val clipboardLabel = stringResource(R.string.portscan_export_label_clipboard)
                        val shareLabel = stringResource(R.string.portscan_export_label_share)
                        IconButton(onClick = {
                            ResultExporter.copyToClipboard(context, clipboardLabel, viewModel.buildExportText())
                        }) {
                            Icon(Icons.Default.ContentCopy, contentDescription = stringResource(R.string.portscan_cd_copy_open_ports))
                        }
                        if (isPro) {
                            IconButton(onClick = {
                                ResultExporter.shareAsText(context, shareLabel, viewModel.buildExportText())
                            }) {
                                Icon(Icons.Default.Share, contentDescription = stringResource(R.string.portscan_cd_share))
                            }
                        }
                    }
                },
            )
        },
    ) { padding ->
        PortScanContent(
            state = uiState,
            onScan = { host, ports -> viewModel.scan(host, ports) },
            onCancel = viewModel::cancelScan,
            onNavigateToTool = onNavigateToTool,
            modifier = Modifier.padding(padding),
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PortScanContent(
    state: PortScanUiState,
    onScan: (String, List<Int>) -> Unit,
    onCancel: () -> Unit,
    onNavigateToTool: (String, String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var host by rememberSaveable { mutableStateOf("") }
    var selectedPreset by rememberSaveable { mutableIntStateOf(PRESET_COMMON) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
    ) {
        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = host,
            onValueChange = { host = it },
            label = { Text(stringResource(R.string.portscan_label_host)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(modifier = Modifier.height(12.dp))

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterChip(
                selected = selectedPreset == PRESET_COMMON,
                onClick = { selectedPreset = PRESET_COMMON },
                label = { Text(stringResource(R.string.portscan_chip_common)) },
            )
            FilterChip(
                selected = selectedPreset == PRESET_ALL,
                onClick = { selectedPreset = PRESET_ALL },
                label = { Text(stringResource(R.string.portscan_chip_all)) },
            )
            FilterChip(
                selected = selectedPreset == PRESET_CUSTOM,
                onClick = { selectedPreset = PRESET_CUSTOM },
                label = { Text(stringResource(R.string.portscan_chip_custom)) },
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (state.isScanning) {
            Button(
                onClick = onCancel,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                ),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Default.Close, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.portscan_button_cancel))
            }
        } else {
            Button(
                onClick = {
                    val ports = portsForPreset(selectedPreset)
                    onScan(host.trim(), ports)
                },
                enabled = host.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Default.Search, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.portscan_button_scan))
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (state.isScanning || state.results.isNotEmpty()) {
            LinearProgressIndicator(
                progress = { state.progress },
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(modifier = Modifier.height(8.dp))

            StatsRow(state = state)

            Spacer(modifier = Modifier.height(8.dp))
        }

        state.error?.let { error ->
            Text(
                text = error.resolve(),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(modifier = Modifier.height(8.dp))
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            val sortedResults = state.results.sortedWith(
                compareBy<PortResult> { it.riskLevel.sortOrder }.thenBy { it.port },
            )
            items(sortedResults, key = { it.port }) { result ->
                PortResultRow(result = result, host = host.trim(), onNavigateToTool = onNavigateToTool)
            }
        }
    }
}

@Composable
private fun StatsRow(state: PortScanUiState) {
    val status = LocalStatusColors.current
    val criticalCount = state.results.count { it.riskLevel == PortRiskLevel.CRITICAL }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                text = stringResource(R.string.portscan_stats_open, state.openCount),
                color = status.pass,
                style = MaterialTheme.typography.labelLarge,
            )
            if (criticalCount > 0) {
                Text(
                    text = stringResource(R.string.portscan_stats_critical, criticalCount),
                    color = status.fail,
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }
        Text(
            text = stringResource(R.string.portscan_stats_scanned, state.results.size),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PortResultRow(
    result: PortResult,
    host: String,
    onNavigateToTool: (String, String) -> Unit,
) {
    val status = LocalStatusColors.current
    val iconColor by animateColorAsState(
        targetValue = if (result.isOpen) riskColor(result.riskLevel, status) else status.muted,
        label = "portIconColor",
    )

    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp, horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = if (result.isOpen) Icons.Default.CheckCircle else Icons.Default.Close,
                contentDescription = if (result.isOpen) stringResource(R.string.portscan_cd_open) else stringResource(R.string.portscan_cd_closed),
                tint = iconColor,
                modifier = Modifier.size(20.dp),
            )

            Spacer(modifier = Modifier.width(12.dp))

            Text(
                text = result.port.toString(),
                style = MaterialTheme.typography.bodyMedium.copy(fontFamily = MaterialTheme.typography.labelSmall.fontFamily),
                modifier = Modifier.width(56.dp),
            )

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = result.serviceName,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (result.isOpen) {
                            MaterialTheme.colorScheme.onSurface
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                    if (result.isOpen && result.riskLevel != PortRiskLevel.CLOSED) {
                        RiskBadge(riskLevel = result.riskLevel)
                    }
                }
                if (result.isOpen && result.description.isNotEmpty()) {
                    Text(
                        text = result.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                    )
                }
            }

            if (result.isOpen && result.latencyMs > 0) {
                Text(
                    text = stringResource(R.string.portscan_latency_ms, result.latencyMs),
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = MaterialTheme.typography.labelSmall.fontFamily),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (result.isOpen && result.port in HTTP_PORTS) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(start = 32.dp, top = 2.dp),
            ) {
                val scheme = if (result.port in TLS_PORTS) "https" else "http"
                val portSuffix = if (result.port == 80 || result.port == 443) "" else ":${result.port}"
                AssistChip(
                    onClick = { onNavigateToTool("httptester", "$scheme://$host$portSuffix") },
                    label = { Text(stringResource(R.string.portscan_action_http_test)) },
                )
                if (result.port in TLS_PORTS) {
                    AssistChip(
                        onClick = { onNavigateToTool("tls", host) },
                        label = { Text(stringResource(R.string.portscan_action_tls_inspect)) },
                    )
                }
            }
        }
    }
}

@Composable
private fun RiskBadge(riskLevel: PortRiskLevel) {
    val color = riskColor(riskLevel, LocalStatusColors.current)
    val label = when (riskLevel) {
        PortRiskLevel.CRITICAL -> stringResource(R.string.portscan_risk_critical)
        PortRiskLevel.WARNING -> stringResource(R.string.portscan_risk_warning)
        PortRiskLevel.INFO -> stringResource(R.string.portscan_risk_info)
        PortRiskLevel.CLOSED -> return
    }
    Text(
        text = label,
        style = MaterialTheme.typography.labelSmall,
        color = color,
        modifier = Modifier
            .padding(horizontal = 6.dp, vertical = 2.dp),
    )
}

private fun riskColor(riskLevel: PortRiskLevel, status: StatusColors): Color = when (riskLevel) {
    PortRiskLevel.CRITICAL -> status.fail
    PortRiskLevel.WARNING -> status.warn
    PortRiskLevel.INFO -> status.info
    PortRiskLevel.CLOSED -> status.muted
}

private val HTTP_PORTS = setOf(80, 443, 8080, 8443)
private val TLS_PORTS = setOf(443, 8443)

private fun portsForPreset(preset: Int): List<Int> = when (preset) {
    PRESET_COMMON -> WellKnownPorts.COMMON_PORTS.keys.sorted()
    PRESET_ALL -> (1..1024).toList()
    PRESET_CUSTOM -> WellKnownPorts.COMMON_PORTS.keys.sorted()
    else -> WellKnownPorts.COMMON_PORTS.keys.sorted()
}
