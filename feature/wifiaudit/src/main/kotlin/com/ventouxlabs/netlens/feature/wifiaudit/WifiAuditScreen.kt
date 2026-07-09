package com.ventouxlabs.netlens.feature.wifiaudit

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
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ventouxlabs.netlens.core.ui.resolve
import com.ventouxlabs.netlens.feature.wifiaudit.model.AuditFinding
import com.ventouxlabs.netlens.feature.wifiaudit.model.AuditSeverity

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WifiAuditScreen(
    onBack: () -> Unit = {},
    viewModel: WifiAuditViewModel = hiltViewModel(),
) {
    val uiState by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
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
                title = { Text(stringResource(R.string.wifiaudit_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.navigate_back),
                        )
                    }
                },
                actions = {
                    IconButton(onClick = viewModel::runAudit) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = stringResource(R.string.wifiaudit_cd_rerun),
                        )
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
            if (uiState.isAuditing) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            uiState.ssid?.let { ssid ->
                Text(
                    text = stringResource(R.string.wifiaudit_network_label, ssid),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }

            val visible = uiState.findings.filter { it.id !in uiState.dismissedIds }

            if (visible.isEmpty() && !uiState.isAuditing && uiState.error == null) {
                EmptyAuditState(
                    hasRun = uiState.ssid != null,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                SummaryBar(findings = visible, modifier = Modifier.padding(horizontal = 16.dp))
                Spacer(modifier = Modifier.height(8.dp))
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(visible, key = { it.id }) { finding ->
                        FindingCard(
                            finding = finding,
                            onDismiss = { viewModel.dismissFinding(finding.id) },
                        )
                    }
                    item { Spacer(modifier = Modifier.height(16.dp)) }
                }
            }
        }
    }
}

@Composable
private fun SummaryBar(findings: List<AuditFinding>, modifier: Modifier = Modifier) {
    val critical = findings.count { it.severity == AuditSeverity.Critical }
    val warnings = findings.count { it.severity == AuditSeverity.Warning }
    val passed = findings.count { it.severity == AuditSeverity.Pass }

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (critical > 0) {
            SummaryChip(
                icon = Icons.Default.Error,
                label = stringResource(R.string.wifiaudit_summary_critical, critical),
                color = MaterialTheme.colorScheme.error,
            )
        }
        if (warnings > 0) {
            SummaryChip(
                icon = Icons.Default.Warning,
                label = pluralStringResource(R.plurals.wifiaudit_summary_warnings, warnings, warnings),
                color = MaterialTheme.colorScheme.tertiary,
            )
        }
        if (passed > 0) {
            SummaryChip(
                icon = Icons.Default.CheckCircle,
                label = stringResource(R.string.wifiaudit_summary_passed, passed),
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun SummaryChip(
    icon: ImageVector,
    label: String,
    color: androidx.compose.ui.graphics.Color,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = color,
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = color,
        )
    }
}

@Composable
private fun FindingCard(
    finding: AuditFinding,
    onDismiss: () -> Unit,
) {
    val containerColor = when (finding.severity) {
        AuditSeverity.Critical -> MaterialTheme.colorScheme.errorContainer
        AuditSeverity.Warning -> MaterialTheme.colorScheme.tertiaryContainer
        AuditSeverity.Info -> MaterialTheme.colorScheme.secondaryContainer
        AuditSeverity.Pass -> MaterialTheme.colorScheme.primaryContainer
    }
    val contentColor = when (finding.severity) {
        AuditSeverity.Critical -> MaterialTheme.colorScheme.onErrorContainer
        AuditSeverity.Warning -> MaterialTheme.colorScheme.onTertiaryContainer
        AuditSeverity.Info -> MaterialTheme.colorScheme.onSecondaryContainer
        AuditSeverity.Pass -> MaterialTheme.colorScheme.onPrimaryContainer
    }
    val severityIcon = when (finding.severity) {
        AuditSeverity.Critical -> Icons.Default.Error
        AuditSeverity.Warning -> Icons.Default.Warning
        AuditSeverity.Info -> Icons.Default.Info
        AuditSeverity.Pass -> Icons.Default.CheckCircle
    }
    val severityLabel = when (finding.severity) {
        AuditSeverity.Critical -> stringResource(R.string.wifiaudit_severity_critical)
        AuditSeverity.Warning -> stringResource(R.string.wifiaudit_severity_warning)
        AuditSeverity.Info -> stringResource(R.string.wifiaudit_severity_info)
        AuditSeverity.Pass -> stringResource(R.string.wifiaudit_severity_pass)
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = containerColor,
            contentColor = contentColor,
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(
                        imageVector = severityIcon,
                        contentDescription = severityLabel,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = finding.title.resolve(),
                        style = MaterialTheme.typography.titleSmall,
                    )
                }
                if (finding.severity != AuditSeverity.Critical) {
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = stringResource(R.string.wifiaudit_cd_dismiss),
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = finding.description.resolve(),
                style = MaterialTheme.typography.bodySmall,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = finding.guidance.resolve(),
                style = MaterialTheme.typography.labelMedium,
                color = contentColor.copy(alpha = 0.8f),
            )
        }
    }
}

@Composable
private fun EmptyAuditState(hasRun: Boolean, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = Icons.Default.CheckCircle,
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = stringResource(
                if (hasRun) R.string.wifiaudit_empty_clean
                else R.string.wifiaudit_empty_not_connected,
            ),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
