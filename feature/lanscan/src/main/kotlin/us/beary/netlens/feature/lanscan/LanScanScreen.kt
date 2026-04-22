package us.beary.netlens.feature.lanscan

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import us.beary.netlens.feature.lanscan.model.LanDevice
import us.beary.netlens.feature.lanscan.model.LanScanUiState

@Composable
fun LanScanScreen(
    viewModel: LanScanViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LanScanContent(
        uiState = uiState,
        onStartScan = viewModel::startScan,
        onCancelScan = viewModel::cancelScan,
    )
}

@Composable
private fun LanScanContent(
    uiState: LanScanUiState,
    onStartScan: () -> Unit,
    onCancelScan: () -> Unit,
) {
    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = if (uiState.isScanning) onCancelScan else onStartScan,
            ) {
                Icon(
                    imageVector = if (uiState.isScanning) {
                        Icons.Default.Close
                    } else {
                        Icons.Default.Search
                    },
                    contentDescription = if (uiState.isScanning) "Stop scan" else "Start scan",
                )
            }
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
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
                        text = "Subnet: ${uiState.subnetInfo}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    SuggestionChip(
                        onClick = {},
                        label = {
                            Text("${uiState.devices.size} devices")
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

@Composable
private fun DeviceCard(device: LanDevice) {
    Card(
        modifier = Modifier.fillMaxWidth(),
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

                device.mac?.let { mac ->
                    Text(
                        text = mac,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                device.hostname?.let { hostname ->
                    Text(
                        text = hostname,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            device.vendor?.let { vendor ->
                Spacer(modifier = Modifier.width(8.dp))
                SuggestionChip(
                    onClick = {},
                    label = {
                        Text(
                            text = vendor,
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1,
                        )
                    },
                )
            }
        }
    }
}
