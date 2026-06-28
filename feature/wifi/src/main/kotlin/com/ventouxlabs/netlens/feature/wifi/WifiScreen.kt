package com.ventouxlabs.netlens.feature.wifi

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.NetworkWifi
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SignalWifi4Bar
import androidx.compose.material.icons.filled.SignalWifiOff
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ventouxlabs.netlens.core.billing.LocalProStatus
import com.ventouxlabs.netlens.core.network.export.ResultExporter
import com.ventouxlabs.netlens.feature.wifi.model.ConnectedWifiInfo
import com.ventouxlabs.netlens.feature.wifi.model.WifiBand
import com.ventouxlabs.netlens.feature.wifi.model.WifiNetwork
import com.ventouxlabs.netlens.feature.wifi.model.WifiUiState
import com.ventouxlabs.netlens.feature.wifi.ui.ChannelGraph

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WifiScreen(
    onBack: () -> Unit = {},
    viewModel: WifiViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val proStatus = LocalProStatus.current
    val isPro by proStatus.isPro.collectAsStateWithLifecycle()

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { permissions ->
        val granted = permissions.values.all { it }
        viewModel.onPermissionResult(granted)
    }

    LaunchedEffect(Unit) {
        val fineLocation = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED
        if (fineLocation) {
            viewModel.onPermissionResult(true)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.wifi_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.wifi_navigate_back),
                        )
                    }
                },
                actions = {
                    if (state.networks.isNotEmpty()) {
                        IconButton(onClick = {
                            ResultExporter.copyToClipboard(
                                context, "WiFi Analyzer", viewModel.buildExportText(),
                            )
                        }) {
                            Icon(
                                Icons.Default.ContentCopy,
                                contentDescription = stringResource(R.string.wifi_cd_copy_results),
                            )
                        }
                        if (isPro) {
                            IconButton(onClick = {
                                ResultExporter.shareAsText(
                                    context, "WiFi Analyzer Results", viewModel.buildExportText(),
                                )
                            }) {
                                Icon(
                                    Icons.Default.Share,
                                    contentDescription = stringResource(R.string.wifi_cd_share),
                                )
                            }
                        }
                    }
                },
            )
        },
        floatingActionButton = {
            if (state.permissionGranted) {
                FloatingActionButton(
                    onClick = { viewModel.startScan() },
                ) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = stringResource(R.string.wifi_scan_button),
                    )
                }
            }
        },
    ) { innerPadding ->
        if (!state.permissionGranted) {
            PermissionRationale(
                onRequestPermission = {
                    permissionLauncher.launch(
                        arrayOf(
                            Manifest.permission.ACCESS_FINE_LOCATION,
                        ),
                    )
                },
                modifier = Modifier.padding(innerPadding),
            )
        } else {
            WifiContent(
                state = state,
                filteredNetworks = viewModel.filteredNetworks(),
                onBandSelected = viewModel::onBandSelected,
                isPro = isPro,
                modifier = Modifier.padding(innerPadding),
            )
        }
    }
}

@Composable
private fun PermissionRationale(
    onRequestPermission: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp),
        ) {
            Icon(
                imageVector = Icons.Default.NetworkWifi,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.height(48.dp).width(48.dp),
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.wifi_permission_title),
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.wifi_permission_rationale),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(24.dp))
            Button(
                onClick = onRequestPermission,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.wifi_permission_grant))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WifiContent(
    state: WifiUiState,
    filteredNetworks: List<WifiNetwork>,
    onBandSelected: (WifiBand) -> Unit,
    isPro: Boolean = true,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize(),
    ) {
        if (state.isScanning) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }

        state.error?.let { error ->
            Text(
                text = error,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }

        state.connectedInfo?.let { info ->
            ConnectedInfoCard(
                info = info,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }

        if (state.networks.isNotEmpty()) {
            BandSelector(
                selectedBand = state.selectedBand,
                onBandSelected = onBandSelected,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            )

            if (isPro) {
                ChannelGraph(
                    networks = filteredNetworks,
                    connectedBssid = state.connectedInfo?.bssid,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.wifi_networks_title),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                SuggestionChip(
                    onClick = {},
                    label = { Text("${filteredNetworks.size}") },
                )
                state.lastScanTimestamp?.let { ts ->
                    val elapsed = formatElapsed(System.currentTimeMillis() - ts)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.wifi_last_scanned, elapsed),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        if (filteredNetworks.isEmpty() && !state.isScanning) {
            EmptyState()
        } else {
            LazyColumn(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(
                    items = filteredNetworks,
                    key = { it.bssid },
                ) { network ->
                    NetworkCard(
                        network = network,
                        isConnected = network.bssid == state.connectedInfo?.bssid,
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyState() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = stringResource(R.string.wifi_no_networks),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.wifi_no_networks_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ConnectedInfoCard(
    info: ConnectedWifiInfo,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.SignalWifi4Bar,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = info.ssid,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                StatItem(
                    label = stringResource(R.string.wifi_signal_label),
                    value = "${info.rssi} dBm",
                )
                StatItem(
                    label = stringResource(R.string.wifi_speed_label),
                    value = "${info.linkSpeedMbps} Mbps",
                )
                StatItem(
                    label = stringResource(R.string.wifi_frequency_label),
                    value = "${info.frequency} MHz",
                )
            }
            info.ipAddress?.let { ip ->
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "${stringResource(R.string.wifi_ip_label)}: $ip",
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = MaterialTheme.typography.labelSmall.fontFamily,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
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
            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BandSelector(
    selectedBand: WifiBand,
    onBandSelected: (WifiBand) -> Unit,
    modifier: Modifier = Modifier,
) {
    SingleChoiceSegmentedButtonRow(modifier = modifier) {
        WifiBand.entries.forEachIndexed { index, band ->
            SegmentedButton(
                selected = selectedBand == band,
                onClick = { onBandSelected(band) },
                shape = SegmentedButtonDefaults.itemShape(index, WifiBand.entries.size),
            ) {
                Text(band.label)
            }
        }
    }
}

@Composable
private fun NetworkCard(
    network: WifiNetwork,
    isConnected: Boolean,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isConnected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
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
                imageVector = if (network.level > -70) {
                    Icons.Default.SignalWifi4Bar
                } else {
                    Icons.Default.SignalWifiOff
                },
                contentDescription = null,
                tint = signalColor(network.level),
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = network.ssid.ifEmpty { stringResource(R.string.wifi_hidden_ssid) },
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "Ch ${network.channelNumber} · ${network.frequency} MHz · ${network.security}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = "${network.level} dBm",
                style = MaterialTheme.typography.bodySmall,
                fontFamily = MaterialTheme.typography.labelSmall.fontFamily,
                fontWeight = FontWeight.Bold,
                color = signalColor(network.level),
            )
        }
    }
}

@Composable
private fun signalColor(level: Int): androidx.compose.ui.graphics.Color {
    return when {
        level >= -50 -> MaterialTheme.colorScheme.primary
        level >= -70 -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.error
    }
}

private fun formatElapsed(ms: Long): String {
    val totalSeconds = ms / 1000
    return when {
        totalSeconds < 60 -> "${totalSeconds}s"
        totalSeconds < 3600 -> "${totalSeconds / 60}m"
        else -> "${totalSeconds / 3600}h"
    }
}
