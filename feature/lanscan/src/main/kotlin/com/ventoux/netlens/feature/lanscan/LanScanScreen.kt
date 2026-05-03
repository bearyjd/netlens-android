package com.ventoux.netlens.feature.lanscan

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.outlined.DeviceUnknown
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
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
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.ventoux.netlens.core.network.export.ResultExporter
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ventoux.netlens.core.billing.LocalProStatus
import com.ventoux.netlens.core.data.model.KnownDeviceEntity
import com.ventoux.netlens.feature.lanscan.model.DeviceSortField
import com.ventoux.netlens.feature.lanscan.model.DiscoveryMethod
import com.ventoux.netlens.feature.lanscan.model.HostDetailState
import com.ventoux.netlens.feature.lanscan.model.LanDevice
import com.ventoux.netlens.feature.lanscan.model.LanScanTab
import com.ventoux.netlens.feature.lanscan.model.LanScanHistoryUiModel
import com.ventoux.netlens.feature.lanscan.model.LanScanUiState
import com.ventoux.netlens.feature.lanscan.model.ScanRangeMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LanScanScreen(
    onBack: () -> Unit = {},
    initialCidr: String? = null,
    onNavigateToTool: (String, String) -> Unit = { _, _ -> },
    viewModel: LanScanViewModel = hiltViewModel(),
) {
    LaunchedEffect(initialCidr) {
        if (initialCidr != null) viewModel.startScanWithCidr(initialCidr)
    }
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val sortOrder by viewModel.sortOrder.collectAsStateWithLifecycle()
    val hostDetail by viewModel.hostDetail.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val proStatus = LocalProStatus.current
    val isPro by proStatus.isPro.collectAsStateWithLifecycle()

    // Request notification permission on API 33+
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { /* no-op: best effort */ },
    )
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    LanScanContent(
        onBack = onBack,
        uiState = uiState,
        sortOrder = sortOrder,
        hostDetail = hostDetail,
        onStartScan = viewModel::startScan,
        onCancelScan = viewModel::cancelScan,
        onSortOrderChange = viewModel::setSortOrder,
        onRangeModeChanged = viewModel::onRangeModeChanged,
        onCustomRangeChanged = viewModel::onCustomRangeChanged,
        onDeviceClick = viewModel::selectDevice,
        onDismissDetail = viewModel::dismissDetail,
        onScanHostPorts = viewModel::scanHostPorts,
        onCancelHostScan = viewModel::cancelHostScan,
        onTabSelected = viewModel::selectTab,
        onScanWithCidr = viewModel::startScanWithCidr,
        onClearHistory = viewModel::clearHistory,
        onNavigateToTool = onNavigateToTool,
        onCopyResults = {
            ResultExporter.copyToClipboard(context, "LAN Scan", viewModel.buildExportText())
        },
        onShareResults = if (isPro) {
            { ResultExporter.shareAsText(context, "LAN Scan Results", viewModel.buildExportText()) }
        } else {
            null
        },
        onShareJson = if (isPro) {
            { ResultExporter.shareAsText(context, "Host Scan JSON", viewModel.buildHostScanJson()) }
        } else {
            null
        },
        onToggleKnown = viewModel::toggleKnown,
        onDeleteDevice = viewModel::deleteDevice,
        onClearInventory = viewModel::clearInventory,
        onInventorySearchChanged = viewModel::setInventorySearchQuery,
        onInventorySortFieldChanged = viewModel::setInventorySortField,
        onToggleInventorySortOrder = viewModel::toggleInventorySortOrder,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LanScanContent(
    onBack: () -> Unit,
    uiState: LanScanUiState,
    sortOrder: SortOrder,
    hostDetail: HostDetailState?,
    onStartScan: () -> Unit,
    onCancelScan: () -> Unit,
    onSortOrderChange: (SortOrder) -> Unit,
    onRangeModeChanged: (ScanRangeMode) -> Unit,
    onCustomRangeChanged: (String) -> Unit,
    onDeviceClick: (LanDevice) -> Unit,
    onDismissDetail: () -> Unit,
    onScanHostPorts: (List<Int>) -> Unit,
    onCancelHostScan: () -> Unit,
    onTabSelected: (LanScanTab) -> Unit,
    onScanWithCidr: (String) -> Unit,
    onClearHistory: () -> Unit,
    onNavigateToTool: (String, String) -> Unit,
    onCopyResults: () -> Unit = {},
    onShareResults: (() -> Unit)? = null,
    onShareJson: (() -> Unit)? = null,
    onToggleKnown: (String) -> Unit = {},
    onDeleteDevice: (String) -> Unit = {},
    onClearInventory: () -> Unit = {},
    onInventorySearchChanged: (String) -> Unit = {},
    onInventorySortFieldChanged: (DeviceSortField) -> Unit = {},
    onToggleInventorySortOrder: () -> Unit = {},
) {
    var sortMenuExpanded by remember { mutableStateOf(false) }
    val showCustomField = uiState.rangeMode == ScanRangeMode.CUSTOM

    if (hostDetail != null) {
        HostDetailSheet(
            state = hostDetail,
            onDismiss = onDismissDetail,
            onScanPorts = onScanHostPorts,
            onCancelScan = onCancelHostScan,
            onNavigateToTool = onNavigateToTool,
            onShareJson = onShareJson,
        )
    }

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
                    if (uiState.selectedTab == LanScanTab.SCAN && uiState.devices.isNotEmpty()) {
                        IconButton(onClick = onCopyResults) {
                            Icon(Icons.Default.ContentCopy, contentDescription = stringResource(R.string.lanscan_cd_copy_results))
                        }
                        if (onShareResults != null) IconButton(onClick = onShareResults) {
                            Icon(Icons.Default.Share, contentDescription = stringResource(R.string.lanscan_cd_share))
                        }
                    }
                    if (uiState.selectedTab == LanScanTab.SCAN) {
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
                    }
                },
            )
        },
        floatingActionButton = {
            if (uiState.selectedTab == LanScanTab.SCAN) {
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
            }
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            TabRow(
                selectedTabIndex = uiState.selectedTab.ordinal,
            ) {
                Tab(
                    selected = uiState.selectedTab == LanScanTab.SCAN,
                    onClick = { onTabSelected(LanScanTab.SCAN) },
                    text = { Text(stringResource(R.string.lanscan_tab_scan)) },
                )
                Tab(
                    selected = uiState.selectedTab == LanScanTab.HISTORY,
                    onClick = { onTabSelected(LanScanTab.HISTORY) },
                    text = { Text(stringResource(R.string.lanscan_tab_history)) },
                )
                Tab(
                    selected = uiState.selectedTab == LanScanTab.INVENTORY,
                    onClick = { onTabSelected(LanScanTab.INVENTORY) },
                    text = { Text(stringResource(R.string.lanscan_tab_inventory)) },
                )
            }

            when (uiState.selectedTab) {
                LanScanTab.SCAN -> ScanTabContent(
                    uiState = uiState,
                    showCustomField = showCustomField,
                    onRangeModeChanged = onRangeModeChanged,
                    onCustomRangeChanged = onCustomRangeChanged,
                    onScanWithCidr = onScanWithCidr,
                    onStartScan = onStartScan,
                    onDeviceClick = onDeviceClick,
                )
                LanScanTab.HISTORY -> HistoryTabContent(
                    entries = uiState.historyEntries,
                    onRescan = onScanWithCidr,
                    onClearHistory = onClearHistory,
                )
                LanScanTab.INVENTORY -> InventoryTabContent(
                    devices = uiState.knownDevices,
                    searchQuery = uiState.inventorySearchQuery,
                    sortField = uiState.inventorySortField,
                    sortAscending = uiState.inventorySortAscending,
                    onSearchChanged = onInventorySearchChanged,
                    onSortFieldChanged = onInventorySortFieldChanged,
                    onToggleSortOrder = onToggleInventorySortOrder,
                    onToggleKnown = onToggleKnown,
                    onDeleteDevice = onDeleteDevice,
                    onClearInventory = onClearInventory,
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ScanTabContent(
    uiState: LanScanUiState,
    showCustomField: Boolean,
    onRangeModeChanged: (ScanRangeMode) -> Unit,
    onCustomRangeChanged: (String) -> Unit,
    onScanWithCidr: (String) -> Unit,
    onStartScan: () -> Unit,
    onDeviceClick: (LanDevice) -> Unit,
) {
    if (uiState.suggestedNetworks.isNotEmpty() || !uiState.isScanning) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            Text(
                text = stringResource(R.string.lanscan_suggested_networks),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(4.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                uiState.suggestedNetworks.forEach { network ->
                    AssistChip(
                        onClick = { onScanWithCidr(network.cidr) },
                        label = { Text("${network.label} ${network.cidr}") },
                        enabled = !uiState.isScanning,
                    )
                }
                AssistChip(
                    onClick = { onRangeModeChanged(ScanRangeMode.CUSTOM) },
                    label = { Text(stringResource(R.string.lanscan_custom_chip)) },
                    enabled = !uiState.isScanning,
                )
            }
        }
    }

    AnimatedVisibility(visible = showCustomField) {
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
            DeviceCard(device = device, onClick = { onDeviceClick(device) })
        }
    }
}

@Composable
private fun HistoryTabContent(
    entries: List<LanScanHistoryUiModel>,
    onRescan: (String) -> Unit,
    onClearHistory: () -> Unit,
) {
    if (entries.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = stringResource(R.string.lanscan_history_empty),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    } else {
        LazyColumn(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            items(
                items = entries,
                key = { it.id },
            ) { entry ->
                val subnet = entry.subnet
                HistoryCard(
                    entry = entry,
                    onClick = if (subnet != null) {
                        { onRescan(subnet) }
                    } else null,
                )
            }
            item {
                TextButton(
                    onClick = onClearHistory,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.lanscan_history_clear))
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun InventoryTabContent(
    devices: List<KnownDeviceEntity>,
    searchQuery: String,
    sortField: DeviceSortField,
    sortAscending: Boolean,
    onSearchChanged: (String) -> Unit,
    onSortFieldChanged: (DeviceSortField) -> Unit,
    onToggleSortOrder: () -> Unit,
    onToggleKnown: (String) -> Unit,
    onDeleteDevice: (String) -> Unit,
    onClearInventory: () -> Unit,
) {
    var showClearDialog by remember { mutableStateOf(false) }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text(stringResource(R.string.lanscan_inventory_clear_title)) },
            text = { Text(stringResource(R.string.lanscan_inventory_clear_message)) },
            confirmButton = {
                TextButton(onClick = {
                    onClearInventory()
                    showClearDialog = false
                }) {
                    Text(stringResource(R.string.lanscan_inventory_clear_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) {
                    Text(stringResource(R.string.lanscan_inventory_clear_cancel))
                }
            },
        )
    }

    Column {
        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchChanged,
            placeholder = { Text(stringResource(R.string.lanscan_inventory_search_placeholder)) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { onSearchChanged("") }) {
                        Icon(Icons.Default.Close, contentDescription = stringResource(R.string.lanscan_inventory_clear_search))
                    }
                }
            },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.weight(1f),
            ) {
                DeviceSortField.entries.forEach { field ->
                    FilterChip(
                        selected = sortField == field,
                        onClick = { onSortFieldChanged(field) },
                        label = { Text(sortFieldLabel(field)) },
                    )
                }
            }
            IconButton(onClick = onToggleSortOrder) {
                Icon(
                    imageVector = if (sortAscending) Icons.Default.ArrowUpward else Icons.Default.ArrowDownward,
                    contentDescription = stringResource(R.string.lanscan_inventory_toggle_sort),
                )
            }
        }

        if (devices.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = if (searchQuery.isNotEmpty()) {
                        stringResource(R.string.lanscan_inventory_no_results)
                    } else {
                        stringResource(R.string.lanscan_inventory_empty)
                    },
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(
                    items = devices,
                    key = { it.macAddress },
                ) { device ->
                    InventoryDeviceCard(
                        device = device,
                        onToggleKnown = { onToggleKnown(device.macAddress) },
                        onDelete = { onDeleteDevice(device.macAddress) },
                    )
                }
                item {
                    TextButton(
                        onClick = { showClearDialog = true },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.lanscan_inventory_clear_all))
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun InventoryDeviceCard(
    device: KnownDeviceEntity,
    onToggleKnown: () -> Unit,
    onDelete: () -> Unit,
) {
    var showDeleteDialog by remember { mutableStateOf(false) }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text(stringResource(R.string.lanscan_inventory_delete_title)) },
            text = {
                Text(
                    stringResource(
                        R.string.lanscan_inventory_delete_message,
                        device.hostname ?: device.ip,
                    ),
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    onDelete()
                    showDeleteDialog = false
                }) {
                    Text(stringResource(R.string.lanscan_inventory_delete_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text(stringResource(R.string.lanscan_inventory_clear_cancel))
                }
            },
        )
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onToggleKnown,
                onLongClick = { showDeleteDialog = true },
            ),
        colors = CardDefaults.cardColors(
            containerColor = if (device.isKnown) {
                MaterialTheme.colorScheme.surfaceVariant
            } else {
                MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
            },
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = if (device.isKnown) Icons.Default.Verified else Icons.Outlined.DeviceUnknown,
                contentDescription = if (device.isKnown) {
                    stringResource(R.string.lanscan_inventory_known)
                } else {
                    stringResource(R.string.lanscan_inventory_unknown)
                },
                tint = if (device.isKnown) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.error
                },
                modifier = Modifier.size(24.dp),
            )

            Spacer(modifier = Modifier.width(10.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = device.hostname ?: device.ip,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )

                device.vendor?.let { vendor ->
                    Text(
                        text = vendor,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                Text(
                    text = device.macAddress,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    maxLines = 1,
                )

                if (device.hostname != null) {
                    Text(
                        text = device.ip,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        maxLines = 1,
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = stringResource(R.string.lanscan_inventory_first_seen, relativeTimeText(device.firstSeen)),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = stringResource(R.string.lanscan_inventory_last_seen, relativeTimeText(device.lastSeen)),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(modifier = Modifier.width(4.dp))

            IconButton(onClick = { showDeleteDialog = true }) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = stringResource(R.string.lanscan_inventory_delete),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

@Composable
private fun sortFieldLabel(field: DeviceSortField): String = when (field) {
    DeviceSortField.HOSTNAME -> stringResource(R.string.lanscan_sort_hostname)
    DeviceSortField.IP -> stringResource(R.string.lanscan_sort_by_ip)
    DeviceSortField.VENDOR -> stringResource(R.string.lanscan_sort_vendor)
    DeviceSortField.FIRST_SEEN -> stringResource(R.string.lanscan_sort_first_seen)
    DeviceSortField.LAST_SEEN -> stringResource(R.string.lanscan_sort_last_seen)
    DeviceSortField.MAC -> stringResource(R.string.lanscan_sort_mac)
}

@Composable
private fun HistoryCard(entry: LanScanHistoryUiModel, onClick: (() -> Unit)?) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = entry.subnet ?: "Unknown",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(modifier = Modifier.height(2.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = stringResource(R.string.lanscan_history_devices, entry.deviceCount),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = relativeTimeText(entry.timestamp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
private fun relativeTimeText(timestampMs: Long): String {
    val now = remember(timestampMs) { System.currentTimeMillis() }
    val diff = now - timestampMs
    return when {
        diff < 60_000 -> stringResource(R.string.lanscan_time_just_now)
        diff < 3_600_000 -> stringResource(R.string.lanscan_time_minutes_ago, (diff / 60_000).toInt())
        diff < 86_400_000 -> stringResource(R.string.lanscan_time_hours_ago, (diff / 3_600_000).toInt())
        else -> stringResource(R.string.lanscan_time_days_ago, (diff / 86_400_000).toInt())
    }
}

@Composable
private fun DeviceCard(device: LanDevice, onClick: () -> Unit) {
    val discoveryText = when (device.discoveryMethod) {
        DiscoveryMethod.PING -> stringResource(R.string.lanscan_discovery_ping)
        DiscoveryMethod.MDNS -> stringResource(R.string.lanscan_discovery_mdns)
        DiscoveryMethod.SSDP -> stringResource(R.string.lanscan_discovery_ssdp)
        DiscoveryMethod.NETBIOS -> stringResource(R.string.lanscan_discovery_netbios)
        DiscoveryMethod.MULTIPLE -> stringResource(R.string.lanscan_discovery_both)
    }
    val secondaryLine = buildString {
        device.osGuess?.let { append(it) }
        if (isNotEmpty()) append(" · ")
        append(discoveryText)
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
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
                modifier = Modifier.size(20.dp),
            )

            Spacer(modifier = Modifier.width(10.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = device.ip,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )

                device.hostname?.let { hostname ->
                    Text(
                        text = hostname,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                device.macAddress?.let { mac ->
                    Text(
                        text = stringResource(R.string.lanscan_mac_label, mac),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        maxLines = 1,
                    )
                }

                Text(
                    text = secondaryLine,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.tertiary,
                    maxLines = 1,
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            Column(horizontalAlignment = Alignment.End) {
                device.deviceType?.let { type ->
                    Text(
                        text = type,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                    )
                }

                device.services.take(3).forEach { service ->
                    val displayName = service
                        .trim('.')
                        .removePrefix("_")
                        .removeSuffix("._tcp")
                        .removeSuffix("._udp")
                    Text(
                        text = displayName,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }

                if (device.services.size > 3) {
                    Text(
                        text = "+${device.services.size - 3}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    )
                }
            }

            Spacer(modifier = Modifier.width(4.dp))

            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun DeviceCardPreview() {
    MaterialTheme {
        Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            DeviceCard(
                device = LanDevice(
                    ip = "192.168.1.42",
                    hostname = "onn.-Streaming-Device-10e164d58de7c09256650a369c4f9acf",
                    isReachable = true,
                    deviceType = "Chromecast",
                    osGuess = "Android",
                    discoveryMethod = DiscoveryMethod.MULTIPLE,
                    services = listOf(
                        "_googlecast._tcp.",
                        "_spotify-connect._tcp.",
                        "_airplay._tcp.",
                        "_raop._tcp.",
                    ),
                ),
                onClick = {},
            )
            DeviceCard(
                device = LanDevice(
                    ip = "192.168.1.1",
                    isReachable = true,
                    discoveryMethod = DiscoveryMethod.PING,
                ),
                onClick = {},
            )
        }
    }
}
