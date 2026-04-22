package us.beary.netlens.feature.httptester

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import us.beary.netlens.feature.httptester.model.HttpMethod
import us.beary.netlens.feature.httptester.model.HttpRequestConfig
import us.beary.netlens.feature.httptester.model.HttpResponseResult
import us.beary.netlens.feature.httptester.model.HttpTesterUiState

@Composable
fun HttpTesterScreen(
    modifier: Modifier = Modifier,
    viewModel: HttpTesterViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    HttpTesterContent(
        state = state,
        onSendRequest = viewModel::sendRequest,
        modifier = modifier,
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HttpTesterContent(
    state: HttpTesterUiState,
    onSendRequest: (HttpRequestConfig) -> Unit,
    modifier: Modifier = Modifier,
) {
    var url by rememberSaveable { mutableStateOf("") }
    var selectedMethod by rememberSaveable { mutableStateOf(HttpMethod.GET) }
    var body by rememberSaveable { mutableStateOf("") }
    val headerKeys = remember { mutableStateListOf("") }
    val headerValues = remember { mutableStateListOf("") }
    var headersExpanded by rememberSaveable { mutableStateOf(false) }
    var responseHeadersExpanded by rememberSaveable { mutableStateOf(false) }

    val isLoading = state is HttpTesterUiState.Loading
    val showBody = selectedMethod in listOf(HttpMethod.POST, HttpMethod.PUT, HttpMethod.PATCH)

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        OutlinedTextField(
            value = url,
            onValueChange = { url = it },
            label = { Text("URL") },
            placeholder = { Text("https://example.com/api") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(modifier = Modifier.height(12.dp))

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            HttpMethod.entries.forEach { method ->
                FilterChip(
                    selected = selectedMethod == method,
                    onClick = { selectedMethod = method },
                    label = { Text(method.label) },
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Headers section
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = "Headers",
                style = MaterialTheme.typography.titleSmall,
            )
            Spacer(modifier = Modifier.weight(1f))
            IconButton(
                onClick = {
                    headerKeys.add("")
                    headerValues.add("")
                    headersExpanded = true
                },
                modifier = Modifier.size(32.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Add header",
                    modifier = Modifier.size(18.dp),
                )
            }
        }

        if (headerKeys.size > 1 || headerKeys.firstOrNull()?.isNotEmpty() == true) {
            headersExpanded = true
        }

        AnimatedVisibility(visible = headersExpanded) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                headerKeys.indices.forEach { index ->
                    HeaderRow(
                        key = headerKeys[index],
                        value = headerValues[index],
                        onKeyChange = { headerKeys[index] = it },
                        onValueChange = { headerValues[index] = it },
                        onRemove = {
                            headerKeys.removeAt(index)
                            headerValues.removeAt(index)
                            if (headerKeys.isEmpty()) {
                                headerKeys.add("")
                                headerValues.add("")
                                headersExpanded = false
                            }
                        },
                    )
                }
            }
        }

        AnimatedVisibility(visible = showBody) {
            Column {
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = body,
                    onValueChange = { body = it },
                    label = { Text("Body") },
                    placeholder = { Text("{\"key\": \"value\"}") },
                    minLines = 3,
                    maxLines = 8,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = {
                val headers = headerKeys.zip(headerValues)
                    .filter { (k, v) -> k.isNotBlank() }
                    .associate { (k, v) -> k to v }
                onSendRequest(
                    HttpRequestConfig(
                        url = url,
                        method = selectedMethod,
                        headers = headers,
                        body = body.ifBlank { null },
                    ),
                )
            },
            enabled = url.isNotBlank() && !isLoading,
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
                Spacer(modifier = Modifier.width(8.dp))
            }
            Text("Send Request")
        }

        Spacer(modifier = Modifier.height(16.dp))

        when (state) {
            is HttpTesterUiState.Idle -> {}
            is HttpTesterUiState.Loading -> {}
            is HttpTesterUiState.Error -> {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                    ),
                ) {
                    Text(
                        text = state.message,
                        modifier = Modifier.padding(12.dp),
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
            is HttpTesterUiState.Success -> {
                ResponseSection(
                    response = state.response,
                    headersExpanded = responseHeadersExpanded,
                    onToggleHeaders = { responseHeadersExpanded = !responseHeadersExpanded },
                )
            }
        }
    }
}

@Composable
private fun HeaderRow(
    key: String,
    value: String,
    onKeyChange: (String) -> Unit,
    onValueChange: (String) -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedTextField(
            value = key,
            onValueChange = onKeyChange,
            placeholder = { Text("Key") },
            singleLine = true,
            modifier = Modifier.weight(1f),
            textStyle = MaterialTheme.typography.bodySmall,
        )
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            placeholder = { Text("Value") },
            singleLine = true,
            modifier = Modifier.weight(1f),
            textStyle = MaterialTheme.typography.bodySmall,
        )
        IconButton(
            onClick = onRemove,
            modifier = Modifier.size(32.dp),
        ) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Remove header",
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

@Composable
private fun ResponseSection(
    response: HttpResponseResult,
    headersExpanded: Boolean,
    onToggleHeaders: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val statusColor = when (response.statusCode) {
        in 200..299 -> MaterialTheme.colorScheme.primary
        in 300..399 -> MaterialTheme.colorScheme.tertiary
        in 400..499 -> MaterialTheme.colorScheme.error
        in 500..599 -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.onSurface
    }

    Column(modifier = modifier.fillMaxWidth()) {
        // Status line
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
            ),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "${response.statusCode} ${response.statusDescription}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = statusColor,
                )
                Text(
                    text = "${response.latencyMs} ms",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Response headers
        Button(
            onClick = onToggleHeaders,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = if (headersExpanded) "Hide Headers" else "Show Headers (${response.headers.size})",
            )
        }

        AnimatedVisibility(visible = headersExpanded) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                ),
            ) {
                Column(modifier = Modifier.padding(8.dp)) {
                    response.headers.forEach { (key, values) ->
                        Text(
                            text = "$key: ${values.joinToString(", ")}",
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = FontFamily.Monospace,
                            ),
                            modifier = Modifier.padding(vertical = 1.dp),
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Content length
        response.contentLength?.let { length ->
            Text(
                text = "Content-Length: $length bytes",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(4.dp))
        }

        // Response body
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
            ),
        ) {
            Text(
                text = response.body.ifEmpty { "(empty body)" },
                style = MaterialTheme.typography.bodySmall.copy(
                    fontFamily = FontFamily.Monospace,
                ),
                modifier = Modifier
                    .padding(8.dp)
                    .horizontalScroll(rememberScrollState()),
            )
        }
    }
}
