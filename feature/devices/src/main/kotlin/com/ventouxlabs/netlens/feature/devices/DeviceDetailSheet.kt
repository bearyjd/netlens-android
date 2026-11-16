package com.ventouxlabs.netlens.feature.devices

import androidx.annotation.StringRes
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
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.ventouxlabs.netlens.core.data.model.DeviceTags
import com.ventouxlabs.netlens.core.data.model.FingerprintEvidence
import com.ventouxlabs.netlens.core.data.model.KnownDeviceEntity
import com.ventouxlabs.netlens.core.data.model.UserDetails
import com.ventouxlabs.netlens.core.data.model.UserFieldLimits
import com.ventouxlabs.netlens.core.data.model.userDetails
import com.ventouxlabs.netlens.core.network.DisplayText
import com.ventouxlabs.netlens.feature.devices.model.DetailDraft
import com.ventouxlabs.netlens.feature.devices.model.DetailNotice
import com.ventouxlabs.netlens.feature.devices.model.DeviceDetailsEdit
import com.ventouxlabs.netlens.feature.devices.model.bidiIsolate
import com.ventouxlabs.netlens.feature.devices.model.displayModel
import com.ventouxlabs.netlens.feature.devices.model.displayName
import com.ventouxlabs.netlens.feature.devices.model.displayOs
import com.ventouxlabs.netlens.feature.devices.model.displayType
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
    /** Saves against `base`, the stored values captured when this editor (or its draft) opened. */
    onSaveDetails: (base: UserDetails, edit: DeviceDetailsEdit) -> Unit,
    onToggleKnown: () -> Unit,
    onDelete: () -> Unit,
    /** Tags already used elsewhere in the inventory, offered as one-tap suggestions. */
    knownTags: List<String> = emptyList(),
    /** A redirected or refused save's draft; used only when it belongs to [device]. */
    draft: DetailDraft? = null,
    saving: Boolean = false,
    notice: DetailNotice? = null,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        val ownDraft = draft?.takeIf { it.deviceId == device.id }
        val draftToken = ownDraft?.token ?: 0
        // Keyed on the device id and the draft token: reopening on a different row starts from that
        // row's stored values, and a new draft replaces the editor. The base is captured HERE, once
        // per editor session; the sheet recomposes with a fresh `device` on every database emission,
        // so reading it at save time would make every save look current and defeat the guard.
        val base = remember(device.id, draftToken) { ownDraft?.base ?: device.userDetails() }
        var edit by remember(device.id, draftToken) { mutableStateOf(ownDraft?.edit ?: DeviceDetailsEdit.from(device)) }
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
            notice?.let { DetailNoticeText(it) }
            Text(device.ip, style = MaterialTheme.typography.labelSmall)
            Text(
                device.macAddress ?: stringResource(R.string.devices_mac_unknown),
                style = MaterialTheme.typography.labelSmall,
            )
            device.vendor?.let { Text(stringResource(R.string.devices_detail_vendor, it)) }
            // Effective values: the user's override wins over detection, and both are flattened.
            device.displayType()?.let { Text(stringResource(R.string.devices_detail_type, it)) }
            device.displayOs()?.let { Text(stringResource(R.string.devices_detail_os, it)) }
            device.displayModel()?.let { Text(stringResource(R.string.devices_detail_model, it)) }
            device.fingerprintConfidence?.let {
                Text(stringResource(R.string.devices_detail_confidence, it))
            }
            device.fingerprintEvidence?.let {
                Text(
                    text = stringResource(R.string.devices_detail_evidence, FingerprintEvidence.formatForDisplay(it)),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Text(stringResource(R.string.devices_first_seen, formatSeenTimestamp(device.firstSeen)))
            Text(stringResource(R.string.devices_last_seen, formatSeenTimestamp(device.lastSeen)))

            // Capped here rather than only in normalized(): truncating at save time drops
            // characters the user can still see on screen, with no hint that it happened.
            OutlinedTextField(
                value = edit.customName,
                onValueChange = { edit = edit.copy(customName = it.take(UserFieldLimits.MAX_NAME_LENGTH)) },
                label = { Text(stringResource(R.string.devices_detail_rename)) },
                placeholder = { Text(stringResource(R.string.devices_detail_rename_hint)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            OverrideField(
                value = edit.deviceTypeOverride,
                onValueChange = { edit = edit.copy(deviceTypeOverride = it.take(UserFieldLimits.MAX_OVERRIDE_LENGTH)) },
                label = R.string.devices_detail_type_override,
                detected = device.deviceType,
            )
            OverrideField(
                value = edit.osOverride,
                onValueChange = { edit = edit.copy(osOverride = it.take(UserFieldLimits.MAX_OVERRIDE_LENGTH)) },
                label = R.string.devices_detail_os_override,
                detected = device.osGuess,
            )
            OverrideField(
                value = edit.modelOverride,
                onValueChange = { edit = edit.copy(modelOverride = it.take(UserFieldLimits.MAX_OVERRIDE_LENGTH)) },
                label = R.string.devices_detail_model_override,
                detected = device.model,
            )

            OutlinedTextField(
                value = edit.location,
                onValueChange = { edit = edit.copy(location = it.take(UserFieldLimits.MAX_LOCATION_LENGTH)) },
                label = { Text(stringResource(R.string.devices_detail_location)) },
                placeholder = { Text(stringResource(R.string.devices_detail_location_hint)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            val tagsAtCapacity = enteredTags.size >= DeviceTags.MAX_TAGS
            OutlinedTextField(
                value = edit.tagsInput,
                onValueChange = { next ->
                    // Refuse input that would exceed the cap rather than accepting it and letting
                    // format() drop it at save — format keeps the FIRST MAX_TAGS, so the tag just
                    // typed is the one that would vanish. Shrinking edits always go through, so a
                    // row that somehow holds more than the cap can still be edited down.
                    val withinCap = DeviceTags.parse(next).size <= DeviceTags.MAX_TAGS
                    if (withinCap || next.length < edit.tagsInput.length) {
                        edit = edit.copy(tagsInput = next)
                    }
                },
                label = { Text(stringResource(R.string.devices_detail_tags)) },
                placeholder = { Text(stringResource(R.string.devices_detail_tags_hint)) },
                supportingText = {
                    // format() keeps the *first* MAX_TAGS, so silently over-filling would discard
                    // the tag just added rather than the oldest — say so instead.
                    Text(
                        if (tagsAtCapacity) {
                            stringResource(R.string.devices_detail_tags_full, DeviceTags.MAX_TAGS)
                        } else {
                            stringResource(R.string.devices_detail_tags_help)
                        },
                    )
                },
                isError = tagsAtCapacity,
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            if (suggestions.isNotEmpty() && !tagsAtCapacity) {
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
                                        .take(DeviceTags.MAX_TAGS)
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
                    edit = edit.copy(notes = it.take(UserFieldLimits.MAX_NOTES_LENGTH))
                },
                label = { Text(stringResource(R.string.devices_detail_notes)) },
                placeholder = { Text(stringResource(R.string.devices_detail_notes_hint)) },
                minLines = 3,
                maxLines = 6,
                modifier = Modifier.fillMaxWidth(),
            )

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                // Stays open until the save answers: a redirected or refused save hands back a draft.
                Button(onClick = { onSaveDetails(base, edit) }, enabled = !saving) {
                    Text(stringResource(if (saving) R.string.devices_detail_saving else R.string.devices_detail_save))
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

/** Announced politely: it appears after a save, while focus is still on the Save button. */
@Composable
private fun DetailNoticeText(notice: DetailNotice) {
    Column(
        Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            if (notice.arg != null) stringResource(notice.message, notice.arg) else stringResource(notice.message),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
        )
        notice.discarded.forEach {
            Text(
                stringResource(R.string.devices_detail_not_applied, stringResource(it.field.label), bidiIsolate(it.text)),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

/** Override input; the placeholder shows the detected value that a blank field falls back to. */
@Composable
private fun OverrideField(
    value: String,
    onValueChange: (String) -> Unit,
    @StringRes label: Int,
    detected: String?,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(stringResource(label)) },
        placeholder = {
            Text(
                DisplayText.flatten(detected)
                    ?.let { stringResource(R.string.devices_detail_override_detected, it) }
                    ?: stringResource(R.string.devices_detail_override_none),
            )
        },
        supportingText = { Text(stringResource(R.string.devices_detail_override_help)) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
}
