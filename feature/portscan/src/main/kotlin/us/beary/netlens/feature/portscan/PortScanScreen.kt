package us.beary.netlens.feature.portscan

import androidx.compose.animation.animateColorAsState
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import us.beary.netlens.feature.portscan.model.PortResult
import us.beary.netlens.feature.portscan.model.PortScanUiState
import us.beary.netlens.feature.portscan.model.WellKnownPorts

private val OpenPortColor = Color(0xFF4CAF50)
private val ClosedPortColor = Color(0xFF6B7280)

private const val PRESET_COMMON = 0
private const val PRESET_ALL = 1
private const val PRESET_CUSTOM = 2

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PortScanScreen(
    viewModel: PortScanViewModel = hiltViewModel(),
) {
    val uiState by viewModel.state.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Port Scanner") })
        },
    ) { padding ->
        PortScanContent(
            state = uiState,
            onScan = { host, ports -> viewModel.scan(host, ports) },
            onCancel = viewModel::cancelScan,
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
            label = { Text("Host or IP address") },
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
                label = { Text("Common") },
            )
            FilterChip(
                selected = selectedPreset == PRESET_ALL,
                onClick = { selectedPreset = PRESET_ALL },
                label = { Text("All (1-1024)") },
            )
            FilterChip(
                selected = selectedPreset == PRESET_CUSTOM,
                onClick = { selectedPreset = PRESET_CUSTOM },
                label = { Text("Custom") },
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
                Text("Cancel Scan")
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
                Text("Scan Ports")
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
                text = error,
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
                compareByDescending<PortResult> { it.isOpen }.thenBy { it.port },
            )
            items(sortedResults, key = { it.port }) { result ->
                PortResultRow(result = result)
            }
        }
    }
}

@Composable
private fun StatsRow(state: PortScanUiState) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = "${state.openCount} open",
            color = OpenPortColor,
            style = MaterialTheme.typography.labelLarge,
        )
        Text(
            text = "${state.results.size} scanned",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun PortResultRow(result: PortResult) {
    val iconColor by animateColorAsState(
        targetValue = if (result.isOpen) OpenPortColor else ClosedPortColor,
        label = "portIconColor",
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = if (result.isOpen) Icons.Default.CheckCircle else Icons.Default.Close,
            contentDescription = if (result.isOpen) "Open" else "Closed",
            tint = iconColor,
            modifier = Modifier.size(20.dp),
        )

        Spacer(modifier = Modifier.width(12.dp))

        Text(
            text = result.port.toString(),
            style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
            modifier = Modifier.width(56.dp),
        )

        Text(
            text = result.serviceName,
            style = MaterialTheme.typography.bodyMedium,
            color = if (result.isOpen) {
                MaterialTheme.colorScheme.onSurface
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            modifier = Modifier.weight(1f),
        )

        if (result.isOpen && result.latencyMs > 0) {
            Text(
                text = "${result.latencyMs}ms",
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun portsForPreset(preset: Int): List<Int> = when (preset) {
    PRESET_COMMON -> WellKnownPorts.COMMON_PORTS.keys.sorted()
    PRESET_ALL -> (1..1024).toList()
    PRESET_CUSTOM -> WellKnownPorts.COMMON_PORTS.keys.sorted()
    else -> WellKnownPorts.COMMON_PORTS.keys.sorted()
}
