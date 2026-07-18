package com.ventouxlabs.netlens.feature.devices

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ventouxlabs.netlens.core.billing.LocalProStatus
import com.ventouxlabs.netlens.core.data.model.KnownDeviceEntity
import com.ventouxlabs.netlens.core.data.model.WatchedNetworkEntity
import com.ventouxlabs.netlens.core.network.export.ResultExporter
import com.ventouxlabs.netlens.feature.devices.model.WatchCadence
import com.ventouxlabs.netlens.feature.devices.model.displayName

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DevicesScreen(
    onBack: () -> Unit = {},
    onNavigateToTool: (String, String) -> Unit = { _, _ -> },
    viewModel: DevicesViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val selected = uiState.devices.find { it.id == uiState.selectedDeviceId }
    val proStatus = LocalProStatus.current
    val isPro by proStatus.isPro.collectAsStateWithLifecycle()

    if (selected != null) {
        DeviceDetailSheet(
            device = selected,
            onDismiss = { viewModel.selectDevice(null) },
            onRename = { viewModel.rename(selected.id, it) },
            onToggleKnown = { viewModel.toggleKnown(selected.id) },
            onDelete = { viewModel.delete(selected.id) },
        )
    }

    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.devices_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.navigate_back))
                    }
                },
                actions = {
                    IconButton(onClick = {
                        ResultExporter.copyToClipboard(context, "Devices", viewModel.buildExportText())
                    }) {
                        Icon(Icons.Default.ContentCopy, stringResource(R.string.devices_cd_copy_results))
                    }
                    if (isPro) {
                        IconButton(onClick = {
                            ResultExporter.shareAsText(context, "Device Inventory", viewModel.buildExportText())
                        }) {
                            Icon(Icons.Default.Share, stringResource(R.string.devices_cd_share))
                        }
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            WatchSection(
                isPro = isPro,
                watchedNetworks = uiState.watchedNetworks,
                cadence = uiState.cadence,
                masterEnabled = uiState.masterWatchEnabled,
                onWatchThisNetwork = viewModel::watchCurrentNetwork,
                onToggleNetwork = viewModel::toggleNetworkWatch,
                onRemoveNetwork = viewModel::removeWatchedNetwork,
                onMasterToggle = { enabled -> viewModel.setMasterWatch(enabled, isPro) },
                onCadenceChange = { cadence -> viewModel.setCadence(cadence, isPro) },
            )
            OutlinedTextField(
                value = uiState.searchQuery,
                onValueChange = viewModel::setSearchQuery,
                placeholder = { Text(stringResource(R.string.devices_search_placeholder)) },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                trailingIcon = {
                    if (uiState.searchQuery.isNotEmpty()) {
                        IconButton(onClick = { viewModel.setSearchQuery("") }) {
                            Icon(Icons.Default.Close, stringResource(R.string.devices_clear_search))
                        }
                    }
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(16.dp),
            )

            val newDevices = uiState.devices.filter { !it.isKnown }
            val knownDevices = uiState.devices.filter { it.isKnown }

            if (uiState.devices.isEmpty()) {
                Text(
                    if (uiState.searchQuery.isBlank()) stringResource(R.string.devices_empty)
                    else stringResource(R.string.devices_no_results),
                    modifier = Modifier.fillMaxWidth().padding(32.dp),
                    style = MaterialTheme.typography.bodyMedium,
                )
            } else {
                LazyColumn(Modifier.fillMaxSize()) {
                    if (newDevices.isNotEmpty()) {
                        item { SectionHeader(stringResource(R.string.devices_section_new)) }
                        items(newDevices, key = { it.id }) { DeviceRow(it) { viewModel.selectDevice(it.id) } }
                    }
                    if (knownDevices.isNotEmpty()) {
                        item { SectionHeader(stringResource(R.string.devices_section_known)) }
                        items(knownDevices, key = { it.id }) { DeviceRow(it) { viewModel.selectDevice(it.id) } }
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
    )
    HorizontalDivider()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DeviceRow(device: KnownDeviceEntity, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(device.displayName()) },
        supportingContent = {
            Text(
                "${device.ip}  ·  ${device.macAddress ?: stringResource(R.string.devices_mac_unknown)}",
                style = MaterialTheme.typography.labelSmall,
            )
        },
        modifier = Modifier.clickable(onClick = onClick).fillMaxWidth().padding(horizontal = 4.dp),
    )
    HorizontalDivider()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WatchSection(
    isPro: Boolean,
    watchedNetworks: List<WatchedNetworkEntity>,
    cadence: WatchCadence,
    masterEnabled: Boolean,
    onWatchThisNetwork: () -> Unit,
    onToggleNetwork: (Long, Boolean) -> Unit,
    onRemoveNetwork: (Long) -> Unit,
    onMasterToggle: (Boolean) -> Unit,
    onCadenceChange: (WatchCadence) -> Unit,
) {
    Column(
        Modifier.fillMaxWidth().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(stringResource(R.string.devices_watch_section), style = MaterialTheme.typography.titleMedium)
        if (!isPro) {
            Text(stringResource(R.string.devices_watch_pro_upsell), style = MaterialTheme.typography.bodyMedium)
            return@Column
        }
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(stringResource(R.string.devices_watch_master))
            Switch(checked = masterEnabled, onCheckedChange = onMasterToggle)
        }
        Text(stringResource(R.string.devices_watch_cadence), style = MaterialTheme.typography.labelMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            WatchCadence.entries.forEach { option ->
                FilterChip(
                    selected = option == cadence,
                    onClick = { onCadenceChange(option) },
                    label = { Text(cadenceLabel(option)) },
                )
            }
        }
        OutlinedButton(onClick = onWatchThisNetwork, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.devices_watch_this_network))
        }
        watchedNetworks.forEach { network ->
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(network.displayName ?: network.gatewayMac, style = MaterialTheme.typography.labelSmall)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(
                        checked = network.watchEnabled,
                        onCheckedChange = { onToggleNetwork(network.id, it) },
                    )
                    TextButton(onClick = { onRemoveNetwork(network.id) }) {
                        Text(stringResource(R.string.devices_watch_remove))
                    }
                }
            }
        }
    }
    HorizontalDivider()
}

@Composable
private fun cadenceLabel(cadence: WatchCadence): String =
    when (cadence) {
        WatchCadence.FIFTEEN_MIN -> stringResource(R.string.devices_watch_cadence_15)
        WatchCadence.THIRTY_MIN -> stringResource(R.string.devices_watch_cadence_30)
        WatchCadence.ONE_HOUR -> stringResource(R.string.devices_watch_cadence_60)
        WatchCadence.SIX_HOURS -> stringResource(R.string.devices_watch_cadence_360)
    }
