package com.ventouxlabs.netlens.feature.vpnstatus

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.SignalWifiOff
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ventouxlabs.netlens.core.network.VpnState
import com.ventouxlabs.netlens.feature.vpnstatus.model.VpnStatusUiState

private val ColorProtected = Color(0xFF388E3C)
private val ColorSplit = Color(0xFFF57C00)
private val ColorNone = Color(0xFFD32F2F)
private val ColorOffline = Color(0xFF9E9E9E)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VpnStatusScreen(
    onBack: () -> Unit = {},
    onOpenDnsLeak: () -> Unit = {},
    viewModel: VpnStatusViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.vpnstatus_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.vpnstatus_back),
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        VpnStatusContent(
            state = state,
            onOpenDnsLeak = onOpenDnsLeak,
            modifier = Modifier.padding(innerPadding),
        )
    }
}

@Composable
private fun VpnStatusContent(
    state: VpnStatusUiState,
    onOpenDnsLeak: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Spacer(modifier = Modifier.height(8.dp))

        ElevatedCard(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                val (color, icon, labelRes, descRes) = when {
                    !state.isOnline -> StatusDisplay(
                        color = ColorOffline,
                        icon = Icons.Default.SignalWifiOff,
                        labelRes = R.string.vpnstatus_state_offline,
                        descRes = R.string.vpnstatus_desc_offline,
                    )
                    state.vpnState == VpnState.FullTunnel -> StatusDisplay(
                        color = ColorProtected,
                        icon = Icons.Default.Lock,
                        labelRes = R.string.vpnstatus_state_protected,
                        descRes = R.string.vpnstatus_desc_protected,
                    )
                    state.vpnState == VpnState.SplitTunnel -> StatusDisplay(
                        color = ColorSplit,
                        icon = Icons.Default.Lock,
                        labelRes = R.string.vpnstatus_state_split,
                        descRes = R.string.vpnstatus_desc_split,
                    )
                    else -> StatusDisplay(
                        color = ColorNone,
                        icon = Icons.Default.LockOpen,
                        labelRes = R.string.vpnstatus_state_none,
                        descRes = R.string.vpnstatus_desc_none,
                    )
                }

                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(64.dp),
                )

                Text(
                    text = stringResource(labelRes),
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = color,
                )

                Text(
                    text = stringResource(descRes),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }

        Button(
            onClick = onOpenDnsLeak,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.vpnstatus_run_leak_test))
        }

        Text(
            text = stringResource(R.string.vpnstatus_caption),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 8.dp),
        )
    }
}

private data class StatusDisplay(
    val color: Color,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val labelRes: Int,
    val descRes: Int,
)
