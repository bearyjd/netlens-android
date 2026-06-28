package com.ventouxlabs.netlens.feature.ipinfo

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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ventouxlabs.netlens.core.billing.LocalProStatus
import com.ventouxlabs.netlens.core.network.export.ResultExporter
import com.ventouxlabs.netlens.core.ui.LocalStatusColors
import com.ventouxlabs.netlens.feature.ipinfo.model.IpInfoResponse
import com.ventouxlabs.netlens.feature.ipinfo.model.IpInfoUiState
import com.ventouxlabs.netlens.feature.ipinfo.model.ReputationResult
import com.ventouxlabs.netlens.feature.ipinfo.model.ReputationRisk

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

    if (uiState is IpInfoUiState.ConsentRequired) {
        ConsentDialog(
            onAllow = viewModel::grantConsent,
            onDismiss = onBack,
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.ipinfo_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.navigate_back),
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
        },
    ) { innerPadding ->
        PullToRefreshBox(
            isRefreshing = uiState is IpInfoUiState.Loading,
            onRefresh = viewModel::refresh,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
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

                is IpInfoUiState.ConsentRequired -> {
                    Box(modifier = Modifier.fillMaxSize())
                }

                is IpInfoUiState.Error -> {
                    ErrorContent(
                        message = state.message,
                        onRetry = viewModel::refresh,
                    )
                }

                is IpInfoUiState.Success -> {
                    SuccessContent(
                        state = state,
                        onNavigateToTool = onNavigateToTool,
                        onSaveApiKey = viewModel::saveApiKey,
                    )
                }
            }
        }
    }
}

@Composable
private fun ConsentDialog(
    onAllow: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.ipinfo_consent_title)) },
        text = { Text(stringResource(R.string.ipinfo_consent_message)) },
        confirmButton = {
            TextButton(onClick = onAllow) {
                Text(stringResource(R.string.ipinfo_consent_allow))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.ipinfo_consent_cancel))
            }
        },
    )
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
private fun SuccessContent(
    state: IpInfoUiState.Success,
    onNavigateToTool: (String, String) -> Unit,
    onSaveApiKey: (String) -> Unit,
) {
    val data = state.data
    val clipboardManager = LocalClipboardManager.current
    var showApiKeyDialog by rememberSaveable { mutableStateOf(false) }

    if (showApiKeyDialog) {
        ApiKeyDialog(
            onSave = { key ->
                onSaveApiKey(key)
                showApiKeyDialog = false
            },
            onDismiss = { showApiKeyDialog = false },
        )
    }

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
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (data.countryFlag.isNotEmpty()) {
                            Text(
                                text = data.countryFlag,
                                style = MaterialTheme.typography.headlineMedium,
                                modifier = Modifier.padding(end = 8.dp),
                            )
                        }
                        Text(
                            text = data.ip,
                            style = MaterialTheme.typography.headlineMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                    if (data.hostname.isNotEmpty() && data.hostname != data.ip) {
                        Text(
                            text = data.hostname,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                        )
                    }
                }
                IconButton(
                    onClick = {
                        clipboardManager.setText(AnnotatedString(data.ip))
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
                onClick = { onNavigateToTool("ping", data.ip) },
                label = { Text(stringResource(R.string.ipinfo_action_ping)) },
            )
            AssistChip(
                onClick = { onNavigateToTool("traceroute", data.ip) },
                label = { Text(stringResource(R.string.ipinfo_action_traceroute)) },
            )
            AssistChip(
                onClick = { onNavigateToTool("whois", data.ip) },
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
                InfoRow(
                    label = stringResource(R.string.ipinfo_label_organization),
                    value = data.orgName.ifEmpty { stringResource(R.string.ipinfo_value_unknown) },
                    onCopy = { clipboardManager.setText(AnnotatedString(data.orgName)) },
                )
                HorizontalDivider()
                InfoRow(
                    label = stringResource(R.string.ipinfo_label_as_number),
                    value = data.asNumber.ifEmpty { stringResource(R.string.ipinfo_value_unknown) },
                    onCopy = if (data.asNumber.isNotEmpty()) {
                        { clipboardManager.setText(AnnotatedString(data.asNumber)) }
                    } else {
                        null
                    },
                )
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                val coords = "${data.latitude}, ${data.longitude}"
                InfoRow(
                    label = stringResource(R.string.ipinfo_label_country),
                    value = data.country.ifEmpty { stringResource(R.string.ipinfo_value_unknown) },
                    onCopy = { clipboardManager.setText(AnnotatedString(data.country)) },
                )
                HorizontalDivider()
                InfoRow(
                    label = stringResource(R.string.ipinfo_label_region),
                    value = data.region.ifEmpty { stringResource(R.string.ipinfo_value_unknown) },
                    onCopy = { clipboardManager.setText(AnnotatedString(data.region)) },
                )
                HorizontalDivider()
                InfoRow(
                    label = stringResource(R.string.ipinfo_label_city),
                    value = data.city.ifEmpty { stringResource(R.string.ipinfo_value_unknown) },
                    onCopy = { clipboardManager.setText(AnnotatedString(data.city)) },
                )
                HorizontalDivider()
                InfoRow(
                    label = stringResource(R.string.ipinfo_label_postal),
                    value = data.postal.ifEmpty { stringResource(R.string.ipinfo_value_unknown) },
                    onCopy = if (data.postal.isNotEmpty()) {
                        { clipboardManager.setText(AnnotatedString(data.postal)) }
                    } else {
                        null
                    },
                )
                HorizontalDivider()
                InfoRow(
                    label = stringResource(R.string.ipinfo_label_timezone),
                    value = data.timezone.ifEmpty { stringResource(R.string.ipinfo_value_unknown) },
                    onCopy = if (data.timezone.isNotEmpty()) {
                        { clipboardManager.setText(AnnotatedString(data.timezone)) }
                    } else {
                        null
                    },
                )
                HorizontalDivider()
                InfoRow(
                    label = stringResource(R.string.ipinfo_label_coordinates),
                    value = if (data.loc.isNotEmpty()) coords else stringResource(R.string.ipinfo_value_unknown),
                    onCopy = if (data.loc.isNotEmpty()) {
                        { clipboardManager.setText(AnnotatedString(coords)) }
                    } else {
                        null
                    },
                )
            }
        }

        ReputationCard(
            reputation = state.reputation,
            isLoading = state.reputationLoading,
            error = state.reputationError,
            onSetupApiKey = { showApiKeyDialog = true },
        )

        Text(
            text = stringResource(R.string.ipinfo_attribution),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )

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

@Composable
private fun ReputationCard(
    reputation: ReputationResult?,
    isLoading: Boolean,
    error: String?,
    onSetupApiKey: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(R.string.ipinfo_label_reputation),
                style = MaterialTheme.typography.titleSmall,
            )

            when {
                isLoading -> {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp))
                        Text(
                            text = stringResource(R.string.ipinfo_reputation_loading),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                reputation != null -> {
                    ReputationDetails(reputation = reputation, onChangeKey = onSetupApiKey)
                }

                error != null -> {
                    Text(
                        text = error,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                    OutlinedButton(onClick = onSetupApiKey) {
                        Text(stringResource(R.string.ipinfo_reputation_setup_button))
                    }
                }

                else -> {
                    Text(
                        text = stringResource(R.string.ipinfo_reputation_setup),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedButton(onClick = onSetupApiKey) {
                        Text(stringResource(R.string.ipinfo_reputation_setup_button))
                    }
                }
            }
        }
    }
}

@Composable
private fun ReputationDetails(
    reputation: ReputationResult,
    onChangeKey: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val status = LocalStatusColors.current
    val riskColor = when (reputation.riskLevel) {
        ReputationRisk.CLEAN -> status.pass
        ReputationRisk.LOW -> status.info
        ReputationRisk.MEDIUM -> status.warn
        ReputationRisk.HIGH -> status.fail
    }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.ipinfo_reputation_score),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "${reputation.abuseConfidenceScore}% — ${reputation.riskLevel.label}",
                style = MaterialTheme.typography.bodyMedium,
                color = riskColor,
            )
        }

        HorizontalDivider()

        InfoRow(
            label = stringResource(R.string.ipinfo_reputation_reports),
            value = reputation.totalReports.toString(),
        )

        if (reputation.isp.isNotEmpty()) {
            HorizontalDivider()
            InfoRow(
                label = stringResource(R.string.ipinfo_reputation_isp),
                value = reputation.isp,
            )
        }

        if (reputation.usageType.isNotEmpty()) {
            HorizontalDivider()
            InfoRow(
                label = stringResource(R.string.ipinfo_reputation_usage_type),
                value = reputation.usageType,
            )
        }

        if (reputation.domain.isNotEmpty()) {
            HorizontalDivider()
            InfoRow(
                label = stringResource(R.string.ipinfo_reputation_domain),
                value = reputation.domain,
            )
        }

        if (reputation.isWhitelisted) {
            HorizontalDivider()
            InfoRow(
                label = stringResource(R.string.ipinfo_reputation_whitelisted),
                value = "Yes",
            )
        }

        Spacer(modifier = Modifier.height(4.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.ipinfo_reputation_attribution),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TextButton(onClick = onChangeKey) {
                Text(
                    text = stringResource(R.string.ipinfo_reputation_setup_button),
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }
    }
}

@Composable
private fun ApiKeyDialog(
    onSave: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var key by rememberSaveable { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.ipinfo_apikey_dialog_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = stringResource(R.string.ipinfo_apikey_dialog_message),
                    style = MaterialTheme.typography.bodySmall,
                )
                OutlinedTextField(
                    value = key,
                    onValueChange = { key = it },
                    label = { Text(stringResource(R.string.ipinfo_apikey_dialog_hint)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(key) }, enabled = key.isNotBlank()) {
                Text(stringResource(R.string.ipinfo_apikey_dialog_save))
            }
        },
        dismissButton = {
            Row {
                TextButton(onClick = { onSave("") }) {
                    Text(stringResource(R.string.ipinfo_apikey_dialog_clear))
                }
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.ipinfo_apikey_dialog_cancel))
                }
            }
        },
    )
}
