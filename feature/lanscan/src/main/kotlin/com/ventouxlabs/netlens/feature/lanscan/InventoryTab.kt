package com.ventouxlabs.netlens.feature.lanscan

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.outlined.DeviceUnknown
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ventouxlabs.netlens.core.data.model.DeviceTags
import com.ventouxlabs.netlens.core.data.model.KnownDeviceEntity
import com.ventouxlabs.netlens.core.ui.StampChip
import com.ventouxlabs.netlens.feature.lanscan.model.DeviceSortField

/**
 * The Inventory tab shows every device the app has ever seen, independent of the current scan —
 * this is the "known devices" ledger a user builds up over time (tag, rename, mark known/unknown),
 * as opposed to the Scan tab's point-in-time snapshot of what's on the network right now.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun InventoryTabContent(
    devices: List<KnownDeviceEntity>,
    searchQuery: String,
    sortField: DeviceSortField,
    sortAscending: Boolean,
    onSearchChanged: (String) -> Unit,
    onSortFieldChanged: (DeviceSortField) -> Unit,
    onToggleSortOrder: () -> Unit,
    onToggleKnown: (Long) -> Unit,
    onDeleteDevice: (Long) -> Unit,
    onClearInventory: () -> Unit,
    onEditDeviceDetails: (KnownDeviceEntity) -> Unit,
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
                    key = { it.id },
                ) { device ->
                    InventoryDeviceCard(
                        device = device,
                        onToggleKnown = { onToggleKnown(device.id) },
                        onDelete = { onDeleteDevice(device.id) },
                        onEditDetails = { onEditDeviceDetails(device) },
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

@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
private fun InventoryDeviceCard(
    device: KnownDeviceEntity,
    onToggleKnown: () -> Unit,
    onDelete: () -> Unit,
    onEditDetails: () -> Unit,
) {
    var showDeleteDialog by remember { mutableStateOf(false) }
    val tags = remember(device.tags) { DeviceTags.parse(device.tags) }

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
                    // Matches the Devices screen's precedence: a name the user typed always
                    // wins over whatever the scanner resolved.
                    text = device.customName ?: device.hostname ?: device.ip,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )

                device.location?.let { location ->
                    Text(
                        text = stringResource(R.string.lanscan_inventory_location, location),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

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
                    text = device.macAddress ?: stringResource(R.string.lanscan_inventory_mac_unknown),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    maxLines = 1,
                )

                if (device.customName != null || device.hostname != null) {
                    Text(
                        text = device.ip,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        maxLines = 1,
                    )
                }

                if (tags.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        tags.forEach { tag -> StampChip(text = tag) }
                    }
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

            IconButton(onClick = onEditDetails) {
                Icon(
                    imageVector = Icons.Default.Edit,
                    contentDescription = stringResource(R.string.lanscan_inventory_edit_details),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
            }

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
