package com.ventouxlabs.netlens.feature.whois

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ventouxlabs.netlens.core.billing.LocalProStatus
import com.ventouxlabs.netlens.core.network.export.ResultExporter
import com.ventouxlabs.netlens.feature.whois.model.RdnsResult
import com.ventouxlabs.netlens.feature.whois.model.WhoisResult
import com.ventouxlabs.netlens.feature.whois.model.WhoisUiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WhoisScreen(
    onBack: () -> Unit = {},
    initialQuery: String? = null,
    onNavigateToTool: (String, String) -> Unit = { _, _ -> },
    viewModel: WhoisViewModel = hiltViewModel(),
) {
    LaunchedEffect(initialQuery) {
        if (initialQuery != null) viewModel.onQueryChanged(initialQuery)
    }
    val query by viewModel.query.collectAsStateWithLifecycle()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val proStatus = LocalProStatus.current
    val isPro by proStatus.isPro.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.whois_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.navigate_back),
                        )
                    }
                },
                actions = {
                    if (state is WhoisUiState.Success) {
                        IconButton(onClick = {
                            ResultExporter.copyToClipboard(context, "WHOIS", viewModel.buildExportText())
                        }) {
                            Icon(Icons.Default.ContentCopy, contentDescription = stringResource(R.string.whois_cd_copy_results))
                        }
                        if (isPro) {
                            IconButton(onClick = {
                                ResultExporter.shareAsText(context, "WHOIS Results", viewModel.buildExportText())
                            }) {
                                Icon(Icons.Default.Share, contentDescription = stringResource(R.string.whois_cd_share))
                            }
                        }
                    }
                },
            )
        },
    ) { innerPadding ->
        WhoisContent(
            query = query,
            state = state,
            onQueryChanged = viewModel::onQueryChanged,
            onLookup = { viewModel.lookup() },
            onNavigateToTool = onNavigateToTool,
            modifier = Modifier.padding(innerPadding),
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WhoisContent(
    query: String,
    state: WhoisUiState,
    onQueryChanged: (String) -> Unit,
    onLookup: () -> Unit,
    onNavigateToTool: (String, String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val isLoading = state is WhoisUiState.Loading

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = query,
                onValueChange = onQueryChanged,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.whois_label_query)) },
                placeholder = { Text(stringResource(R.string.whois_hint_query)) },
                singleLine = true,
                trailingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = stringResource(R.string.whois_cd_lookup),
                    )
                },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Uri,
                    imeAction = ImeAction.Search,
                ),
                keyboardActions = KeyboardActions(
                    onSearch = { onLookup() },
                ),
            )
        }

        item {
            Button(
                onClick = onLookup,
                modifier = Modifier.fillMaxWidth(),
                enabled = !isLoading,
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier
                            .height(18.dp)
                            .width(18.dp),
                        strokeWidth = 2.dp,
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text(stringResource(R.string.whois_button_lookup))
            }
        }

        when (state) {
            is WhoisUiState.Idle -> { /* nothing */ }
            is WhoisUiState.Loading -> { /* button already shows indicator */ }
            is WhoisUiState.Error -> {
                item {
                    Text(
                        text = state.message,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
            is WhoisUiState.Success -> {
                state.whois?.let { whois ->
                    item {
                        Text(
                            text = "WHOIS",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                    item { WhoisResultCard(whois = whois) }
                }
                state.rdns?.let { rdns ->
                    item {
                        Text(
                            text = "Reverse DNS",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                    item { RdnsResultCard(rdns = rdns) }
                }
                item {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        AssistChip(
                            onClick = { onNavigateToTool("ping", query.trim()) },
                            label = { Text(stringResource(R.string.whois_action_ping)) },
                        )
                        AssistChip(
                            onClick = { onNavigateToTool("dns", query.trim()) },
                            label = { Text(stringResource(R.string.whois_action_dns)) },
                        )
                        AssistChip(
                            onClick = { onNavigateToTool("tls", query.trim()) },
                            label = { Text(stringResource(R.string.whois_action_tls)) },
                        )
                    }
                }
            }
        }

        item { Spacer(modifier = Modifier.height(16.dp)) }
    }
}

@Composable
private fun WhoisResultCard(whois: WhoisResult) {
    var showRaw by rememberSaveable { mutableStateOf(false) }

    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            WhoisField(label = "Domain", value = whois.domain)
            whois.registrar?.let { WhoisField(label = "Registrar", value = it) }
            whois.createdDate?.let { WhoisField(label = "Created", value = it) }
            whois.expiryDate?.let { WhoisField(label = "Expires", value = it) }
            if (whois.nameServers.isNotEmpty()) {
                WhoisField(
                    label = "Name Servers",
                    value = whois.nameServers.joinToString("\n"),
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
            TextButton(onClick = { showRaw = !showRaw }) {
                Text(if (showRaw) "Hide Raw WHOIS" else "Show Raw WHOIS")
            }
            AnimatedVisibility(visible = showRaw) {
                Text(
                    text = whois.rawResponse,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = MaterialTheme.typography.labelSmall.fontFamily,
                    ),
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
}

@Composable
private fun RdnsResultCard(rdns: RdnsResult) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            WhoisField(label = "IP", value = rdns.ip)
            if (rdns.hostnames.isNotEmpty()) {
                WhoisField(
                    label = "Hostnames",
                    value = rdns.hostnames.joinToString("\n"),
                )
            } else {
                Text(
                    text = "No reverse DNS entry found",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun WhoisField(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.width(100.dp),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontFamily = MaterialTheme.typography.labelSmall.fontFamily,
            ),
            maxLines = if (value.contains("\n")) Int.MAX_VALUE else 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
    }
}
