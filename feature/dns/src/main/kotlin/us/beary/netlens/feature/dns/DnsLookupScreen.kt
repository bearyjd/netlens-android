package us.beary.netlens.feature.dns

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import us.beary.netlens.feature.dns.model.DnsLookupUiState
import us.beary.netlens.feature.dns.model.DnsRecordType
import us.beary.netlens.feature.dns.model.DnsResult

@Composable
fun DnsLookupScreen(
    viewModel: DnsLookupViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    DnsLookupContent(
        state = state,
        onDomainChanged = viewModel::onDomainChanged,
        onTypeToggled = viewModel::onTypeToggled,
        onLookup = viewModel::lookup,
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DnsLookupContent(
    state: DnsLookupUiState,
    onDomainChanged: (String) -> Unit,
    onTypeToggled: (DnsRecordType) -> Unit,
    onLookup: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier
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
                label = { Text("Domain") },
                placeholder = { Text("example.com") },
                singleLine = true,
                trailingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Lookup",
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
                text = "Record Types",
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
                Text("Lookup")
            }
        }

        state.error?.let { error ->
            item {
                Text(
                    text = error,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }

        val grouped = state.results.groupBy { it.type }
        grouped.forEach { (type, records) ->
            item {
                Text(
                    text = "${type.displayName} Records",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            items(records, key = { "${it.type}:${it.name}:${it.value}" }) { result ->
                DnsResultCard(result = result)
            }
        }

        item { Spacer(modifier = Modifier.height(16.dp)) }
    }
}

@Composable
private fun DnsResultCard(result: DnsResult) {
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
                    text = "TTL ${result.ttl}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = result.value,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontFamily = FontFamily.Monospace,
                ),
            )
        }
    }
}
