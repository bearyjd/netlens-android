package com.ventouxlabs.netlens.feature.posture

import android.content.ActivityNotFoundException
import android.content.Intent
import android.provider.Settings
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ventouxlabs.netlens.core.network.VpnState
import com.ventouxlabs.netlens.core.ui.LocalStatusColors
import com.ventouxlabs.netlens.core.ui.Spacing
import com.ventouxlabs.netlens.core.ui.StatusColors
import com.ventouxlabs.netlens.feature.posture.model.FactorResult
import com.ventouxlabs.netlens.feature.posture.model.PostureFactor
import com.ventouxlabs.netlens.feature.posture.model.PostureScore
import com.ventouxlabs.netlens.feature.posture.model.PostureUiState
import com.ventouxlabs.netlens.feature.posture.model.Severity

@Composable
fun gradeColor(grade: String): Color = gradeColor(grade, LocalStatusColors.current)

private fun gradeColor(grade: String, statusColors: StatusColors): Color = when {
    grade.startsWith("A") || grade.startsWith("B") -> statusColors.pass
    grade.startsWith("C") || grade.startsWith("D") -> statusColors.warn
    else -> statusColors.fail
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PostureScreen(
    onBack: () -> Unit,
    onFactorClick: (PostureFactor) -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: PostureViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.posture_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.posture_cd_back))
                    }
                },
                actions = {
                    IconButton(onClick = viewModel::refresh) {
                        Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.posture_cd_refresh))
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
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            when (val s = state) {
                PostureUiState.Loading -> {
                    Spacer(modifier = Modifier.height(64.dp))
                    CircularProgressIndicator()
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(stringResource(R.string.posture_loading))
                }
                PostureUiState.Disconnected -> {
                    Spacer(modifier = Modifier.height(64.dp))
                    Text(
                        text = stringResource(R.string.posture_disconnected_title),
                        style = MaterialTheme.typography.headlineSmall,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.posture_disconnected_body),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
                is PostureUiState.Error -> {
                    Spacer(modifier = Modifier.height(64.dp))
                    Icon(
                        imageVector = Icons.Default.Error,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(48.dp),
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = stringResource(R.string.posture_error_title),
                        style = MaterialTheme.typography.headlineSmall,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = s.message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
                is PostureUiState.Scored -> {
                    val context = LocalContext.current
                    VpnLockIndicator(
                        state = s.score.vpnState,
                        onClick = {
                            val vpnIntent = Intent(Settings.ACTION_VPN_SETTINGS)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            try {
                                context.startActivity(vpnIntent)
                            } catch (e: ActivityNotFoundException) {
                                // AOSP forks without a VPN settings activity (rare).
                                Log.w("PostureScreen", "ACTION_VPN_SETTINGS not handled", e)
                                context.startActivity(
                                    Intent(Settings.ACTION_SETTINGS)
                                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                                )
                            }
                        },
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    ScoreHero(score = s.score)
                    Spacer(modifier = Modifier.height(24.dp))
                    s.score.factors.forEach { factor ->
                        FactorCard(
                            factor = factor,
                            onClick = { onFactorClick(factor.factor) },
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun VpnLockIndicator(state: VpnState, onClick: () -> Unit) {
    val statusColors = LocalStatusColors.current
    val (containerColor, contentColor, icon, labelRes) = when (state) {
        VpnState.FullTunnel -> VpnIndicatorDisplay(
            statusColors.passContainer,
            statusColors.onPassContainer,
            Icons.Default.Lock,
            R.string.posture_vpn_label_full,
        )
        VpnState.SplitTunnel -> VpnIndicatorDisplay(
            statusColors.warnContainer,
            statusColors.onWarnContainer,
            Icons.Default.Lock,
            R.string.posture_vpn_label_split,
        )
        VpnState.None -> VpnIndicatorDisplay(
            statusColors.failContainer,
            statusColors.onFailContainer,
            Icons.Default.LockOpen,
            R.string.posture_vpn_label_none,
        )
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(containerColor)
            .clickable(onClick = onClick)
            .padding(horizontal = Spacing.md, vertical = 6.dp),
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = contentColor, modifier = Modifier.size(16.dp))
        Spacer(modifier = Modifier.width(6.dp))
        Text(text = stringResource(labelRes), color = contentColor, fontWeight = FontWeight.Bold)
    }
}

private data class VpnIndicatorDisplay(
    val containerColor: Color,
    val contentColor: Color,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val labelRes: Int,
)

@Composable
private fun ScoreHero(score: PostureScore) {
    val color = gradeColor(score.grade)
    Box(
        modifier = Modifier
            .size(140.dp)
            .background(
                color = color.copy(alpha = 0.12f),
                shape = CircleShape,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = score.grade,
            // Hero exception: the grade letter is the screen's focal point and
            // intentionally larger than the type scale's displayLarge (34sp).
            style = MaterialTheme.typography.displayLarge.copy(
                fontSize = 64.sp,
                lineHeight = 72.sp,
            ),
            fontWeight = FontWeight.Bold,
            color = color,
        )
    }
    Spacer(modifier = Modifier.height(12.dp))
    Text(
        text = stringResource(R.string.posture_score_format, score.numericScore),
        style = MaterialTheme.typography.titleLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(modifier = Modifier.height(4.dp))
    Text(
        text = gradeDescription(score.grade),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun FactorCard(factor: FactorResult, onClick: () -> Unit = {}) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Icon(
                imageVector = severityIcon(factor.severity),
                contentDescription = null,
                tint = severityColor(factor.severity),
                modifier = Modifier.size(24.dp),
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = factor.factor.displayName,
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Text(
                        text = factor.label,
                        style = MaterialTheme.typography.labelMedium,
                        color = severityColor(factor.severity),
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = factor.detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun gradeDescription(grade: String): String = when (grade) {
    "A" -> stringResource(R.string.posture_grade_a)
    "B" -> stringResource(R.string.posture_grade_b)
    "C" -> stringResource(R.string.posture_grade_c)
    "D" -> stringResource(R.string.posture_grade_d)
    "F" -> stringResource(R.string.posture_grade_f)
    else -> stringResource(R.string.posture_grade_unknown)
}

private fun severityIcon(severity: Severity) = when (severity) {
    Severity.Good -> Icons.Default.CheckCircle
    Severity.Moderate -> Icons.Default.Info
    Severity.Poor -> Icons.Default.Warning
    Severity.Critical -> Icons.Default.Error
    Severity.Unavailable -> Icons.Default.Info
}

@Composable
private fun severityColor(severity: Severity): Color {
    val statusColors = LocalStatusColors.current
    return when (severity) {
        Severity.Good -> statusColors.pass
        Severity.Moderate -> statusColors.warn
        Severity.Poor -> statusColors.warn
        Severity.Critical -> statusColors.fail
        Severity.Unavailable -> statusColors.muted
    }
}
