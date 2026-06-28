package com.ventouxlabs.netlens.feature.ipcalc

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.ventouxlabs.netlens.core.billing.LocalProStatus
import com.ventouxlabs.netlens.core.network.export.ResultExporter
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ventouxlabs.netlens.feature.ipcalc.model.SubnetInfo

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IpCalcScreen(
    onBack: () -> Unit = {},
    initialInput: String? = null,
    viewModel: IpCalcViewModel = hiltViewModel(),
) {
    val uiState by viewModel.state.collectAsStateWithLifecycle()
    val keyboardController = LocalSoftwareKeyboardController.current
    val context = LocalContext.current
    val proStatus = LocalProStatus.current
    val isPro by proStatus.isPro.collectAsStateWithLifecycle()

    LaunchedEffect(initialInput) {
        if (!initialInput.isNullOrEmpty()) {
            viewModel.onInputChange(initialInput)
            viewModel.calculate()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.ipcalc_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.navigate_back),
                        )
                    }
                },
                actions = {
                    if (uiState.result != null) {
                        IconButton(onClick = {
                            ResultExporter.copyToClipboard(context, "IP Calculator", viewModel.buildExportText())
                        }) {
                            Icon(Icons.Default.ContentCopy, contentDescription = stringResource(R.string.ipcalc_cd_copy_results))
                        }
                        if (isPro) {
                            IconButton(onClick = {
                                ResultExporter.shareAsText(context, "IP Calculator Results", viewModel.buildExportText())
                            }) {
                                Icon(Icons.Default.Share, contentDescription = stringResource(R.string.ipcalc_cd_share))
                            }
                        }
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            OutlinedTextField(
                value = uiState.input,
                onValueChange = viewModel::onInputChange,
                label = { Text(stringResource(R.string.ipcalc_label_input)) },
                placeholder = { Text(stringResource(R.string.ipcalc_placeholder_input)) },
                singleLine = true,
                isError = uiState.error != null,
                supportingText = uiState.error?.let { error -> { Text(error) } },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = {
                    keyboardController?.hide()
                    viewModel.calculate()
                }),
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(modifier = Modifier.height(12.dp))

            Button(
                onClick = {
                    keyboardController?.hide()
                    viewModel.calculate()
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.ipcalc_button_calculate))
            }

            Spacer(modifier = Modifier.height(16.dp))

            uiState.result?.let { info ->
                SubnetResultCard(info = info)
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun SubnetResultCard(info: SubnetInfo) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            ResultRow(stringResource(R.string.ipcalc_label_cidr), info.cidrNotation)
            ResultRow(stringResource(R.string.ipcalc_label_network), info.networkAddress)
            ResultRow(stringResource(R.string.ipcalc_label_broadcast), info.broadcastAddress)
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            ResultRow(stringResource(R.string.ipcalc_label_first_host), info.firstHost)
            ResultRow(stringResource(R.string.ipcalc_label_last_host), info.lastHost)
            ResultRow(stringResource(R.string.ipcalc_label_total_hosts), info.totalHosts.toString())
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            ResultRow(stringResource(R.string.ipcalc_label_subnet_mask), info.subnetMask)
            ResultRow(stringResource(R.string.ipcalc_label_wildcard), info.wildcardMask)
            ResultRow(stringResource(R.string.ipcalc_label_ip_class), info.ipClass)
            ResultRow(
                stringResource(R.string.ipcalc_label_bogon),
                if (info.isBogon) stringResource(R.string.ipcalc_bogon_yes)
                else stringResource(R.string.ipcalc_bogon_no),
            )
        }
    }
}

@Composable
private fun ResultRow(label: String, value: String) {
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
            fontFamily = MaterialTheme.typography.labelSmall.fontFamily,
        )
    }
}

