package com.ventoux.netlens.feature.lanscan

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ventoux.netlens.feature.lanscan.model.DiscoveryMethod
import com.ventoux.netlens.feature.lanscan.model.LanDevice
import com.ventoux.netlens.feature.lanscan.model.LanScanUiState
import com.ventoux.netlens.feature.lanscan.model.ScanRangeMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LanScanScreen(
    onBack: () -> Unit = {},
    viewModel: LanScanViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val sortOrder by viewModel.sortOrder.collectAsStateWithLifecycle()

    LanScanContent(
        onBack = onBack,
        uiState = uiState,
        sortOrder = sortOrder,
        onStartScan = viewModel::startScan,
        onCancelScan = viewModel::cancelScan,
        onSortOrderChange = viewModel::setSortOrder,
        onRangeModeChanged = viewModel::onRangeModeChanged,
        onCustomRangeChanged = viewModel::onCustomRangeChanged,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LanScanContent(
    onBack: () -> Unit,
    uiState: LanScanUiState,
    sortOrder: SortOrder,
    onStartScan: () -> Unit,
    onCancelScan: () -> Unit,
    onSortOrderChange: (SortOrder) -> Unit,
    onRangeModeChanged: (ScanRangeMode) -> Unit,
    onCustomRangeChanged: (String) -> Unit,
) {
    var sortMenuExpanded by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.lanscan_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.navigate_back),
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { sortMenuExpanded = true }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Sort,
                            contentDescription = stringResource(R.string.lanscan_cd_sort),
                        )
                    }
                    DropdownMenu(
                        expanded = sortMenuExpanded,
                        onDismissRequest = { sortMenuExpanded = false },
                    ) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.lanscan_sort_by_ip)) },
                            onClick = {
                                onSortOrderChange(SortOrder.IP)
                                sortMenuExpanded = false
                            },
                            trailingIcon = if (sortOrder == SortOrder.IP) {
                                { Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(16.dp)) }
                            } else null,
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.lanscan_sort_by_latency)) },
                            onClick = {
                                onSortOrderChange(SortOrder.LATENCY)
                                sortMenuExpanded = false
                            },
                            trailingIcon = if (sortOrder == SortOrder.LATENCY) {
                                { Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(16.dp)) }
                            } else null,
                        )
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    if (uiState.isScanning) {
                        onCancelScan()
                    } else if (uiState.rangeMode != ScanRangeMode.CUSTOM || uiState.customRange.isNotBlank()) {
                        onStartScan()
                    }
                },
            ) {
                Icon(
                    imageVector = if (uiState.isScanning) {
                        Icons.Default.Close
                    } else {
                        Icons.Default.Search
                    },
                    contentDescription = if (uiState.isScanning) stringResource(R.string.lanscan_cd_stop_scan) else stringResource(R.string.lanscan_cd_start_scan),
                )
            }
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(
                    selected = uiState.rangeMode == ScanRangeMode.AUTO,
                    onClick = { onRangeModeChanged(ScanRangeMode.AUTO) },
                    label = { Text(stringResource(R.string.lanscan_mode_auto)) },
                    enabled = !uiState.isScanning,
                )
                FilterChip(
                    selected = uiState.rangeMode == ScanRangeMode.CUSTOM,
                    onClick = { onRangeModeChanged(ScanRangeMode.CUSTOM) },
                    label = { Text(stringResource(R.string.lanscan_mode_custom)) },
                    enabled = !uiState.isScanning,
                )
            }

            AnimatedVisibility(visible = uiState.rangeMode == ScanRangeMode.CUSTOM) {
                OutlinedTextField(
                    value = uiState.customRange,
                    onValueChange = onCustomRangeChanged,
                    label = { Text(stringResource(R.string.lanscan_label_custom_range)) },
                    placeholder = { Text(stringResource(R.string.lanscan_placeholder_cidr)) },
                    isError = uiState.rangeError != null,
                    supportingText = uiState.rangeError?.let { error ->
                        { Text(error) }
                    },
                    singleLine = true,
                    enabled = !uiState.isScanning,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Uri,
                        imeAction = ImeAction.Done,
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                )
            }

            AnimatedVisibility(visible = uiState.isScanning) {
                LinearProgressIndicator(
                    progress = { uiState.progress },
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            if (uiState.subnetInfo.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.lanscan_subnet_label, uiState.subnetInfo),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    SuggestionChip(
                        onClick = {},
                        label = {
                            Text(
                                text = if (uiState.isScanning) {
                                    stringResource(R.string.lanscan_found_devices_scanning, uiState.deviceCount)
                                } else {
                                    stringResource(R.string.lanscan_device_count, uiState.devices.size)
                                },
                            )
                        },
                    )
                }
            }

            uiState.error?.let { error ->
                Text(
                    text = error,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }

            LazyColumn(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(
                    items = uiState.devices,
                    key = { it.ip },
                ) { device ->
                    DeviceCard(device = device)
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DeviceCard(device: LanDevice) {
    val clipboardManager = LocalClipboardManager.current

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = if (device.isReachable) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = device.ip,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )

                    device.hostname?.let { hostname ->
                        Text(
                            text = hostname,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    Text(
                        text = stringResource(R.string.lanscan_mac_unavailable),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    )

                    device.osGuess?.let { os ->
                        Text(
                            text = os,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.tertiary,
                        )
                    }
                }

                device.deviceType?.let { type ->
                    Spacer(modifier = Modifier.width(8.dp))
                    SuggestionChip(
                        onClick = {},
                        label = {
                            Text(
                                text = type,
                                style = MaterialTheme.typography.labelSmall,
                                maxLines = 1,
                            )
                        },
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                SuggestionChip(
                    onClick = {},
                    label = {
                        Text(
                            text = when (device.discoveryMethod) {
                                DiscoveryMethod.PING -> stringResource(R.string.lanscan_discovery_ping)
                                DiscoveryMethod.MDNS -> stringResource(R.string.lanscan_discovery_mdns)
                                DiscoveryMethod.BOTH -> stringResource(R.string.lanscan_discovery_both)
                            },
                            style = MaterialTheme.typography.labelSmall,
                        )
                    },
                )

                Spacer(modifier = Modifier.width(4.dp))

                IconButton(
                    onClick = { clipboardManager.setText(AnnotatedString(device.ip)) },
                    modifier = Modifier.size(36.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = stringResource(R.string.lanscan_cd_copy_ip),
                        modifier = Modifier.size(16.dp),
                    )
                }
            }

            if (device.services.isNotEmpty()) {
                FlowRow(
                    modifier = Modifier.padding(start = 52.dp, end = 16.dp, bottom = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    device.services.forEach { service ->
                        val displayName = service
                            .trim('.')
                            .removePrefix("_")
                            .removeSuffix("._tcp")
                            .removeSuffix("._udp")
                        SuggestionChip(
                            onClick = {},
                            label = {
                                Text(
                                    text = displayName,
                                    style = MaterialTheme.typography.labelSmall,
                                )
                            },
                        )
                    }
                }
            }
        }
    }
}
