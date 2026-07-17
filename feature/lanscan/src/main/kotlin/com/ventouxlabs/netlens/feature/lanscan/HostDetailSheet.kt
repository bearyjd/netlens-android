package com.ventouxlabs.netlens.feature.lanscan

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ventouxlabs.netlens.core.ui.LocalStatusColors
import com.ventouxlabs.netlens.core.scan.model.DiscoveryMethod
import com.ventouxlabs.netlens.feature.lanscan.model.HostDetailState
import com.ventouxlabs.netlens.feature.lanscan.model.HostPortResult
import com.ventouxlabs.netlens.feature.portscan.model.PortRiskLevel

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun HostDetailSheet(
    state: HostDetailState,
    onDismiss: () -> Unit,
    onScanPorts: (List<Int>) -> Unit,
    onCancelScan: () -> Unit,
    onNavigateToTool: (String, String) -> Unit,
    onShareJson: (() -> Unit)?,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(bottom = 32.dp),
        ) {
            // Host header
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = state.device.ip,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                        )
                        state.device.hostname?.let {
                            Text(
                                text = it,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.padding(top = 4.dp),
                        ) {
                            Text(
                                text = stringResource(R.string.lanscan_latency_ms, state.device.latencyMs),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                text = when (state.device.discoveryMethod) {
                                    DiscoveryMethod.PING -> stringResource(R.string.lanscan_discovery_ping)
                                    DiscoveryMethod.MDNS -> stringResource(R.string.lanscan_discovery_mdns)
                                    DiscoveryMethod.SSDP -> stringResource(R.string.lanscan_discovery_ssdp)
                                    DiscoveryMethod.NETBIOS -> stringResource(R.string.lanscan_discovery_netbios)
                                    DiscoveryMethod.MULTIPLE -> stringResource(R.string.lanscan_discovery_both)
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        state.device.macAddress?.let { mac ->
                            Text(
                                text = mac,
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = MaterialTheme.typography.labelSmall.fontFamily,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 2.dp),
                            )
                        }
                        val typeLabel = state.enrichedType ?: state.device.deviceType
                        val osLabel = state.enrichedOs ?: state.device.osGuess
                        val vendorLabel = state.device.vendor
                        if (typeLabel != null || osLabel != null || vendorLabel != null) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                modifier = Modifier.padding(top = 4.dp),
                            ) {
                                typeLabel?.let {
                                    SuggestionChip(onClick = {}, label = { Text(it, style = MaterialTheme.typography.labelSmall) })
                                }
                                osLabel?.let {
                                    SuggestionChip(onClick = {}, label = { Text(it, style = MaterialTheme.typography.labelSmall) })
                                }
                                vendorLabel?.let {
                                    SuggestionChip(onClick = {}, label = { Text(it, style = MaterialTheme.typography.labelSmall) })
                                }
                            }
                        }
                    }
                    if (onShareJson != null && state.portResults.isNotEmpty() && !state.isScanning) {
                        IconButton(onClick = onShareJson) {
                            Icon(
                                imageVector = Icons.Default.Share,
                                contentDescription = stringResource(R.string.lanscan_cd_share),
                            )
                        }
                    }
                }
            }

            // Quick actions
            item {
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                Text(
                    text = stringResource(R.string.lanscan_actions_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(top = 4.dp),
                ) {
                    AssistChip(
                        onClick = { onNavigateToTool("ping", state.device.ip) },
                        label = { Text(stringResource(R.string.lanscan_action_ping)) },
                    )
                    AssistChip(
                        onClick = { onNavigateToTool("portscan", state.device.ip) },
                        label = { Text(stringResource(R.string.lanscan_action_port_scan)) },
                    )
                    AssistChip(
                        onClick = { onNavigateToTool("traceroute", state.device.ip) },
                        label = { Text(stringResource(R.string.lanscan_action_traceroute)) },
                    )
                    AssistChip(
                        onClick = { onNavigateToTool("whois", state.device.ip) },
                        label = { Text(stringResource(R.string.lanscan_action_whois)) },
                    )
                }
            }

            // Port scan section header + progress
            item {
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.lanscan_port_scan_section),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    if (state.isScanning) {
                        OutlinedButton(
                            onClick = onCancelScan,
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = MaterialTheme.colorScheme.error,
                            ),
                        ) {
                            Text(stringResource(R.string.lanscan_cancel_port_scan))
                        }
                    } else if (state.portResults.isNotEmpty()) {
                        OutlinedButton(onClick = { onScanPorts(com.ventouxlabs.netlens.feature.portscan.model.WellKnownPorts.TOP_1000_PORTS) }) {
                            Text(stringResource(R.string.lanscan_rescan))
                        }
                    }
                }
            }

            if (state.isScanning || state.portResults.isNotEmpty()) {
                item {
                    LinearProgressIndicator(
                        progress = { state.progress },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        // Risk summary counts
                        val criticalCount = state.enrichedResults.count { it.riskLevel == PortRiskLevel.CRITICAL }
                        val openCount = state.enrichedResults.count { it.riskLevel == PortRiskLevel.WARNING }
                        val closedCount = state.enrichedResults.count { !it.isOpen }
                        if (criticalCount > 0) {
                            Text(
                                text = stringResource(R.string.lanscan_risk_critical_count, criticalCount),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                        if (openCount > 0) {
                            Text(
                                text = stringResource(R.string.lanscan_risk_open_count, openCount),
                                style = MaterialTheme.typography.bodySmall,
                                color = LocalStatusColors.current.warn,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                        Text(
                            text = stringResource(R.string.lanscan_scanned_count, state.portResults.size),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            state.error?.let { error ->
                item {
                    Text(
                        text = error,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }

            // Grouped port results by risk level
            if (state.enrichedResults.isNotEmpty()) {
                val grouped = state.enrichedResults
                    .groupBy { it.riskLevel }
                    .entries
                    .sortedBy { it.key.sortOrder }

                for ((riskLevel, results) in grouped) {
                    item(key = "header_${riskLevel.name}") {
                        RiskGroupHeader(
                            riskLevel = riskLevel,
                            count = results.size,
                        )
                    }
                    items(results, key = { "${riskLevel.name}_${it.port}" }) { result ->
                        PortResultRow(result = result)
                    }
                }
            } else if (!state.isScanning && state.portResults.isEmpty() && state.error == null) {
                item {
                    Text(
                        text = stringResource(R.string.lanscan_port_scan_starting),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 8.dp),
                    )
                }
            }

            // Fingerprint section
            if (state.fingerprintEvidence.isNotEmpty()) {
                item {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    Text(
                        text = stringResource(R.string.lanscan_fingerprint_section),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    state.enrichedType?.let {
                        Text(
                            text = stringResource(R.string.lanscan_profile_label, it),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                    state.enrichedOs?.let {
                        Text(
                            text = stringResource(R.string.lanscan_os_label, it),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    Text(
                        text = stringResource(R.string.lanscan_evidence_label),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                    state.fingerprintEvidence.forEach { ev ->
                        Text(
                            text = "• $ev",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RiskGroupHeader(
    riskLevel: PortRiskLevel,
    count: Int,
) {
    val color = riskLevel.toColor()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp, bottom = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = riskLevel.label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = color,
        )
        Surface(
            color = color.copy(alpha = 0.12f),
            shape = MaterialTheme.shapes.small,
        ) {
            Text(
                text = count.toString(),
                style = MaterialTheme.typography.labelSmall,
                color = color,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            )
        }
    }
}

@Composable
private fun PortResultRow(result: HostPortResult) {
    val riskColor = result.riskLevel.toColor()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = if (result.isOpen) Icons.Default.CheckCircle else Icons.Default.Close,
                contentDescription = null,
                tint = riskColor,
                modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = result.port.toString(),
                style = MaterialTheme.typography.bodyMedium,
                fontFamily = MaterialTheme.typography.labelSmall.fontFamily,
                modifier = Modifier.width(52.dp),
            )
            Text(
                text = result.serviceName,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            Surface(
                color = riskColor.copy(alpha = 0.12f),
                shape = MaterialTheme.shapes.small,
            ) {
                Text(
                    text = result.riskLevel.label,
                    style = MaterialTheme.typography.labelSmall,
                    color = riskColor,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }
            if (result.isOpen && result.latencyMs > 0) {
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "${result.latencyMs}ms",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (result.isOpen) {
            Text(
                text = result.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 24.dp, top = 1.dp),
            )
        }
    }
}

@Composable
private fun PortRiskLevel.toColor(): Color = when (this) {
    PortRiskLevel.CRITICAL -> MaterialTheme.colorScheme.error
    PortRiskLevel.WARNING -> LocalStatusColors.current.warn
    PortRiskLevel.INFO -> MaterialTheme.colorScheme.tertiary
    PortRiskLevel.CLOSED -> MaterialTheme.colorScheme.outline
}
