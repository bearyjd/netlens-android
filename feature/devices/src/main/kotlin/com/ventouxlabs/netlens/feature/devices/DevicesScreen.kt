package com.ventouxlabs.netlens.feature.devices

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ventouxlabs.netlens.core.data.model.KnownDeviceEntity
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

    if (selected != null) {
        DeviceDetailSheet(
            device = selected,
            onDismiss = { viewModel.selectDevice(null) },
            onRename = { viewModel.rename(selected.id, it) },
            onToggleKnown = { viewModel.toggleKnown(selected.id) },
            onDelete = { viewModel.delete(selected.id) },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.devices_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.navigate_back))
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
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
