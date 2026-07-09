package com.ventouxlabs.netlens.feature.celltower

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CellTower
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.LocationOff
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SignalCellular4Bar
import androidx.compose.material.icons.filled.SignalCellularAlt
import androidx.compose.material.icons.filled.SignalCellularOff
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ventouxlabs.netlens.core.billing.LocalProStatus
import com.ventouxlabs.netlens.core.network.export.ResultExporter
import com.ventouxlabs.netlens.feature.celltower.model.CellTowerInfo
import com.ventouxlabs.netlens.feature.celltower.model.SignalQuality
import com.ventouxlabs.netlens.feature.celltower.model.rsrpQuality
import com.ventouxlabs.netlens.feature.celltower.model.rssiQuality

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CellTowerScreen(
    onBack: () -> Unit = {},
    viewModel: CellTowerViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val proStatus = LocalProStatus.current
    val isPro by proStatus.isPro.collectAsStateWithLifecycle()

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        viewModel.onPermissionResult(granted)
    }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        val granted = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) {
            viewModel.onPermissionResult(true)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.celltower_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.celltower_navigate_back),
                        )
                    }
                },
                actions = {
                    if (state.connectedTower != null || state.neighborCells.isNotEmpty()) {
                        IconButton(onClick = {
                            ResultExporter.copyToClipboard(
                                context, "Cell Tower", viewModel.buildExportText(),
                            )
                        }) {
                            Icon(
                                Icons.Default.ContentCopy,
                                contentDescription = stringResource(R.string.celltower_cd_copy),
                            )
                        }
                        if (isPro) {
                            IconButton(onClick = {
                                ResultExporter.shareAsText(
                                    context, "Cell Tower Info", viewModel.buildExportText(),
                                )
                            }) {
                                Icon(
                                    Icons.Default.Share,
                                    contentDescription = stringResource(R.string.celltower_cd_share),
                                )
                            }
                        }
                    }
                    IconButton(onClick = viewModel::refresh) {
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = stringResource(R.string.celltower_cd_refresh),
                        )
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            if (state.isRefreshing) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            when {
                !state.hasPermission -> PermissionPrompt(
                    onRequest = {
                        permissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                    },
                    modifier = Modifier.fillMaxSize(),
                )
                state.noCellular -> NoCellularState(modifier = Modifier.fillMaxSize())
                else -> CellTowerContent(
                    connectedTower = state.connectedTower,
                    neighbors = state.neighborCells,
                )
            }
        }
    }
}

@Composable
private fun CellTowerContent(
    connectedTower: CellTowerInfo?,
    neighbors: List<CellTowerInfo>,
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        connectedTower?.let { tower ->
            item {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.celltower_connected),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(modifier = Modifier.height(4.dp))
                ConnectedTowerCard(tower)
            }
        }

        if (neighbors.isNotEmpty()) {
            item {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.celltower_neighbors, neighbors.size),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            itemsIndexed(neighbors, key = { index, tower -> "${index}_${tower.networkType}_${tower.cellId}" }) { _, tower ->
                NeighborCellCard(tower)
            }
        }

        item { Spacer(modifier = Modifier.height(16.dp)) }
    }
}

@Composable
private fun ConnectedTowerCard(tower: CellTowerInfo) {
    val quality = tower.rsrp?.let { rsrpQuality(it) }
        ?: tower.rssi?.let { rssiQuality(it) }
        ?: SignalQuality.Unknown
    val qualityColor = qualityColor(quality)

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(
                    imageVector = Icons.Default.CellTower,
                    contentDescription = null,
                    modifier = Modifier.size(28.dp),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = tower.networkType,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                    if (tower.operatorName.isNotEmpty()) {
                        Text(
                            text = tower.operatorName,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                }
                SignalIndicator(quality = quality, color = qualityColor)
            }

            Spacer(modifier = Modifier.height(12.dp))
            TowerMetricsGrid(tower)
        }
    }
}

@Composable
private fun NeighborCellCard(tower: CellTowerInfo) {
    val quality = tower.rsrp?.let { rsrpQuality(it) }
        ?: tower.rssi?.let { rssiQuality(it) }
        ?: SignalQuality.Unknown
    val qualityColor = qualityColor(quality)

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = tower.networkType,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    if (tower.cellId.isNotEmpty()) {
                        Text(
                            text = "CID: ${tower.cellId}",
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = MaterialTheme.typography.labelSmall.fontFamily,
                            ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                SignalIndicator(quality = quality, color = qualityColor)
            }

            if (tower.band.isNotEmpty() || tower.rsrp != null || tower.rssi != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    if (tower.band.isNotEmpty()) {
                        MetricLabel(stringResource(R.string.celltower_label_band), tower.band)
                    }
                    tower.rsrp?.let {
                        MetricLabel(stringResource(R.string.celltower_label_rsrp), "${it}dBm", qualityColor)
                    }
                    tower.rssi?.let {
                        MetricLabel(stringResource(R.string.celltower_label_rssi), "${it}dBm", qualityColor)
                    }
                }
            }
        }
    }
}

@Composable
private fun TowerMetricsGrid(tower: CellTowerInfo) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (tower.cellId.isNotEmpty()) {
                MetricItem(
                    label = stringResource(R.string.celltower_label_cell_id),
                    value = tower.cellId,
                    modifier = Modifier.weight(1f),
                )
            }
            if (tower.tac.isNotEmpty()) {
                MetricItem(
                    label = stringResource(R.string.celltower_label_tac),
                    value = tower.tac,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        if (tower.band.isNotEmpty()) {
            MetricItem(
                label = stringResource(R.string.celltower_label_band),
                value = tower.band,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            tower.rsrp?.let {
                val q = rsrpQuality(it)
                MetricItem(
                    label = stringResource(R.string.celltower_label_rsrp),
                    value = "${it} dBm",
                    valueColor = qualityColor(q),
                    modifier = Modifier.weight(1f),
                )
            }
            tower.rsrq?.let {
                MetricItem(
                    label = stringResource(R.string.celltower_label_rsrq),
                    value = "${it} dB",
                    modifier = Modifier.weight(1f),
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            tower.sinr?.let {
                MetricItem(
                    label = stringResource(R.string.celltower_label_sinr),
                    value = "${it} dB",
                    modifier = Modifier.weight(1f),
                )
            }
            tower.rssi?.let {
                val q = rssiQuality(it)
                MetricItem(
                    label = stringResource(R.string.celltower_label_rssi),
                    value = "${it} dBm",
                    valueColor = qualityColor(q),
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun MetricItem(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    valueColor: Color = MaterialTheme.colorScheme.onPrimaryContainer,
) {
    Column(modifier = modifier) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            // This grid sits on a primaryContainer card, so the label must use
            // that container's on-color, not onSurfaceVariant.
            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontFamily = MaterialTheme.typography.labelSmall.fontFamily,
            ),
            fontWeight = FontWeight.Medium,
            color = valueColor,
        )
    }
}

@Composable
private fun MetricLabel(label: String, value: String, valueColor: Color = Color.Unspecified) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = "$label: ",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall.copy(
                fontFamily = MaterialTheme.typography.labelSmall.fontFamily,
            ),
            color = if (valueColor != Color.Unspecified) valueColor
            else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SignalIndicator(quality: SignalQuality, color: Color) {
    val icon = when (quality) {
        SignalQuality.Excellent -> Icons.Default.SignalCellular4Bar
        SignalQuality.Fair -> Icons.Default.SignalCellularAlt
        SignalQuality.Poor -> Icons.Default.SignalCellularOff
        SignalQuality.Unknown -> Icons.Default.SignalCellularAlt
    }
    val label = quality.name
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            modifier = Modifier.size(24.dp),
            tint = color,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = color,
        )
    }
}

@Composable
private fun qualityColor(quality: SignalQuality): Color = when (quality) {
    SignalQuality.Excellent -> MaterialTheme.colorScheme.primary
    SignalQuality.Fair -> MaterialTheme.colorScheme.tertiary
    SignalQuality.Poor -> MaterialTheme.colorScheme.error
    SignalQuality.Unknown -> MaterialTheme.colorScheme.outline
}

@Composable
private fun PermissionPrompt(onRequest: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = Icons.Default.LocationOff,
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.celltower_permission_title),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.celltower_permission_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 32.dp),
        )
        Spacer(modifier = Modifier.height(24.dp))
        Button(onClick = onRequest) {
            Text(text = stringResource(R.string.celltower_permission_grant))
        }
    }
}

@Composable
private fun NoCellularState(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = Icons.Default.SignalCellularOff,
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.celltower_no_cellular),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
