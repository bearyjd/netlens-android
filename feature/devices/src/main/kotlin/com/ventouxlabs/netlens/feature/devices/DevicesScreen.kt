package com.ventouxlabs.netlens.feature.devices

import android.content.ActivityNotFoundException
import android.content.Intent
import android.provider.Settings
import android.util.Log
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ventouxlabs.netlens.core.billing.LocalProStatus
import com.ventouxlabs.netlens.core.data.model.DeviceTags
import com.ventouxlabs.netlens.core.data.model.KnownDeviceEntity
import com.ventouxlabs.netlens.core.data.model.WatchedNetworkEntity
import com.ventouxlabs.netlens.core.network.export.ResultExporter
import com.ventouxlabs.netlens.core.ui.StampChip
import com.ventouxlabs.netlens.feature.devices.model.DeviceDetailsEdit
import com.ventouxlabs.netlens.feature.devices.model.DevicesUiState
import com.ventouxlabs.netlens.feature.devices.model.WatchCadence
import com.ventouxlabs.netlens.feature.devices.model.displayName

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DevicesScreen(
    onBack: () -> Unit = {},
    /** Pre-fills the search box — how "Tag this host" from a LAN scan lands on one device. */
    initialQuery: String? = null,
    onNavigateToTool: (String, String) -> Unit = { _, _ -> },
    viewModel: DevicesViewModel = hiltViewModel(),
) {
    // Applied once, not on every recomposition: the key is a constant nav argument, so without
    // the flag a rotation re-imposes the incoming filter over whatever the user has since typed
    // (or cleared) — the ViewModel outlives the composition, the effect does not.
    var queryApplied by rememberSaveable(initialQuery) { mutableStateOf(false) }
    LaunchedEffect(initialQuery) {
        if (initialQuery != null && !queryApplied) {
            viewModel.setSearchQuery(initialQuery)
            queryApplied = true
        }
    }
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val proStatus = LocalProStatus.current
    val isPro by proStatus.isPro.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    uiState.watchError?.let { messageRes ->
        val message = stringResource(messageRes)
        LaunchedEffect(messageRes) {
            snackbarHostState.showSnackbar(message)
            viewModel.clearWatchError()
        }
    }

    DevicesContent(
        state = uiState,
        isPro = isPro,
        onBack = onBack,
        onCopyResults = { ResultExporter.copyToClipboard(context, "Devices", viewModel.buildExportText()) },
        onShareResults = { ResultExporter.shareAsText(context, "Device Inventory", viewModel.buildExportText()) },
        watchSection = {
            WatchSection(
                isPro = isPro,
                watchedNetworks = uiState.watchedNetworks,
                cadence = uiState.cadence,
                masterEnabled = uiState.masterWatchEnabled,
                onWatchThisNetwork = viewModel::watchCurrentNetwork,
                onToggleNetwork = viewModel::toggleNetworkWatch,
                onRemoveNetwork = viewModel::removeWatchedNetwork,
                onMasterToggle = { enabled -> viewModel.setMasterWatch(enabled, isPro) },
                onCadenceChange = { cadence -> viewModel.setCadence(cadence, isPro) },
            )
        },
        onSearchQueryChanged = viewModel::setSearchQuery,
        onToggleTag = viewModel::toggleTagFilter,
        onClearTags = viewModel::clearTagFilters,
        onSelectDevice = viewModel::selectDevice,
        onSaveDetails = viewModel::saveDetails,
        onToggleKnown = viewModel::toggleKnown,
        onDelete = viewModel::delete,
        snackbarHostState = snackbarHostState,
    )
}

/** State-driven screen body, kept separate from Hilt wiring for composition smoke tests. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DevicesContent(
    state: DevicesUiState,
    isPro: Boolean,
    onBack: () -> Unit,
    onCopyResults: () -> Unit,
    onShareResults: () -> Unit,
    watchSection: @Composable () -> Unit,
    onSearchQueryChanged: (String) -> Unit,
    onToggleTag: (String) -> Unit,
    onClearTags: () -> Unit,
    onSelectDevice: (Long?) -> Unit,
    onSaveDetails: (Long, DeviceDetailsEdit) -> Unit,
    onToggleKnown: (Long) -> Unit,
    onDelete: (Long) -> Unit,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
    val selected = state.devices.find { it.id == state.selectedDeviceId }
    if (selected != null) {
        DeviceDetailSheet(
            device = selected,
            onDismiss = { onSelectDevice(null) },
            onSaveDetails = { onSaveDetails(selected.id, it) },
            onToggleKnown = { onToggleKnown(selected.id) },
            onDelete = { onDelete(selected.id) },
            knownTags = state.availableTags,
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.devices_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.navigate_back))
                    }
                },
                actions = {
                    IconButton(onClick = {
                        onCopyResults()
                    }) {
                        Icon(Icons.Default.ContentCopy, stringResource(R.string.devices_cd_copy_results))
                    }
                    if (isPro) {
                        IconButton(onClick = {
                            onShareResults()
                        }) {
                            Icon(Icons.Default.Share, stringResource(R.string.devices_cd_share))
                        }
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            watchSection()
            OutlinedTextField(
                value = state.searchQuery,
                onValueChange = onSearchQueryChanged,
                placeholder = { Text(stringResource(R.string.devices_search_placeholder)) },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                trailingIcon = {
                    if (state.searchQuery.isNotEmpty()) {
                        IconButton(onClick = { onSearchQueryChanged("") }) {
                            Icon(Icons.Default.Close, stringResource(R.string.devices_clear_search))
                        }
                    }
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(16.dp),
            )

            TagFilterRow(
                availableTags = state.availableTags,
                activeTags = state.activeTags,
                onToggleTag = onToggleTag,
                onClearTags = onClearTags,
            )

            // Partition once per device-list change rather than on every recomposition
            // (every search keystroke, selection, and isPro emission re-filtered twice).
            val (newDevices, knownDevices) = remember(state.devices) {
                state.devices.partition { !it.isKnown }
            }

            if (state.devices.isEmpty()) {
                Text(
                    if (state.searchQuery.isBlank()) stringResource(R.string.devices_empty)
                    else stringResource(R.string.devices_no_results),
                    modifier = Modifier.fillMaxWidth().padding(32.dp),
                    style = MaterialTheme.typography.bodyMedium,
                )
            } else {
                LazyColumn(Modifier.fillMaxSize()) {
                    // Keys are namespaced per section even though `partition` guarantees the two
                    // lists are disjoint. That guarantee lives at line 179, not here, and it is
                    // the kind that quietly disappears — build these from two Room queries
                    // instead of one partition and every device in both lists collides. #116 was
                    // exactly this: two items() in one LazyColumn keyed on raw ids that were
                    // disjoint right up until they weren't.
                    if (newDevices.isNotEmpty()) {
                        item { SectionHeader(stringResource(R.string.devices_section_new)) }
                        items(newDevices, key = { "new_${it.id}" }) { DeviceRow(it) { onSelectDevice(it.id) } }
                    }
                    if (knownDevices.isNotEmpty()) {
                        item { SectionHeader(stringResource(R.string.devices_section_known)) }
                        items(knownDevices, key = { "known_${it.id}" }) { DeviceRow(it) { onSelectDevice(it.id) } }
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TagFilterRow(
    availableTags: List<String>,
    activeTags: Set<String>,
    onToggleTag: (String) -> Unit,
    onClearTags: () -> Unit,
) {
    if (availableTags.isEmpty()) return
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stringResource(R.string.devices_filter_by_tag),
                style = MaterialTheme.typography.labelMedium,
            )
            if (activeTags.isNotEmpty()) {
                TextButton(onClick = onClearTags) {
                    Text(stringResource(R.string.devices_filter_clear))
                }
            }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            availableTags.forEach { tag ->
                FilterChip(
                    selected = activeTags.any { it.equals(tag, ignoreCase = true) },
                    onClick = { onToggleTag(tag) },
                    label = { Text(tag) },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun DeviceRow(device: KnownDeviceEntity, onClick: () -> Unit) {
    val tags = remember(device.tags) { DeviceTags.parse(device.tags) }
    ListItem(
        headlineContent = { Text(device.displayName()) },
        supportingContent = {
            Column {
                Text(
                    "${device.ip}  ·  ${device.macAddress ?: stringResource(R.string.devices_mac_unknown)}",
                    style = MaterialTheme.typography.labelSmall,
                )
                device.location?.let { location ->
                    Text(
                        stringResource(R.string.devices_row_location, location),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                if (tags.isNotEmpty()) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.padding(top = 4.dp),
                    ) {
                        tags.forEach { tag ->
                            StampChip(text = tag)
                        }
                    }
                }
            }
        },
        modifier = Modifier.clickable(onClick = onClick).fillMaxWidth().padding(horizontal = 4.dp),
    )
    HorizontalDivider()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WatchSection(
    isPro: Boolean,
    watchedNetworks: List<WatchedNetworkEntity>,
    cadence: WatchCadence,
    masterEnabled: Boolean,
    onWatchThisNetwork: () -> Unit,
    onToggleNetwork: (Long, Boolean) -> Unit,
    onRemoveNetwork: (Long) -> Unit,
    onMasterToggle: (Boolean) -> Unit,
    onCadenceChange: (WatchCadence) -> Unit,
) {
    val context = LocalContext.current
    var notificationsEnabled by remember {
        mutableStateOf(NotificationManagerCompat.from(context).areNotificationsEnabled())
    }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        notificationsEnabled = NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    Column(
        Modifier.fillMaxWidth().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(stringResource(R.string.devices_watch_section), style = MaterialTheme.typography.titleMedium)
        if (!isPro) {
            Text(stringResource(R.string.devices_watch_pro_upsell), style = MaterialTheme.typography.bodyMedium)
            return@Column
        }
        if (!notificationsEnabled) {
            AssistChip(
                onClick = {
                    val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                        .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    try {
                        context.startActivity(intent)
                    } catch (e: ActivityNotFoundException) {
                        // AOSP forks without a notification settings activity (rare).
                        Log.w("DevicesScreen", "ACTION_APP_NOTIFICATION_SETTINGS not handled", e)
                        context.startActivity(
                            Intent(Settings.ACTION_SETTINGS)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                        )
                    }
                },
                label = { Text(stringResource(R.string.devices_watch_notif_prompt)) },
                leadingIcon = { Icon(Icons.Default.NotificationsOff, null) },
            )
        }
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(stringResource(R.string.devices_watch_master))
            Switch(checked = masterEnabled, onCheckedChange = onMasterToggle)
        }
        Text(stringResource(R.string.devices_watch_cadence), style = MaterialTheme.typography.labelMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            WatchCadence.entries.forEach { option ->
                FilterChip(
                    selected = option == cadence,
                    onClick = { onCadenceChange(option) },
                    label = { Text(cadenceLabel(option)) },
                )
            }
        }
        OutlinedButton(onClick = onWatchThisNetwork, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.devices_watch_this_network))
        }
        watchedNetworks.forEach { network ->
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(network.displayName ?: network.gatewayMac, style = MaterialTheme.typography.labelSmall)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(
                        checked = network.watchEnabled,
                        onCheckedChange = { onToggleNetwork(network.id, it) },
                    )
                    TextButton(onClick = { onRemoveNetwork(network.id) }) {
                        Text(stringResource(R.string.devices_watch_remove))
                    }
                }
            }
        }
    }
    HorizontalDivider()
}

@Composable
private fun cadenceLabel(cadence: WatchCadence): String =
    when (cadence) {
        WatchCadence.FIFTEEN_MIN -> stringResource(R.string.devices_watch_cadence_15)
        WatchCadence.THIRTY_MIN -> stringResource(R.string.devices_watch_cadence_30)
        WatchCadence.ONE_HOUR -> stringResource(R.string.devices_watch_cadence_60)
        WatchCadence.SIX_HOURS -> stringResource(R.string.devices_watch_cadence_360)
    }
