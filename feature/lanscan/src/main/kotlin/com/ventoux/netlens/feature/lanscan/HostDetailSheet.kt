package com.ventoux.netlens.feature.lanscan

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ventoux.netlens.feature.lanscan.model.DiscoveryMethod
import com.ventoux.netlens.feature.lanscan.model.HostDetailState
import com.ventoux.netlens.feature.portscan.model.PortResult
import com.ventoux.netlens.feature.portscan.model.WellKnownPorts

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun HostDetailSheet(
    state: HostDetailState,
    onDismiss: () -> Unit,
    onScanPorts: (List<Int>) -> Unit,
    onCancelScan: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var selectedPreset by remember { mutableIntStateOf(0) }

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
            item {
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
                            DiscoveryMethod.BOTH -> stringResource(R.string.lanscan_discovery_both)
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                state.device.macAddress?.let { mac ->
                    Text(
                        text = mac,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
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

            item {
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                Text(
                    text = stringResource(R.string.lanscan_port_scan_section),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(top = 4.dp),
                ) {
                    FilterChip(
                        selected = selectedPreset == 0,
                        onClick = { selectedPreset = 0 },
                        label = { Text(stringResource(R.string.lanscan_chip_common)) },
                        enabled = !state.isScanning,
                    )
                    FilterChip(
                        selected = selectedPreset == 1,
                        onClick = { selectedPreset = 1 },
                        label = { Text(stringResource(R.string.lanscan_chip_all)) },
                        enabled = !state.isScanning,
                    )
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(top = 4.dp),
                ) {
                    if (state.isScanning) {
                        OutlinedButton(
                            onClick = onCancelScan,
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = MaterialTheme.colorScheme.error,
                            ),
                        ) {
                            Text(stringResource(R.string.lanscan_cancel_port_scan))
                        }
                    } else {
                        Button(onClick = {
                            val ports = if (selectedPreset == 0) {
                                WellKnownPorts.COMMON_PORTS.keys.sorted()
                            } else {
                                (1..1024).toList()
                            }
                            onScanPorts(ports)
                        }) {
                            Text(stringResource(R.string.lanscan_scan_ports))
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
                        Text(
                            text = stringResource(R.string.lanscan_open_count, state.openCount),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
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

            val sorted = state.portResults.sortedWith(
                compareByDescending<PortResult> { it.isOpen }
                    .thenBy { it.port },
            )
            items(sorted, key = { it.port }) { result ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = if (result.isOpen) Icons.Default.CheckCircle else Icons.Default.Close,
                        contentDescription = null,
                        tint = if (result.isOpen) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = result.port.toString(),
                        style = MaterialTheme.typography.bodyMedium,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.width(52.dp),
                    )
                    Text(
                        text = result.serviceName,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                    )
                    if (result.isOpen && result.latencyMs > 0) {
                        Text(
                            text = "${result.latencyMs}ms",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

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
