package com.ventouxlabs.netlens.feature.dnsleak

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ventouxlabs.netlens.core.billing.LocalProStatus
import com.ventouxlabs.netlens.core.network.export.ResultExporter
import com.ventouxlabs.netlens.feature.dnsleak.model.DnsLeakResult
import com.ventouxlabs.netlens.feature.dnsleak.model.DnsLeakUiState
import com.ventouxlabs.netlens.feature.dnsleak.model.ResolverInfo

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DnsLeakScreen(
    onBack: () -> Unit = {},
    viewModel: DnsLeakViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val proStatus = LocalProStatus.current
    val isPro by proStatus.isPro.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.dnsleak_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.navigate_back),
                        )
                    }
                },
                actions = {
                    if (state.result != null) {
                        IconButton(onClick = {
                            ResultExporter.copyToClipboard(
                                context,
                                "DNS Leak Test",
                                viewModel.buildExportText(),
                            )
                        }) {
                            Icon(
                                Icons.Default.ContentCopy,
                                contentDescription = stringResource(R.string.dnsleak_cd_copy),
                            )
                        }
                        if (isPro) {
                            IconButton(onClick = {
                                ResultExporter.shareAsText(
                                    context,
                                    "DNS Leak Test Results",
                                    viewModel.buildExportText(),
                                )
                            }) {
                                Icon(
                                    Icons.Default.Share,
                                    contentDescription = stringResource(R.string.dnsleak_cd_share),
                                )
                            }
                        }
                    }
                },
            )
        },
    ) { padding ->
        DnsLeakContent(
            state = state,
            onRunTest = viewModel::runTest,
            modifier = Modifier.padding(padding),
        )
    }
}

@Composable
private fun DnsLeakContent(
    state: DnsLeakUiState,
    onRunTest: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Spacer(modifier = Modifier.height(4.dp))

        StatusCard(state)

        Button(
            onClick = onRunTest,
            modifier = Modifier.fillMaxWidth(),
            enabled = !state.isLoading,
        ) {
            if (state.isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                )
                Spacer(modifier = Modifier.width(8.dp))
            }
            Text(
                if (state.result != null) {
                    stringResource(R.string.dnsleak_button_rerun)
                } else {
                    stringResource(R.string.dnsleak_button_run)
                },
            )
        }

        when (val result = state.result) {
            is DnsLeakResult.NoLeak -> {
                if (!state.vpnActive) {
                    InfoCard(
                        title = stringResource(R.string.dnsleak_no_vpn_info_title),
                        body = stringResource(R.string.dnsleak_no_vpn_info_body),
                    )
                }
                ResolverList(
                    title = stringResource(R.string.dnsleak_resolvers_title),
                    resolvers = result.resolvers,
                )
            }
            is DnsLeakResult.LeakDetected -> {
                ResolverList(
                    title = stringResource(R.string.dnsleak_resolvers_leaked_title),
                    resolvers = result.leakedResolvers,
                    isLeak = true,
                )
                if (result.expectedResolvers.isNotEmpty()) {
                    ResolverList(
                        title = stringResource(R.string.dnsleak_resolvers_expected_title),
                        resolvers = result.expectedResolvers,
                    )
                }
                FixGuidanceCard()
            }
            is DnsLeakResult.Error -> {
                // Error is shown in status card
            }
            null -> {
                // Not yet tested
            }
        }

        ExplanationCard()

        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun StatusCard(state: DnsLeakUiState) {
    val (icon, iconTint, title, detail) = when {
        state.isLoading -> StatusCardData(
            icon = Icons.Default.Shield,
            iconTint = MaterialTheme.colorScheme.primary,
            title = stringResource(R.string.dnsleak_title),
            detail = "",
        )
        state.result is DnsLeakResult.NoLeak -> StatusCardData(
            icon = Icons.Default.CheckCircle,
            iconTint = MaterialTheme.colorScheme.primary,
            title = stringResource(R.string.dnsleak_status_pass),
            detail = stringResource(R.string.dnsleak_status_pass_detail),
        )
        state.result is DnsLeakResult.LeakDetected -> StatusCardData(
            icon = Icons.Default.Warning,
            iconTint = MaterialTheme.colorScheme.error,
            title = stringResource(R.string.dnsleak_status_fail),
            detail = stringResource(R.string.dnsleak_status_fail_detail),
        )
        state.result is DnsLeakResult.Error -> StatusCardData(
            icon = Icons.Default.Error,
            iconTint = MaterialTheme.colorScheme.error,
            title = stringResource(R.string.dnsleak_status_error),
            detail = (state.result as DnsLeakResult.Error).message,
        )
        else -> StatusCardData(
            icon = Icons.Default.Shield,
            iconTint = MaterialTheme.colorScheme.onSurfaceVariant,
            title = stringResource(R.string.dnsleak_status_not_tested),
            detail = stringResource(R.string.dnsleak_status_not_tested_detail),
        )
    }

    val containerColor = when (state.result) {
        is DnsLeakResult.NoLeak -> MaterialTheme.colorScheme.primaryContainer
        is DnsLeakResult.LeakDetected -> MaterialTheme.colorScheme.errorContainer
        else -> MaterialTheme.colorScheme.surfaceVariant
    }

    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.elevatedCardColors(containerColor = containerColor),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = iconTint,
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
            )
            if (state.result != null && !state.isLoading) {
                Row(
                    modifier = Modifier.padding(top = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = if (state.vpnActive) {
                            stringResource(R.string.dnsleak_vpn_active)
                        } else {
                            stringResource(R.string.dnsleak_vpn_inactive)
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (detail.isNotBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = detail,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ResolverList(
    title: String,
    resolvers: List<ResolverInfo>,
    isLeak: Boolean = false,
) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = if (isLeak) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.primary
                },
            )
            Spacer(modifier = Modifier.height(8.dp))
            resolvers.forEach { resolver ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = resolver.ip,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontFamily = MaterialTheme.typography.labelSmall.fontFamily,
                            ),
                        )
                        Text(
                            text = resolver.name,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(
                        text = if (resolver.isKnownPublic) {
                            stringResource(R.string.dnsleak_resolver_public)
                        } else {
                            stringResource(R.string.dnsleak_resolver_isp)
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = if (resolver.isKnownPublic) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.error
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun InfoCard(title: String, body: String) {
    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.padding(16.dp)) {
            Icon(
                imageVector = Icons.Default.Info,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = body,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun FixGuidanceCard() {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.dnsleak_fix_title),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(modifier = Modifier.height(8.dp))
            val steps = listOf(
                stringResource(R.string.dnsleak_fix_step_1),
                stringResource(R.string.dnsleak_fix_step_2),
                stringResource(R.string.dnsleak_fix_step_3),
                stringResource(R.string.dnsleak_fix_step_4),
            )
            steps.forEachIndexed { index, step ->
                Text(
                    text = "${index + 1}. $step",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 2.dp),
                )
            }
        }
    }
}

@Composable
private fun ExplanationCard() {
    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.dnsleak_what_is_title),
                style = MaterialTheme.typography.titleSmall,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.dnsleak_what_is_body),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private data class StatusCardData(
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val iconTint: androidx.compose.ui.graphics.Color,
    val title: String,
    val detail: String,
)
