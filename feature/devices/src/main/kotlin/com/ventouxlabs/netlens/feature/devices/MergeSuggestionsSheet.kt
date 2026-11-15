package com.ventouxlabs.netlens.feature.devices

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ventouxlabs.netlens.feature.devices.model.MergeSuggestionUi
import com.ventouxlabs.netlens.feature.devices.model.bidiIsolate
import com.ventouxlabs.netlens.feature.devices.model.SuggestionRow
import com.ventouxlabs.netlens.feature.devices.model.UserField

private val MIN_TOUCH_TARGET = 48.dp

/** Banner above the list; one tap target that opens the suggestions sheet. */
@Composable
internal fun MergeSuggestionsBanner(count: Int, onClick: () -> Unit) {
    val label = pluralStringResource(R.plurals.devices_suggestion_banner, count, count)
    val description = stringResource(R.string.devices_suggestion_banner_cd)
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = MIN_TOUCH_TARGET)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics(mergeDescendants = true) { contentDescription = "$label. $description" }
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
        Text(stringResource(R.string.devices_suggestion_banner_action), style = MaterialTheme.typography.labelLarge)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MergeSuggestionsSheet(
    suggestions: List<MergeSuggestionUi>,
    busyKey: String?,
    message: Int?,
    onDismiss: () -> Unit,
    onMerge: (key: String, isKnown: Boolean?, confirmed: Boolean) -> Unit,
    onNotTheSame: (key: String) -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        MergeSuggestionsContent(suggestions, busyKey, message, onDismiss, onMerge, onNotTheSame)
    }
}

/** Sheet body, stateless so a composition smoke test can render it without a sheet window. */
@Composable
internal fun MergeSuggestionsContent(
    suggestions: List<MergeSuggestionUi>,
    busyKey: String?,
    message: Int?,
    onClose: () -> Unit,
    onMerge: (key: String, isKnown: Boolean?, confirmed: Boolean) -> Unit,
    onNotTheSame: (key: String) -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp)) {
        Text(
            stringResource(R.string.devices_suggestion_sheet_title),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.semantics { heading() },
        )
        message?.let {
            Text(
                stringResource(it),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 8.dp).semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
        if (suggestions.isEmpty()) {
            Text(
                stringResource(R.string.devices_suggestion_empty),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(vertical = 16.dp),
            )
        }
        // Keys are namespaced ("suggestion:low:high") so they cannot collide with any other keyed
        // item, whatever lazy container this list ever shares.
        LazyColumn(Modifier.weight(1f, fill = false), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(suggestions, key = { it.key }) { suggestion ->
                SuggestionCard(suggestion, busy = busyKey != null, onMerge = onMerge, onNotTheSame = onNotTheSame)
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = onClose, modifier = Modifier.heightIn(min = MIN_TOUCH_TARGET)) {
                Text(stringResource(R.string.devices_suggestion_close))
            }
        }
    }
}

@Composable
private fun SuggestionCard(
    suggestion: MergeSuggestionUi,
    busy: Boolean,
    onMerge: (String, Boolean?, Boolean) -> Unit,
    onNotTheSame: (String) -> Unit,
) {
    // No default: a differing-trust merge needs the user's explicit choice, so Merge stays disabled
    // until one is made. Keyed on the pair so a recycled card never inherits another pair's choice.
    var trustChoice by rememberSaveable(suggestion.key) { mutableStateOf<Boolean?>(null) }
    val mergeEnabled = !busy && (!suggestion.trustDiffers || trustChoice != null)
    var confirming by rememberSaveable(suggestion.key) { mutableStateOf(false) }
    val survivorName = bidiIsolate(suggestion.survivor.name)
    val loserName = bidiIsolate(suggestion.loser.name)

    if (confirming) {
        AlertDialog(
            onDismissRequest = { confirming = false },
            title = { Text(stringResource(R.string.devices_suggestion_confirm_title)) },
            text = { MergeConfirmContent(suggestion, trustChoice) },
            confirmButton = {
                TextButton(
                    onClick = { confirming = false; onMerge(suggestion.key, trustChoice, true) },
                    modifier = Modifier.heightIn(min = MIN_TOUCH_TARGET),
                ) { Text(stringResource(R.string.devices_suggestion_confirm_action)) }
            },
            dismissButton = {
                TextButton(onClick = { confirming = false }, modifier = Modifier.heightIn(min = MIN_TOUCH_TARGET)) {
                    Text(stringResource(R.string.devices_suggestion_confirm_cancel))
                }
            },
        )
    }

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            RowSummary(stringResource(R.string.devices_suggestion_survives, survivorName), suggestion.survivor)
            RowSummary(stringResource(R.string.devices_suggestion_removed, loserName), suggestion.loser)
            if (suggestion.macsDiffer) {
                Text(
                    stringResource(R.string.devices_suggestion_macs_differ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            if (suggestion.escalatesTrust) {
                Text(
                    stringResource(R.string.devices_suggestion_escalation_warning),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            if (suggestion.reasons.isNotEmpty()) {
                val reasons = suggestion.reasons.map { stringResource(it.label) }.joinToString(", ")
                Text(stringResource(R.string.devices_suggestion_reasons, reasons), style = MaterialTheme.typography.bodySmall)
            }
            MergePreview(suggestion)
            if (suggestion.trustDiffers) {
                TrustChoice(trustChoice) { trustChoice = it }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val mergeDescription = stringResource(R.string.devices_suggestion_merge_cd, survivorName, loserName)
                Button(
                    onClick = { if (suggestion.requiresConfirmation) confirming = true else onMerge(suggestion.key, trustChoice, false) },
                    enabled = mergeEnabled,
                    modifier = Modifier
                        .heightIn(min = MIN_TOUCH_TARGET)
                        .semantics { contentDescription = mergeDescription },
                ) { Text(stringResource(R.string.devices_suggestion_merge)) }
                val rejectDescription = stringResource(R.string.devices_suggestion_not_same_cd, survivorName, loserName)
                OutlinedButton(
                    onClick = { onNotTheSame(suggestion.key) },
                    enabled = !busy,
                    modifier = Modifier
                        .heightIn(min = MIN_TOUCH_TARGET)
                        .semantics { contentDescription = rejectDescription },
                ) { Text(stringResource(R.string.devices_suggestion_not_same)) }
            }
        }
    }
}

@Composable
private fun RowSummary(title: String, row: SuggestionRow) {
    Column {
        Text(title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
        // labelSmall is the Mono role (DESIGN.md): IPs and MACs belong in it.
        Text(
            "${bidiIsolate(row.ip)}  ·  ${bidiIsolate(row.mac ?: stringResource(R.string.devices_mac_unknown))}",
            style = MaterialTheme.typography.labelSmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            stringResource(if (row.isKnown) R.string.devices_suggestion_trust_known else R.string.devices_suggestion_trust_new),
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
private fun MergePreview(suggestion: MergeSuggestionUi) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        val none = suggestion.keptFields.isEmpty() && suggestion.filledFields.isEmpty() && suggestion.droppedFields.isEmpty()
        if (none) Text(stringResource(R.string.devices_suggestion_no_details), style = MaterialTheme.typography.bodySmall)
        FieldLine(R.string.devices_suggestion_kept, suggestion.keptFields)
        FieldLine(R.string.devices_suggestion_filled, suggestion.filledFields)
        FieldLine(R.string.devices_suggestion_dropped, suggestion.droppedFields)
        if (suggestion.movedIdentifierCount == 0) {
            Text(stringResource(R.string.devices_suggestion_moved_none), style = MaterialTheme.typography.bodySmall)
        } else {
            Text(
                stringResource(R.string.devices_suggestion_moved, suggestion.movedIdentifierCount),
                style = MaterialTheme.typography.bodySmall,
            )
            suggestion.movedIdentifiers.forEach {
                Text("${it.kind}: ${it.value}", style = MaterialTheme.typography.labelSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            val more = suggestion.movedIdentifierCount - suggestion.movedIdentifiers.size
            if (more > 0) Text(stringResource(R.string.devices_suggestion_moved_more, more), style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun FieldLine(template: Int, fields: List<UserField>) {
    if (fields.isEmpty()) return
    Text(
        stringResource(template, fields.map { stringResource(it.label) }.joinToString(", ")),
        style = MaterialTheme.typography.bodySmall,
    )
}

@Composable
private fun TrustChoice(choice: Boolean?, onChoose: (Boolean) -> Unit) {
    Column(Modifier.selectableGroup()) {
        Text(stringResource(R.string.devices_suggestion_trust_prompt), style = MaterialTheme.typography.bodySmall)
        // "New" first: the safe option is the first one a screen reader reaches.
        TrustOption(R.string.devices_suggestion_trust_choose_new, selected = choice == false) { onChoose(false) }
        TrustOption(R.string.devices_suggestion_trust_choose_known, selected = choice == true) { onChoose(true) }
    }
}

@Composable
private fun TrustOption(label: Int, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = MIN_TOUCH_TARGET)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Text(stringResource(label), modifier = Modifier.padding(start = 8.dp))
    }
}

/** Body of the confirmation dialog, stateless so a composition smoke test can render it without a dialog window. */
@Composable
internal fun MergeConfirmContent(suggestion: MergeSuggestionUi, trustChoice: Boolean?) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            stringResource(R.string.devices_suggestion_confirm_irreversible, bidiIsolate(suggestion.loser.name)),
            style = MaterialTheme.typography.bodyMedium,
        )
        suggestion.droppedValues.forEach {
            Text(
                stringResource(R.string.devices_suggestion_confirm_lost, stringResource(it.field.label), bidiIsolate(it.value)),
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (suggestion.trustDiffers && trustChoice != null) {
            val label = stringResource(if (trustChoice) R.string.devices_suggestion_trust_known else R.string.devices_suggestion_trust_new)
            Text(stringResource(R.string.devices_suggestion_confirm_trust, label), style = MaterialTheme.typography.bodyMedium)
        }
        if (suggestion.escalatesTrust && trustChoice == true) {
            Text(
                stringResource(
                    R.string.devices_suggestion_confirm_mac,
                    bidiIsolate(suggestion.loser.mac ?: stringResource(R.string.devices_mac_unknown)),
                    bidiIsolate(suggestion.survivor.name),
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
            Text(
                stringResource(R.string.devices_suggestion_escalation_warning),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}
