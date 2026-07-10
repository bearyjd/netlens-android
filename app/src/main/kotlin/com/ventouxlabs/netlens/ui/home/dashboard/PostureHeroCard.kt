package com.ventouxlabs.netlens.ui.home.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.ventouxlabs.netlens.R
import com.ventouxlabs.netlens.core.ui.LocalStatusColors
import com.ventouxlabs.netlens.core.ui.Spacing
import com.ventouxlabs.netlens.core.ui.StampChip
import com.ventouxlabs.netlens.feature.posture.model.PostureScore
import com.ventouxlabs.netlens.feature.posture.model.PostureUiState
import com.ventouxlabs.netlens.feature.posture.model.Severity

/**
 * The dashboard's focal point: overall posture as one plain-language line,
 * the letter grade, and the connection identity as a stamp chip. Colors come
 * exclusively from the three-state status system (teal / amber / stamp red).
 * Tapping opens the Posture detail screen; in the error state it retries.
 */
@Composable
fun PostureHeroCard(
    postureState: PostureUiState,
    isConnected: Boolean,
    interfaceLabel: String?,
    onClick: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isError = postureState is PostureUiState.Error
    val clickLabel = if (isError) {
        stringResource(R.string.dash_hero_retry)
    } else {
        stringResource(R.string.dash_hero_open)
    }
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(
                onClickLabel = clickLabel,
                role = Role.Button,
                onClick = if (isError) onRetry else onClick,
            ),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(modifier = Modifier.padding(Spacing.lg)) {
            when (postureState) {
                is PostureUiState.Scored -> ScoredContent(
                    score = postureState.score,
                    isConnected = isConnected,
                    interfaceLabel = interfaceLabel,
                )
                PostureUiState.Loading -> SimpleContent(
                    badgeText = "…",
                    badgeColor = LocalStatusColors.current.muted,
                    title = stringResource(R.string.dash_status_loading),
                    subtitle = null,
                )
                PostureUiState.Disconnected -> SimpleContent(
                    badgeText = "—",
                    badgeColor = LocalStatusColors.current.muted,
                    title = stringResource(R.string.dash_status_disconnected),
                    subtitle = null,
                    chipText = stringResource(R.string.dash_chip_offline),
                    chipContainer = MaterialTheme.colorScheme.surface,
                    chipContent = LocalStatusColors.current.muted,
                )
                is PostureUiState.Error -> SimpleContent(
                    badgeText = "!",
                    badgeColor = LocalStatusColors.current.fail,
                    title = stringResource(R.string.dash_status_error),
                    subtitle = stringResource(R.string.dash_tap_retry),
                )
            }
        }
    }
}

@Composable
private fun ScoredContent(
    score: PostureScore,
    isConnected: Boolean,
    interfaceLabel: String?,
) {
    val status = LocalStatusColors.current
    val attentionCount = score.factors.count {
        it.severity == Severity.Moderate || it.severity == Severity.Poor ||
            it.severity == Severity.Critical
    }
    // Title and tint are derived from the SAME bucket so the copy can never
    // contradict its color (e.g. "looks good" rendered in alert red for an
    // unexpected grade string — unknown grades land in the fail bucket).
    val bucket = when (score.grade.firstOrNull()) {
        'A', 'B' -> StatusBucket.Pass
        'C', 'D' -> StatusBucket.Warn
        else -> StatusBucket.Fail
    }
    val (accentColor, containerColor, onContainerColor) = when (bucket) {
        StatusBucket.Pass -> Triple(status.pass, status.passContainer, status.onPassContainer)
        StatusBucket.Warn -> Triple(status.warn, status.warnContainer, status.onWarnContainer)
        StatusBucket.Fail -> Triple(status.fail, status.failContainer, status.onFailContainer)
    }
    val title = when (bucket) {
        StatusBucket.Fail -> stringResource(R.string.dash_status_alert)
        StatusBucket.Warn -> pluralStringResource(
            R.plurals.dash_status_attention,
            attentionCount.coerceAtLeast(1),
            attentionCount.coerceAtLeast(1),
        )
        StatusBucket.Pass -> if (attentionCount > 0) {
            pluralStringResource(R.plurals.dash_status_attention, attentionCount, attentionCount)
        } else {
            stringResource(R.string.dash_status_good)
        }
    }
    val scoredFactors = score.factors.count { it.severity != Severity.Unavailable }

    Row(verticalAlignment = Alignment.CenterVertically) {
        GradeBadge(
            text = score.grade,
            textColor = onContainerColor,
            containerColor = containerColor,
        )
        Spacer(modifier = Modifier.width(Spacing.lg))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                color = accentColor,
            )
            Text(
                text = stringResource(
                    R.string.posture_hero_score_subtitle,
                    score.numericScore,
                    scoredFactors,
                    score.factors.size,
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    if (isConnected) {
        Spacer(modifier = Modifier.height(Spacing.md))
        StampChip(
            text = interfaceLabel ?: stringResource(R.string.dash_chip_connected),
            containerColor = containerColor,
            contentColor = onContainerColor,
        )
    }
}

@Composable
private fun SimpleContent(
    badgeText: String,
    badgeColor: Color,
    title: String,
    subtitle: String?,
    chipText: String? = null,
    chipContainer: Color = Color.Unspecified,
    chipContent: Color = Color.Unspecified,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        GradeBadge(
            text = badgeText,
            textColor = badgeColor,
            containerColor = badgeColor.copy(alpha = 0.12f),
        )
        Spacer(modifier = Modifier.width(Spacing.lg))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
    if (chipText != null) {
        Spacer(modifier = Modifier.height(Spacing.md))
        StampChip(
            text = chipText,
            containerColor = chipContainer,
            contentColor = chipContent,
        )
    }
}

private enum class StatusBucket { Pass, Warn, Fail }

@Composable
private fun GradeBadge(
    text: String,
    textColor: Color,
    containerColor: Color,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(56.dp)
            .background(containerColor, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.headlineMedium,
            color = textColor,
        )
    }
}
