package com.ventouxlabs.netlens.feature.devices

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SuggestionChip
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
import com.ventouxlabs.netlens.core.data.model.DeviceTags
import com.ventouxlabs.netlens.core.data.model.KnownDeviceEntity
import com.ventouxlabs.netlens.feature.devices.model.DeviceDetailsEdit
import com.ventouxlabs.netlens.feature.devices.model.MAX_DEVICE_NOTES_LENGTH
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

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun DeviceDetailSheet(
    device: KnownDeviceEntity,
    onDismiss: () -> Unit,
    onSaveDetails: (DeviceDetailsEdit) -> Unit,
    onToggleKnown: () -> Unit,
    onDelete: () -> Unit,
    /** Tags already used elsewhere in the inventory, offered as one-tap suggestions. */
    knownTags: List<String> = emptyList(),
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        // Keyed on the device id so reopening the sheet on a different row starts from that
        // row's stored values instead of the previous device's half-typed edit.
        var edit by remember(device.id) { mutableStateOf(DeviceDetailsEdit.from(device)) }
        val enteredTags = remember(edit.tagsInput) { DeviceTags.parse(edit.tagsInput) }
        val suggestions = remember(knownTags, enteredTags) {
            knownTags.filterNot { known -> enteredTags.any { it.equals(known, ignoreCase = true) } }
        }

        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
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
                value = edit.customName,
                onValueChange = { edit = edit.copy(customName = it) },
                label = { Text(stringResource(R.string.devices_detail_rename)) },
                placeholder = { Text(stringResource(R.string.devices_detail_rename_hint)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = edit.location,
                onValueChange = { edit = edit.copy(location = it) },
                label = { Text(stringResource(R.string.devices_detail_location)) },
                placeholder = { Text(stringResource(R.string.devices_detail_location_hint)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = edit.tagsInput,
                onValueChange = { edit = edit.copy(tagsInput = it) },
                label = { Text(stringResource(R.string.devices_detail_tags)) },
                placeholder = { Text(stringResource(R.string.devices_detail_tags_hint)) },
                supportingText = { Text(stringResource(R.string.devices_detail_tags_help)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            if (suggestions.isNotEmpty()) {
                Text(
                    stringResource(R.string.devices_detail_tag_suggestions),
                    style = MaterialTheme.typography.labelMedium,
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    suggestions.forEach { tag ->
                        SuggestionChip(
                            onClick = {
                                edit = edit.copy(
                                    tagsInput = DeviceTags.parse(edit.tagsInput)
                                        .plus(tag)
                                        .joinToString(", "),
                                )
                            },
                            label = { Text(tag) },
                        )
                    }
                }
            }

            OutlinedTextField(
                value = edit.notes,
                onValueChange = {
                    edit = edit.copy(notes = it.take(MAX_DEVICE_NOTES_LENGTH))
                },
                label = { Text(stringResource(R.string.devices_detail_notes)) },
                placeholder = { Text(stringResource(R.string.devices_detail_notes_hint)) },
                minLines = 3,
                maxLines = 6,
                modifier = Modifier.fillMaxWidth(),
            )

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = { onSaveDetails(edit); onDismiss() }) {
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
