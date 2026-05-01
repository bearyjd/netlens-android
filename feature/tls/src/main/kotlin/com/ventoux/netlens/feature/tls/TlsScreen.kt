package com.ventoux.netlens.feature.tls

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.ui.res.stringResource
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.ventoux.netlens.core.network.export.ResultExporter
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ventoux.netlens.feature.tls.model.TlsCertInfo
import com.ventoux.netlens.feature.tls.model.TlsInspectResult
import com.ventoux.netlens.feature.tls.model.TlsUiState

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TlsScreen(
    onBack: () -> Unit = {},
    initialHost: String? = null,
    onNavigateToTool: (String, String) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier,
    viewModel: TlsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var host by rememberSaveable { mutableStateOf(initialHost ?: "") }
    var portText by rememberSaveable { mutableStateOf("443") }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("TLS Inspector") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                        )
                    }
                },
                actions = {
                    if (uiState is TlsUiState.Success) {
                        IconButton(onClick = {
                            ResultExporter.copyToClipboard(context, "TLS Inspector", viewModel.buildExportText())
                        }) {
                            Icon(Icons.Default.ContentCopy, contentDescription = stringResource(R.string.tls_cd_copy_results))
                        }
                        IconButton(onClick = {
                            ResultExporter.shareAsText(context, "TLS Inspector Results", viewModel.buildExportText())
                        }) {
                            Icon(Icons.Default.Share, contentDescription = stringResource(R.string.tls_cd_share))
                        }
                    }
                },
            )
        },
    ) { innerPadding ->
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        OutlinedTextField(
            value = host,
            onValueChange = { host = it },
            label = { Text("Host") },
            placeholder = { Text("e.g. example.com") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedTextField(
                value = portText,
                onValueChange = { portText = it.filter { c -> c.isDigit() } },
                label = { Text("Port") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.width(120.dp),
            )

            Button(
                onClick = {
                    val port = portText.toIntOrNull() ?: 443
                    viewModel.inspect(host.trim(), port)
                },
                enabled = host.isNotBlank() && uiState !is TlsUiState.Loading,
            ) {
                Text("Inspect")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        when (val state = uiState) {
            is TlsUiState.Idle -> { /* empty */ }

            is TlsUiState.Loading -> {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 32.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            }

            is TlsUiState.Error -> {
                Text(
                    text = state.message,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            is TlsUiState.Success -> {
                ResultContent(result = state.result)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    AssistChip(
                        onClick = { onNavigateToTool("whois", host.trim()) },
                        label = { Text(stringResource(R.string.tls_action_whois)) },
                    )
                    AssistChip(
                        onClick = { onNavigateToTool("httptester", "https://${host.trim()}") },
                        label = { Text(stringResource(R.string.tls_action_http_test)) },
                    )
                }
            }
        }
    }
    }
}

@Composable
private fun ResultContent(result: TlsInspectResult) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
        ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            InfoRow(label = "Protocol", value = result.protocol)
            HorizontalDivider()
            InfoRow(label = "Cipher Suite", value = result.cipherSuite)
        }
    }

    Spacer(modifier = Modifier.height(12.dp))

    Text(
        text = "Certificate Chain (${result.certificates.size})",
        style = MaterialTheme.typography.titleSmall,
        modifier = Modifier.padding(bottom = 8.dp),
    )

    result.certificates.forEachIndexed { index, cert ->
        CertificateCard(
            cert = cert,
            index = index,
        )
        if (index < result.certificates.lastIndex) {
            Spacer(modifier = Modifier.height(8.dp))
        }
    }

    Spacer(modifier = Modifier.height(16.dp))
}

@Composable
private fun CertificateCard(
    cert: TlsCertInfo,
    index: Int,
) {
    var expanded by rememberSaveable { mutableStateOf(index == 0) }
    val expiryColor = when {
        cert.isExpired -> MaterialTheme.colorScheme.error
        cert.daysUntilExpiry < 30 -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.primary
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded },
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = cert.subjectCN,
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Text(
                        text = when {
                            cert.isExpired -> "Expired"
                            cert.daysUntilExpiry < 30 -> "${cert.daysUntilExpiry}d until expiry"
                            else -> "${cert.daysUntilExpiry}d remaining"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = expiryColor,
                    )
                }
                Text(
                    text = if (expanded) "▲" else "▼",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(),
                exit = shrinkVertically(),
            ) {
                Column(
                    modifier = Modifier.padding(top = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    HorizontalDivider()
                    InfoRow(label = "Subject", value = cert.subjectCN)
                    InfoRow(label = "Issuer", value = cert.issuerCN)
                    InfoRow(label = "Serial", value = cert.serialNumber)
                    InfoRow(label = "Valid From", value = cert.notBefore)
                    InfoRow(label = "Valid Until", value = cert.notAfter)
                    InfoRow(label = "Algorithm", value = cert.signatureAlgorithm)
                }
            }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}
