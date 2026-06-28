package com.ventouxlabs.netlens.feature.monitor

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ventouxlabs.netlens.core.data.model.EndpointCheck
import com.ventouxlabs.netlens.core.data.model.MonitoredEndpoint
import com.ventouxlabs.netlens.feature.monitor.model.MonitorUiState

@Composable
fun MonitorScreen(
    onBack: () -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: MonitorViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    MonitorContent(
        state = state,
        onAddEndpoint = viewModel::addEndpoint,
        onRemoveEndpoint = viewModel::removeEndpoint,
        onSelectEndpoint = viewModel::selectEndpoint,
        onDeselectEndpoint = viewModel::deselectEndpoint,
        onCheckNow = viewModel::checkNow,
        onDismissError = viewModel::dismissError,
        onBack = onBack,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MonitorContent(
    state: MonitorUiState,
    onAddEndpoint: (String, String, Int) -> Unit,
    onRemoveEndpoint: (MonitoredEndpoint) -> Unit,
    onSelectEndpoint: (MonitoredEndpoint) -> Unit,
    onDeselectEndpoint: () -> Unit,
    onDismissError: () -> Unit,
    onCheckNow: (MonitoredEndpoint) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showAddDialog by rememberSaveable { mutableStateOf(false) }

    state.error?.let { errorMessage ->
        AlertDialog(
            onDismissRequest = onDismissError,
            title = { Text(stringResource(R.string.monitor_error_title)) },
            text = { Text(errorMessage) },
            confirmButton = {
                TextButton(onClick = onDismissError) {
                    Text(stringResource(R.string.monitor_error_ok))
                }
            },
        )
    }

    if (state.selectedEndpoint != null) {
        EndpointDetailView(
            endpoint = state.selectedEndpoint,
            checks = state.checks,
            isChecking = state.isChecking,
            onBack = onDeselectEndpoint,
            onCheckNow = { onCheckNow(state.selectedEndpoint) },
            modifier = modifier,
        )
    } else {
        Scaffold(
            modifier = modifier,
            topBar = {
                TopAppBar(
                    title = { Text(stringResource(R.string.monitor_title)) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.navigate_back),
                            )
                        }
                    },
                )
            },
            floatingActionButton = {
                FloatingActionButton(onClick = { showAddDialog = true }) {
                    Icon(Icons.Default.Add, contentDescription = stringResource(R.string.monitor_cd_add))
                }
            },
        ) { padding ->
            if (state.endpoints.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(R.string.monitor_empty),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(state.endpoints, key = { it.id }) { endpoint ->
                        SwipeToDeleteEndpointCard(
                            endpoint = endpoint,
                            onSelect = { onSelectEndpoint(endpoint) },
                            onDelete = { onRemoveEndpoint(endpoint) },
                            onCheckNow = { onCheckNow(endpoint) },
                        )
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        AddEndpointDialog(
            onDismiss = { showAddDialog = false },
            onConfirm = { label, url ->
                onAddEndpoint(label, url, 60)
                showAddDialog = false
            },
        )
    }
}

@Composable
private fun SwipeToDeleteEndpointCard(
    endpoint: MonitoredEndpoint,
    onSelect: () -> Unit,
    onDelete: () -> Unit,
    onCheckNow: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val dismissState = rememberSwipeToDismissBoxState()
    var deleted by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(dismissState.currentValue) {
        if (dismissState.currentValue == SwipeToDismissBoxValue.EndToStart && !deleted) {
            deleted = true
            onDelete()
        }
    }

    SwipeToDismissBox(
        state = dismissState,
        modifier = modifier,
        backgroundContent = {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.error)
                    .padding(horizontal = 20.dp),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = stringResource(R.string.monitor_cd_delete),
                    tint = MaterialTheme.colorScheme.onError,
                )
            }
        },
        enableDismissFromStartToEnd = false,
    ) {
        EndpointCard(
            endpoint = endpoint,
            onSelect = onSelect,
            onCheckNow = onCheckNow,
        )
    }
}

@Composable
private fun EndpointCard(
    endpoint: MonitoredEndpoint,
    onSelect: () -> Unit,
    onCheckNow: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onSelect),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(
                        if (endpoint.isActive) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.error
                        },
                    ),
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = endpoint.label,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = endpoint.url,
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = MaterialTheme.typography.labelSmall.fontFamily),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            IconButton(onClick = onCheckNow) {
                Icon(
                    Icons.Default.Refresh,
                    contentDescription = stringResource(R.string.monitor_cd_check_now),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

@Composable
private fun AddEndpointDialog(
    onDismiss: () -> Unit,
    onConfirm: (String, String) -> Unit,
) {
    var label by rememberSaveable { mutableStateOf("") }
    var url by rememberSaveable { mutableStateOf("https://") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.monitor_dialog_add_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    label = { Text(stringResource(R.string.monitor_dialog_label)) },
                    placeholder = { Text(stringResource(R.string.monitor_dialog_label_hint)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text(stringResource(R.string.monitor_dialog_url)) },
                    placeholder = { Text(stringResource(R.string.monitor_dialog_url_hint)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(label, url) },
                enabled = label.isNotBlank() && url.isNotBlank(),
            ) {
                Text(stringResource(R.string.monitor_dialog_add))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.monitor_dialog_cancel))
            }
        },
    )
}

@Composable
private fun EndpointDetailView(
    endpoint: MonitoredEndpoint,
    checks: List<EndpointCheck>,
    isChecking: Boolean,
    onBack: () -> Unit,
    onCheckNow: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.navigate_back))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = endpoint.label,
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = endpoint.url,
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = MaterialTheme.typography.labelSmall.fontFamily),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (isChecking) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp))
            } else {
                IconButton(onClick = onCheckNow) {
                    Icon(
                        Icons.Default.Refresh,
                        contentDescription = stringResource(R.string.monitor_cd_check_now),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (checks.isNotEmpty()) {
            LatencyChart(
                checks = checks,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(100.dp),
            )
            Spacer(modifier = Modifier.height(16.dp))
        }

        Text(
            text = stringResource(R.string.monitor_detail_check_history),
            style = MaterialTheme.typography.titleSmall,
        )
        Spacer(modifier = Modifier.height(8.dp))

        if (checks.isEmpty()) {
            Text(
                text = stringResource(R.string.monitor_detail_no_checks),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                items(checks, key = { it.id }) { check ->
                    CheckRow(check = check)
                }
            }
        }
    }
}

@Composable
private fun LatencyChart(
    checks: List<EndpointCheck>,
    modifier: Modifier = Modifier,
) {
    val successColor = MaterialTheme.colorScheme.primary
    val errorColor = MaterialTheme.colorScheme.error
    val reversed = remember(checks) { checks.reversed() }

    Canvas(modifier = modifier) {
        val maxLatency = reversed.maxOfOrNull { it.latencyMs }?.toFloat() ?: return@Canvas
        if (maxLatency <= 0f) return@Canvas

        val barWidth = size.width / reversed.size.coerceAtLeast(1)
        val padding = 2.dp.toPx()

        reversed.forEachIndexed { index, check ->
            val barHeight = (check.latencyMs.toFloat() / maxLatency) * size.height
            val color = if (check.isSuccess) successColor else errorColor

            drawRect(
                color = color,
                topLeft = Offset(
                    x = index * barWidth + padding,
                    y = size.height - barHeight,
                ),
                size = androidx.compose.ui.geometry.Size(
                    width = (barWidth - padding * 2).coerceAtLeast(1f),
                    height = barHeight,
                ),
            )
        }
    }
}

@Composable
private fun CheckRow(
    check: EndpointCheck,
    modifier: Modifier = Modifier,
) {
    val statusColor by animateColorAsState(
        targetValue = if (check.isSuccess) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
        label = "statusColor",
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(statusColor),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = if (check.statusCode > 0) "${check.statusCode}" else stringResource(R.string.monitor_status_error),
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = MaterialTheme.typography.labelSmall.fontFamily),
                color = statusColor,
            )
        }

        Text(
            text = "${check.latencyMs} ms",
            style = MaterialTheme.typography.bodySmall.copy(fontFamily = MaterialTheme.typography.labelSmall.fontFamily),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        check.errorMessage?.let { msg ->
            Text(
                text = msg,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
        }
    }
}
