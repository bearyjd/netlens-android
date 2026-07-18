package com.ventouxlabs.netlens.feature.devices

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ventouxlabs.netlens.core.data.model.KnownDeviceEntity
import com.ventouxlabs.netlens.feature.devices.model.displayName
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

private val DETAIL_TIMESTAMP_FORMATTER: DateTimeFormatter =
    DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
        .withLocale(Locale.getDefault())
        .withZone(ZoneId.systemDefault())

private fun formatSeenTimestamp(epochMillis: Long): String =
    DETAIL_TIMESTAMP_FORMATTER.format(Instant.ofEpochMilli(epochMillis))

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceDetailSheet(
    device: KnownDeviceEntity,
    onDismiss: () -> Unit,
    onRename: (String) -> Unit,
    onToggleKnown: () -> Unit,
    onDelete: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        var name by remember(device.id) { mutableStateOf(device.customName ?: "") }
        Column(Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(device.displayName(), style = MaterialTheme.typography.titleLarge)
            Text(device.ip, style = MaterialTheme.typography.labelSmall)
            Text(
                device.macAddress ?: stringResource(R.string.devices_mac_unknown),
                style = MaterialTheme.typography.labelSmall,
            )
            device.vendor?.let { Text(stringResource(R.string.devices_detail_vendor, it)) }
            device.deviceType?.let { Text(stringResource(R.string.devices_detail_type, it)) }
            device.osGuess?.let { Text(stringResource(R.string.devices_detail_os, it)) }
            Text(stringResource(R.string.devices_first_seen, formatSeenTimestamp(device.firstSeen)))
            Text(stringResource(R.string.devices_last_seen, formatSeenTimestamp(device.lastSeen)))

            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(R.string.devices_detail_rename)) },
                placeholder = { Text(stringResource(R.string.devices_detail_rename_hint)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = { onRename(name); onDismiss() }) {
                    Text(stringResource(R.string.devices_detail_save))
                }
                OutlinedButton(onClick = onToggleKnown) {
                    Text(
                        if (device.isKnown) stringResource(R.string.devices_detail_mark_unknown)
                        else stringResource(R.string.devices_detail_mark_known),
                    )
                }
            }
            OutlinedButton(onClick = { onDelete(); onDismiss() }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.devices_detail_delete))
            }
            Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.devices_detail_close))
                }
            }
        }
    }
}
