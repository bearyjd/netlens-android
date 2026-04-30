package com.ventoux.netlens.feature.dns

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.AssistChip
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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ventoux.netlens.feature.dns.model.DnsError
import com.ventoux.netlens.feature.dns.model.DnsLookupUiState
import com.ventoux.netlens.feature.dns.model.DnsRecordType
import com.ventoux.netlens.feature.dns.model.DnsResult

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DnsLookupScreen(
    onBack: () -> Unit = {},
    initialDomain: String? = null,
    viewModel: DnsLookupViewModel = hiltViewModel(),
    onNavigateToTool: (String, String) -> Unit = { _, _ -> },
) {
    LaunchedEffect(initialDomain) {
        if (initialDomain != null) viewModel.onDomainChanged(initialDomain)
    }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val clipboardManager = LocalClipboardManager.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.dns_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.navigate_back),
                        )
                    }
                },
                actions = {
                    if (state.results.isNotEmpty()) {
                        IconButton(
                            onClick = {
                                val text = state.results
                                    .groupBy { it.type }
                                    .entries
                                    .joinToString("\n\n") { (type, records) ->
                                        "${type.displayName} Records:\n" +
                                            records.joinToString("\n") { it.value }
                                    }
                                clipboardManager.setText(AnnotatedString(text))
                            },
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = stringResource(R.string.dns_cd_copy_all))
                        }
                    }
                },
            )
        },
    ) { padding ->
        DnsLookupContent(
            state = state,
            onDomainChanged = viewModel::onDomainChanged,
            onTypeToggled = viewModel::onTypeToggled,
            onLookup = viewModel::lookup,
            onNavigateToTool = onNavigateToTool,
            modifier = Modifier.padding(padding),
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DnsLookupContent(
    state: DnsLookupUiState,
    onDomainChanged: (String) -> Unit,
    onTypeToggled: (DnsRecordType) -> Unit,
    onLookup: () -> Unit,
    onNavigateToTool: (String, String) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = state.domain,
                onValueChange = onDomainChanged,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.dns_label_domain)) },
                placeholder = { Text(stringResource(R.string.dns_placeholder_domain)) },
                singleLine = true,
                trailingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = stringResource(R.string.dns_cd_lookup),
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
            Text(
                text = stringResource(R.string.dns_label_record_types),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(4.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                DnsRecordType.entries.forEach { type ->
                    FilterChip(
                        selected = type in state.selectedTypes,
                        onClick = { onTypeToggled(type) },
                        label = { Text(type.displayName) },
                    )
                }
            }
        }

        item {
            androidx.compose.material3.FilledTonalButton(
                onClick = onLookup,
                modifier = Modifier.fillMaxWidth(),
                enabled = !state.isLoading,
            ) {
                if (state.isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier
                            .height(18.dp)
                            .width(18.dp),
                        strokeWidth = 2.dp,
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text(stringResource(R.string.dns_button_lookup))
            }
        }

        state.error?.let { error ->
            item {
                Text(
                    text = when (error) {
                        is DnsError.EmptyDomain -> stringResource(R.string.dns_error_empty_domain)
                        is DnsError.NoTypes -> stringResource(R.string.dns_error_no_types)
                        is DnsError.LookupFailed -> error.message?.takeIf { it.isNotBlank() }
                            ?: stringResource(R.string.dns_error_lookup_failed)
                    },
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }

        val grouped = state.results.groupBy { it.type }
        var resultIndex = 0
        grouped.forEach { (type, records) ->
            item {
                Text(
                    text = stringResource(R.string.dns_records_header, type.displayName),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            val startIndex = resultIndex
            items(records.size, key = { i -> "dns:${startIndex + i}" }) { i ->
                DnsResultCard(result = records[i], onNavigateToTool = onNavigateToTool)
            }
            resultIndex += records.size
        }

        item { Spacer(modifier = Modifier.height(16.dp)) }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DnsResultCard(result: DnsResult, onNavigateToTool: (String, String) -> Unit) {
    val clipboardManager = LocalClipboardManager.current

    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = result.type.displayName,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = result.name,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = stringResource(R.string.dns_ttl_label, result.ttl),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = result.value,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontFamily = FontFamily.Monospace,
                    ),
                    modifier = Modifier.weight(1f),
                )
                IconButton(
                    onClick = { clipboardManager.setText(AnnotatedString(result.value)) },
                    modifier = Modifier.size(32.dp),
                ) {
                    Icon(
                        Icons.Default.ContentCopy,
                        contentDescription = stringResource(R.string.dns_cd_copy),
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
            if (result.type == DnsRecordType.A || result.type == DnsRecordType.AAAA) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(top = 4.dp),
                ) {
                    AssistChip(
                        onClick = { onNavigateToTool("ping", result.value) },
                        label = { Text(stringResource(R.string.dns_action_ping)) },
                    )
                    AssistChip(
                        onClick = { onNavigateToTool("portscan", result.value) },
                        label = { Text(stringResource(R.string.dns_action_port_scan)) },
                    )
                    AssistChip(
                        onClick = { onNavigateToTool("traceroute", result.value) },
                        label = { Text(stringResource(R.string.dns_action_traceroute)) },
                    )
                    AssistChip(
                        onClick = { onNavigateToTool("whois", result.value) },
                        label = { Text(stringResource(R.string.dns_action_whois)) },
                    )
                }
            }
        }
    }
}
