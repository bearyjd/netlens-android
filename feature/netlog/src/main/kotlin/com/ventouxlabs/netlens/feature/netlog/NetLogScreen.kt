package com.ventouxlabs.netlens.feature.netlog

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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDateRangePickerState
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
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ventouxlabs.netlens.core.data.model.NetworkEvent
import com.ventouxlabs.netlens.core.data.model.NetworkEventType
import com.ventouxlabs.netlens.core.network.export.ResultExporter
import com.ventouxlabs.netlens.core.ui.resolve
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NetLogScreen(
    onBack: () -> Unit = {},
    viewModel: NetLogViewModel = hiltViewModel(),
) {
    val uiState by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    var showDatePicker by rememberSaveable { mutableStateOf(false) }
    val errorMessage = uiState.error?.resolve()

    LaunchedEffect(errorMessage) {
        errorMessage?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.clearError()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.netlog_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.navigate_back),
                        )
                    }
                },
                actions = {
                    if (uiState.events.isNotEmpty()) {
                        val exportTitle = stringResource(R.string.netlog_export_title)
                        IconButton(onClick = {
                            ResultExporter.shareAsText(
                                context,
                                exportTitle,
                                viewModel.buildExportJson(),
                            )
                        }) {
                            Icon(
                                imageVector = Icons.Default.Share,
                                contentDescription = stringResource(R.string.netlog_cd_export),
                            )
                        }
                    }
                    IconButton(
                        onClick = {
                            if (uiState.isMonitoring) viewModel.stopMonitoring()
                            else viewModel.startMonitoring()
                        },
                    ) {
                        Icon(
                            imageVector = if (uiState.isMonitoring) Icons.Default.Close
                            else Icons.Default.PlayArrow,
                            contentDescription = stringResource(
                                if (uiState.isMonitoring) R.string.netlog_cd_stop_monitoring
                                else R.string.netlog_cd_start_monitoring,
                            ),
                        )
                    }
                    if (uiState.events.isNotEmpty()) {
                        IconButton(onClick = viewModel::showClearConfirmation) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = stringResource(R.string.netlog_cd_clear),
                            )
                        }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            FilterBar(
                selectedTypes = uiState.selectedEventTypes,
                hasDateRange = uiState.dateRangeStartMs != null,
                onToggleType = viewModel::toggleEventTypeFilter,
                onDateRangeClick = { showDatePicker = true },
                onClearFilters = viewModel::clearFilters,
            )

            if (uiState.events.isEmpty()) {
                EmptyState(
                    isMonitoring = uiState.isMonitoring,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                EventTimeline(
                    events = uiState.events,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }

    if (uiState.showClearConfirmation) {
        AlertDialog(
            onDismissRequest = viewModel::hideClearConfirmation,
            title = { Text(stringResource(R.string.netlog_dialog_title)) },
            text = { Text(stringResource(R.string.netlog_dialog_message)) },
            confirmButton = {
                TextButton(onClick = viewModel::clearHistory) {
                    Text(stringResource(R.string.netlog_dialog_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::hideClearConfirmation) {
                    Text(stringResource(R.string.netlog_dialog_cancel))
                }
            },
        )
    }

    if (showDatePicker) {
        DateRangePickerDialog(
            onConfirm = { startMs, endMs ->
                viewModel.setDateRange(startMs, endMs)
                showDatePicker = false
            },
            onDismiss = { showDatePicker = false },
        )
    }
}

@Composable
private fun FilterBar(
    selectedTypes: Set<String>,
    hasDateRange: Boolean,
    onToggleType: (String) -> Unit,
    onDateRangeClick: () -> Unit,
    onClearFilters: () -> Unit,
) {
    val hasFilters = selectedTypes.isNotEmpty() || hasDateRange

    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            item {
                FilterChip(
                    selected = hasDateRange,
                    onClick = onDateRangeClick,
                    label = { Text(stringResource(R.string.netlog_filter_date)) },
                    leadingIcon = {
                        Icon(
                            Icons.Default.DateRange,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                    },
                )
            }
            items(FILTER_EVENT_TYPES) { type ->
                FilterChip(
                    selected = type in selectedTypes,
                    onClick = { onToggleType(type) },
                    label = { Text(eventTypeLabel(type)) },
                )
            }
            if (hasFilters) {
                item {
                    FilterChip(
                        selected = false,
                        onClick = onClearFilters,
                        label = { Text(stringResource(R.string.netlog_filter_clear)) },
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
    }
}

@Composable
private fun EventTimeline(
    events: List<NetworkEvent>,
    modifier: Modifier = Modifier,
) {
    val todayLabel = stringResource(R.string.netlog_date_today)
    val yesterdayLabel = stringResource(R.string.netlog_date_yesterday)
    val grouped = remember(events, todayLabel, yesterdayLabel) {
        groupByDate(events, todayLabel, yesterdayLabel)
    }

    LazyColumn(
        modifier = modifier.padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        for ((dateLabel, dayEvents) in grouped) {
            item(key = "header_$dateLabel") {
                DateSeparator(label = dateLabel)
            }
            items(
                items = dayEvents,
                key = { it.id },
            ) { event ->
                NetworkEventCard(event = event)
            }
        }
        item { Spacer(modifier = Modifier.height(16.dp)) }
    }
}

@Composable
private fun DateSeparator(label: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            color = MaterialTheme.colorScheme.outlineVariant,
            modifier = Modifier
                .weight(1f)
                .height(1.dp),
        ) {}
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 12.dp),
        )
        Surface(
            color = MaterialTheme.colorScheme.outlineVariant,
            modifier = Modifier
                .weight(1f)
                .height(1.dp),
        ) {}
    }
}

@Composable
private fun NetworkEventCard(event: NetworkEvent) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                EventTypeBadge(eventType = event.eventType)
                Text(
                    text = formatTime(event.timestamp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = event.transportType,
                    style = MaterialTheme.typography.titleSmall,
                )
                if (event.isVpn) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = stringResource(R.string.netlog_cd_vpn_active),
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            if (event.networkDetails.isNotBlank()) {
                Text(
                    text = event.networkDetails,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun EventTypeBadge(eventType: String) {
    val color = when (eventType) {
        NetworkEventType.CONNECTED -> MaterialTheme.colorScheme.primary
        NetworkEventType.DISCONNECTED -> MaterialTheme.colorScheme.error
        NetworkEventType.CHANGED -> MaterialTheme.colorScheme.tertiary
        NetworkEventType.DNS_CHANGE -> MaterialTheme.colorScheme.secondary
        NetworkEventType.SPEED_TEST -> MaterialTheme.colorScheme.primary
        NetworkEventType.SECURITY_AUDIT -> MaterialTheme.colorScheme.error
        NetworkEventType.SCORE_CHANGE -> MaterialTheme.colorScheme.tertiary
        NetworkEventType.NEW_DEVICE -> MaterialTheme.colorScheme.secondary
        else -> MaterialTheme.colorScheme.outline
    }
    Surface(
        color = color.copy(alpha = 0.15f),
        shape = MaterialTheme.shapes.extraSmall,
    ) {
        Text(
            text = eventTypeLabel(eventType),
            style = MaterialTheme.typography.labelSmall,
            color = color,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateRangePickerDialog(
    onConfirm: (Long?, Long?) -> Unit,
    onDismiss: () -> Unit,
) {
    val state = rememberDateRangePickerState()

    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = { onConfirm(state.selectedStartDateMillis, state.selectedEndDateMillis) }) {
                Text(stringResource(R.string.netlog_date_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.netlog_date_cancel))
            }
        },
    ) {
        DateRangePicker(
            state = state,
            modifier = Modifier.height(500.dp),
        )
    }
}

@Composable
private fun EmptyState(isMonitoring: Boolean, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = stringResource(R.string.netlog_empty_title),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(
                if (isMonitoring) R.string.netlog_empty_monitoring
                else R.string.netlog_empty_paused,
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private val FILTER_EVENT_TYPES = listOf(
    NetworkEventType.CONNECTED,
    NetworkEventType.DISCONNECTED,
    NetworkEventType.CHANGED,
    NetworkEventType.DNS_CHANGE,
    NetworkEventType.SPEED_TEST,
    NetworkEventType.SECURITY_AUDIT,
    NetworkEventType.SCORE_CHANGE,
    NetworkEventType.NEW_DEVICE,
)

@Composable
private fun eventTypeLabel(type: String): String = when (type) {
    NetworkEventType.CONNECTED -> stringResource(R.string.netlog_event_connected)
    NetworkEventType.DISCONNECTED -> stringResource(R.string.netlog_event_disconnected)
    NetworkEventType.CHANGED -> stringResource(R.string.netlog_event_changed)
    NetworkEventType.DNS_CHANGE -> stringResource(R.string.netlog_event_dns_change)
    NetworkEventType.SPEED_TEST -> stringResource(R.string.netlog_event_speed_test)
    NetworkEventType.SECURITY_AUDIT -> stringResource(R.string.netlog_event_security_audit)
    NetworkEventType.SCORE_CHANGE -> stringResource(R.string.netlog_event_score_change)
    NetworkEventType.NEW_DEVICE -> stringResource(R.string.netlog_event_new_device)
    else -> type
}

private val DATE_FORMATTER: DateTimeFormatter =
    DateTimeFormatter.ofPattern("EEEE, MMMM d", Locale.getDefault())
        .withZone(ZoneId.systemDefault())

private val TIME_FORMATTER: DateTimeFormatter =
    DateTimeFormatter.ofPattern("HH:mm:ss", Locale.getDefault())
        .withZone(ZoneId.systemDefault())

private fun formatTime(timestamp: Long): String =
    TIME_FORMATTER.format(Instant.ofEpochMilli(timestamp))

private fun groupByDate(
    events: List<NetworkEvent>,
    todayLabel: String,
    yesterdayLabel: String,
): List<Pair<String, List<NetworkEvent>>> {
    val zone = ZoneId.systemDefault()
    val today = LocalDate.now(zone)
    val yesterday = today.minusDays(1)

    return events.groupBy { event ->
        Instant.ofEpochMilli(event.timestamp).atZone(zone).toLocalDate()
    }.toSortedMap(compareByDescending { it })
        .map { (date, dayEvents) ->
            val label = when (date) {
                today -> todayLabel
                yesterday -> yesterdayLabel
                else -> DATE_FORMATTER.format(date.atStartOfDay(zone))
            }
            label to dayEvents
        }
}
