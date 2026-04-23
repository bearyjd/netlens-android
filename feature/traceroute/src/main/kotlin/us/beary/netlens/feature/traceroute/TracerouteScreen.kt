package us.beary.netlens.feature.traceroute

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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import us.beary.netlens.feature.traceroute.model.TracerouteHop
import us.beary.netlens.feature.traceroute.model.TracerouteUiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TracerouteScreen(
    onBack: () -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: TracerouteViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val clipboardManager = LocalClipboardManager.current

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("Traceroute") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        TracerouteContent(
            state = state,
            onHostChange = viewModel::onHostChange,
            onStartTrace = viewModel::startTrace,
            onStopTrace = viewModel::stopTrace,
            onCopyResults = {
                clipboardManager.setText(AnnotatedString(viewModel.buildCopyText()))
            },
            modifier = Modifier.padding(innerPadding),
        )
    }
}

@Composable
private fun TracerouteContent(
    state: TracerouteUiState,
    onHostChange: (String) -> Unit,
    onStartTrace: (String) -> Unit,
    onStopTrace: () -> Unit,
    onCopyResults: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()

    LaunchedEffect(state.hops.size) {
        if (state.hops.isNotEmpty()) {
            listState.animateScrollToItem(state.hops.lastIndex)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = state.host,
                onValueChange = onHostChange,
                label = { Text("Host") },
                placeholder = { Text("e.g. google.com") },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            if (state.hops.isNotEmpty()) {
                IconButton(onClick = onCopyResults) {
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = "Copy results",
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = { onStartTrace(state.host) },
                enabled = state.host.isNotBlank() && !state.isTracing,
            ) {
                Text("Trace")
            }

            if (state.isTracing) {
                OutlinedButton(onClick = onStopTrace) {
                    Text("Stop")
                }
            }
        }

        if (state.isTracing) {
            Spacer(modifier = Modifier.height(8.dp))
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }

        state.error?.let { error ->
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = error,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            items(state.hops, key = { it.hopNumber }) { hop ->
                HopRow(hop = hop)
            }
        }
    }
}

@Composable
private fun HopRow(
    hop: TracerouteHop,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (hop.isTimeout) {
                MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            },
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "%2d".format(hop.hopNumber),
                style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                modifier = Modifier.width(28.dp),
            )

            Spacer(modifier = Modifier.width(12.dp))

            if (hop.isTimeout) {
                Text(
                    text = "*  *  *",
                    style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.weight(1f),
                )
            } else {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = hop.ip ?: "*",
                        style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                    )
                }

                hop.rttMs.firstOrNull()?.let { rtt ->
                    Text(
                        text = "%.1f ms".format(rtt),
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }
}
