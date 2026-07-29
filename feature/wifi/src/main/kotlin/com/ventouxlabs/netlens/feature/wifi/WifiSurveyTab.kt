package com.ventouxlabs.netlens.feature.wifi

import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ventouxlabs.netlens.core.data.model.WifiSurveySessionSummary
import com.ventouxlabs.netlens.feature.wifi.engine.SurveyAggregator
import com.ventouxlabs.netlens.feature.wifi.model.SignalQuality
import com.ventouxlabs.netlens.feature.wifi.model.SurveyError
import com.ventouxlabs.netlens.feature.wifi.model.surveyPointKey
import com.ventouxlabs.netlens.feature.wifi.model.surveySessionKey
import com.ventouxlabs.netlens.feature.wifi.model.WifiSurveyUiState
import com.ventouxlabs.netlens.feature.wifi.ui.CoverageBar
import com.ventouxlabs.netlens.feature.wifi.ui.SignalTrail
import com.ventouxlabs.netlens.feature.wifi.ui.color

/**
 * Walk-through coverage survey. The flow is: start a session, walk to a spot, type where you
 * are, capture a few seconds of readings, repeat — then read the weak spots off the map.
 */
@Composable
fun WifiSurveyTab(
    state: WifiSurveyUiState,
    onStartSurvey: (String) -> Unit,
    onStopSurvey: () -> Unit,
    onLabelChanged: (String) -> Unit,
    onCapturePoint: () -> Unit,
    onCancelCapture: () -> Unit,
    onDeletePoint: (Long) -> Unit,
    onSelectSession: (Long) -> Unit,
    onDeleteSession: (Long) -> Unit,
    onToggleSort: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val orderedPoints = remember(state.points, state.sortWorstFirst) {
        if (state.sortWorstFirst) state.points.sortedBy { it.avgRssi } else state.points
    }
    val weakSpots = remember(state.points) { SurveyAggregator.weakSpots(state.points) }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Conditional around the item, not inside it: an always-present item still takes a slot
        // in the 12dp spacedBy arrangement, leaving a permanent gap in the no-error case.
        state.error?.let { error ->
            item {
                Text(
                    text = stringResource(error.messageRes()),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }

        item {
            if (state.isSurveying) {
                LiveMeterCard(state = state)
            } else {
                StartSurveyCard(onStartSurvey = onStartSurvey)
            }
        }

        if (state.isSurveying) {
            item {
                CaptureControls(
                    state = state,
                    onLabelChanged = onLabelChanged,
                    onCapturePoint = onCapturePoint,
                    onCancelCapture = onCancelCapture,
                    onStopSurvey = onStopSurvey,
                )
            }
        }

        if (state.points.isNotEmpty()) {
            item {
                HorizontalDivider()
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = state.viewedSessionName.ifBlank {
                            stringResource(R.string.wifi_survey_map_title)
                        },
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    TextButton(onClick = onToggleSort) {
                        Text(
                            stringResource(
                                if (state.sortWorstFirst) {
                                    R.string.wifi_survey_sort_by_order
                                } else {
                                    R.string.wifi_survey_sort_worst_first
                                },
                            ),
                        )
                    }
                }
            }

            if (weakSpots.isNotEmpty()) {
                item {
                    WeakSpotSummary(labels = weakSpots.map { it.label })
                }
            }

            // One lazy item per bar, not one item holding every bar: a thorough survey of a large
            // house runs to dozens of spots, and wrapping them in a single item would compose and
            // measure all of them on every emission — including at the live meter's 1.4 Hz tick.
            items(orderedPoints, key = { surveyPointKey(it.id) }) { point ->
                CoverageBar(point = point, onDelete = { onDeletePoint(point.id) })
            }
        } else if (!state.isSurveying) {
            item {
                Text(
                    text = stringResource(R.string.wifi_survey_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                )
            }
        }

        if (state.sessions.isNotEmpty()) {
            item {
                HorizontalDivider()
                Text(
                    text = stringResource(R.string.wifi_survey_past_sessions),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            items(state.sessions, key = { surveySessionKey(it.id) }) { session ->
                SessionRow(
                    session = session,
                    isViewed = session.id == state.viewedSessionId,
                    // Switching sessions mid-walk would silently redirect the next capture, so
                    // history is read-only until the current survey is stopped.
                    enabled = !state.isSurveying,
                    onClick = { onSelectSession(session.id) },
                    onDelete = { onDeleteSession(session.id) },
                )
            }
        }
    }
}

@Composable
private fun StartSurveyCard(onStartSurvey: (String) -> Unit) {
    var name by remember { mutableStateOf("") }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = stringResource(R.string.wifi_survey_start_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = stringResource(R.string.wifi_survey_start_help),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(R.string.wifi_survey_name_label)) },
                placeholder = { Text(stringResource(R.string.wifi_survey_name_hint)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Button(onClick = { onStartSurvey(name) }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.wifi_survey_start_button))
            }
        }
    }
}

@Composable
private fun LiveMeterCard(state: WifiSurveyUiState) {
    val quality = state.liveQuality
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text(
                        text = state.liveSample?.let {
                            stringResource(R.string.wifi_survey_dbm, it.rssi)
                        } ?: stringResource(R.string.wifi_survey_waiting),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = quality?.color() ?: MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    quality?.let {
                        Text(
                            text = stringResource(it.labelRes()),
                            style = MaterialTheme.typography.labelMedium,
                            color = it.color(),
                        )
                    }
                }
                state.liveSample?.let { sample ->
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = sample.ssid.orEmpty(),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                        )
                        Text(
                            text = stringResource(
                                R.string.wifi_survey_live_link,
                                sample.linkSpeedMbps,
                            ),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            SignalTrail(trail = state.trail)
        }
    }
}

@Composable
private fun CaptureControls(
    state: WifiSurveyUiState,
    onLabelChanged: (String) -> Unit,
    onCapturePoint: () -> Unit,
    onCancelCapture: () -> Unit,
    onStopSurvey: () -> Unit,
) {
    val capture = state.capture
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (capture != null) {
            Text(
                text = stringResource(
                    R.string.wifi_survey_capturing,
                    capture.label,
                    capture.collected,
                    capture.target,
                ),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
            )
            LinearProgressIndicator(
                progress = { capture.fraction },
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedButton(onClick = onCancelCapture, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.wifi_survey_cancel_capture))
            }
        } else {
            OutlinedTextField(
                value = state.pendingLabel,
                onValueChange = onLabelChanged,
                label = { Text(stringResource(R.string.wifi_survey_label_label)) },
                placeholder = { Text(stringResource(R.string.wifi_survey_label_hint)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onCapturePoint, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.wifi_survey_capture_button))
                }
                OutlinedButton(onClick = onStopSurvey) {
                    Icon(Icons.Default.Stop, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.wifi_survey_stop_button))
                }
            }
        }
    }
}

@Composable
private fun WeakSpotSummary(labels: List<String>) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f),
        ),
    ) {
        Column(Modifier.padding(12.dp)) {
            Text(
                text = stringResource(R.string.wifi_survey_weak_title, labels.size),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = labels.joinToString(", "),
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun SessionRow(
    session: WifiSurveySessionSummary,
    isViewed: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = session.name,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (isViewed) FontWeight.Bold else FontWeight.Normal,
            )
            Text(
                text = if (session.pointCount == 0) {
                    stringResource(R.string.wifi_survey_session_empty)
                } else {
                    stringResource(
                        R.string.wifi_survey_session_summary,
                        session.pointCount,
                        session.worstRssi ?: 0,
                    )
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = onDelete) {
            Icon(
                imageVector = Icons.Default.Delete,
                contentDescription = stringResource(R.string.wifi_survey_delete_session),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@StringRes
private fun SurveyError.messageRes(): Int = when (this) {
    SurveyError.NOT_CONNECTED -> R.string.wifi_survey_error_not_connected
    SurveyError.LABEL_REQUIRED -> R.string.wifi_survey_error_label_required
    SurveyError.NO_SAMPLES -> R.string.wifi_survey_error_no_samples
    SurveyError.SIGNAL_LOST -> R.string.wifi_survey_error_signal_lost
    SurveyError.CAPTURE_INTERRUPTED -> R.string.wifi_survey_error_capture_interrupted
}

@StringRes
private fun SignalQuality.labelRes(): Int = when (this) {
    SignalQuality.EXCELLENT -> R.string.wifi_quality_excellent
    SignalQuality.GOOD -> R.string.wifi_quality_good
    SignalQuality.FAIR -> R.string.wifi_quality_fair
    SignalQuality.WEAK -> R.string.wifi_quality_weak
    SignalQuality.UNUSABLE -> R.string.wifi_quality_unusable
}
