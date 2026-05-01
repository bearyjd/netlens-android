package com.ventoux.netlens.feature.ipinfo

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ventoux.netlens.core.billing.LocalProStatus
import com.ventoux.netlens.core.network.export.ResultExporter
import com.ventoux.netlens.feature.ipinfo.model.IpApiResponse
import com.ventoux.netlens.feature.ipinfo.model.IpInfoUiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IpInfoScreen(
    onBack: () -> Unit = {},
    onNavigateToTool: (String, String) -> Unit = { _, _ -> },
    viewModel: IpInfoViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val proStatus = LocalProStatus.current
    val isPro by proStatus.isPro.collectAsStateWithLifecycle()

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(stringResource(R.string.ipinfo_title)) },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                    )
                }
            },
            actions = {
                if (uiState is IpInfoUiState.Success) {
                    IconButton(onClick = {
                        ResultExporter.copyToClipboard(context, "IP Info", viewModel.buildExportText())
                    }) {
                        Icon(Icons.Default.ContentCopy, contentDescription = stringResource(R.string.ipinfo_cd_copy))
                    }
                    if (isPro) {
                        IconButton(onClick = {
                            ResultExporter.shareAsText(context, "IP Info Results", viewModel.buildExportText())
                        }) {
                            Icon(Icons.Default.Share, contentDescription = stringResource(R.string.ipinfo_cd_share))
                        }
                    }
                }
            },
        )

        PullToRefreshBox(
            isRefreshing = uiState is IpInfoUiState.Loading,
            onRefresh = viewModel::refresh,
            modifier = Modifier.fillMaxSize(),
        ) {
            when (val state = uiState) {
                is IpInfoUiState.Loading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator()
                    }
                }

                is IpInfoUiState.Error -> {
                    ErrorContent(
                        message = state.message,
                        onRetry = viewModel::refresh,
                    )
                }

                is IpInfoUiState.Success -> {
                    SuccessContent(data = state.data, onNavigateToTool = onNavigateToTool)
                }
            }
        }
    }
}

@Composable
private fun ErrorContent(
    message: String,
    onRetry: () -> Unit,
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.error,
            )
            Button(onClick = onRetry) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = null,
                    modifier = Modifier.padding(end = 8.dp),
                )
                Text(stringResource(R.string.ipinfo_button_retry))
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SuccessContent(data: IpApiResponse, onNavigateToTool: (String, String) -> Unit) {
    val clipboardManager = LocalClipboardManager.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer,
            ),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text(
                        text = stringResource(R.string.ipinfo_label_public_ip),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                    )
                    Text(
                        text = data.query,
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
                IconButton(
                    onClick = {
                        clipboardManager.setText(AnnotatedString(data.query))
                    },
                ) {
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = stringResource(R.string.ipinfo_cd_copy_ip),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            }
        }

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            AssistChip(
                onClick = { onNavigateToTool("ping", data.query) },
                label = { Text(stringResource(R.string.ipinfo_action_ping)) },
            )
            AssistChip(
                onClick = { onNavigateToTool("traceroute", data.query) },
                label = { Text(stringResource(R.string.ipinfo_action_traceroute)) },
            )
            AssistChip(
                onClick = { onNavigateToTool("whois", data.query) },
                label = { Text(stringResource(R.string.ipinfo_action_whois)) },
            )
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                InfoRow(label = stringResource(R.string.ipinfo_label_isp), value = data.isp, onCopy = { clipboardManager.setText(AnnotatedString(data.isp)) })
                HorizontalDivider()
                InfoRow(label = stringResource(R.string.ipinfo_label_organization), value = data.org, onCopy = { clipboardManager.setText(AnnotatedString(data.org)) })
                HorizontalDivider()
                InfoRow(label = stringResource(R.string.ipinfo_label_as_number), value = data.asNumber, onCopy = { clipboardManager.setText(AnnotatedString(data.asNumber)) })
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                val coords = "${data.lat}, ${data.lon}"
                InfoRow(label = stringResource(R.string.ipinfo_label_country), value = data.country, onCopy = { clipboardManager.setText(AnnotatedString(data.country)) })
                HorizontalDivider()
                InfoRow(label = stringResource(R.string.ipinfo_label_region), value = data.regionName, onCopy = { clipboardManager.setText(AnnotatedString(data.regionName)) })
                HorizontalDivider()
                InfoRow(label = stringResource(R.string.ipinfo_label_city), value = data.city, onCopy = { clipboardManager.setText(AnnotatedString(data.city)) })
                HorizontalDivider()
                InfoRow(label = stringResource(R.string.ipinfo_label_coordinates), value = coords, onCopy = { clipboardManager.setText(AnnotatedString(coords)) })
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                InfoRow(
                    label = stringResource(R.string.ipinfo_label_proxy_vpn),
                    value = if (data.proxy) stringResource(R.string.ipinfo_value_detected) else stringResource(R.string.ipinfo_value_not_detected),
                )
                HorizontalDivider()
                InfoRow(
                    label = stringResource(R.string.ipinfo_label_hosting),
                    value = if (data.hosting) stringResource(R.string.ipinfo_value_yes) else stringResource(R.string.ipinfo_value_no),
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun InfoRow(label: String, value: String, onCopy: (() -> Unit)? = null) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (onCopy != null) {
                IconButton(onClick = onCopy) {
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = stringResource(R.string.ipinfo_cd_copy_field, label),
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
    }
}
