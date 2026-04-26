package com.ventoux.netlens.feature.posture

import androidx.compose.foundation.background
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Info
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ventoux.netlens.feature.posture.model.FactorResult
import com.ventoux.netlens.feature.posture.model.PostureScore
import com.ventoux.netlens.feature.posture.model.PostureUiState
import com.ventoux.netlens.feature.posture.model.Severity

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PostureScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PostureViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("Security Posture") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = viewModel::refresh) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
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
                    Text("Evaluating network security…")
                }
                PostureUiState.Disconnected -> {
                    Spacer(modifier = Modifier.height(64.dp))
                    Text(
                        text = "No network connection",
                        style = MaterialTheme.typography.headlineSmall,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Connect to a network to see your security posture score.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
                is PostureUiState.Scored -> {
                    ScoreHero(score = s.score)
                    Spacer(modifier = Modifier.height(24.dp))
                    s.score.factors.forEach { factor ->
                        FactorCard(factor = factor)
                        Spacer(modifier = Modifier.height(12.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun ScoreHero(score: PostureScore) {
    Box(
        modifier = Modifier
            .size(140.dp)
            .background(
                color = score.color.copy(alpha = 0.12f),
                shape = CircleShape,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = score.grade,
            fontSize = 64.sp,
            fontWeight = FontWeight.Bold,
            color = score.color,
        )
    }
    Spacer(modifier = Modifier.height(12.dp))
    Text(
        text = "${score.numericScore}/100",
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
private fun FactorCard(factor: FactorResult) {
    Card(
        modifier = Modifier.fillMaxWidth(),
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

private fun gradeDescription(grade: String): String = when (grade) {
    "A" -> "Excellent — your network is well secured"
    "B" -> "Good — minor improvements possible"
    "C" -> "Fair — some security gaps to address"
    "D" -> "Poor — significant vulnerabilities detected"
    "F" -> "Critical — immediate action recommended"
    else -> "Not yet evaluated"
}

private fun severityIcon(severity: Severity) = when (severity) {
    Severity.Good -> Icons.Default.CheckCircle
    Severity.Moderate -> Icons.Default.Info
    Severity.Poor -> Icons.Default.Warning
    Severity.Critical -> Icons.Default.Error
    Severity.Unavailable -> Icons.Default.Info
}

private fun severityColor(severity: Severity) = when (severity) {
    Severity.Good -> Color(0xFF4CAF50)
    Severity.Moderate -> Color(0xFFFFC107)
    Severity.Poor -> Color(0xFFFF9800)
    Severity.Critical -> Color(0xFFF44336)
    Severity.Unavailable -> Color(0xFF9E9E9E)
}
