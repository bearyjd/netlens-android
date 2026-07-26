package com.ventouxlabs.netlens.feature.wifi.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ventouxlabs.netlens.core.data.model.WifiSurveyPointEntity
import com.ventouxlabs.netlens.core.ui.LocalStatusColors
import com.ventouxlabs.netlens.feature.wifi.R
import com.ventouxlabs.netlens.feature.wifi.model.SignalQuality
import com.ventouxlabs.netlens.feature.wifi.model.apShortName

/**
 * The coverage map: one bar per measured spot, length and colour by average signal.
 *
 * A ranked bar chart rather than a floor plan — the phone has no indoor position fix, so the
 * honest representation of "walk around and measure" is a list of named spots, not a heat map
 * drawn over a room outline the app would have to invent.
 */
@Composable
fun CoverageMap(
    points: List<WifiSurveyPointEntity>,
    onDeletePoint: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        points.forEach { point ->
            CoverageBar(point = point, onDelete = { onDeletePoint(point.id) })
        }
    }
}

@Composable
private fun CoverageBar(
    point: WifiSurveyPointEntity,
    onDelete: () -> Unit,
) {
    val quality = SignalQuality.forRssi(point.avgRssi)
    val color = quality.color()

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = point.label,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = stringResource(R.string.wifi_survey_dbm, point.avgRssi),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = color,
            )
            IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = stringResource(R.string.wifi_survey_delete_point),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(10.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(5.dp)),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(SignalQuality.normalize(point.avgRssi))
                    .height(10.dp)
                    .background(color, RoundedCornerShape(5.dp)),
            )
        }
        Text(
            text = buildString {
                append(
                    stringResource(
                        R.string.wifi_survey_point_range,
                        point.minRssi,
                        point.maxRssi,
                        point.sampleCount,
                    ),
                )
                append(" · ")
                append(stringResource(R.string.wifi_survey_point_channel, point.channel))
                point.bssid?.let {
                    append(" · ")
                    append(stringResource(R.string.wifi_survey_point_ap, apShortName(it)))
                }
            },
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 2.dp),
        )
    }
}

/** Signal banding mapped onto the app's three-state status palette. */
@Composable
fun SignalQuality.color(): Color = when (this) {
    SignalQuality.EXCELLENT, SignalQuality.GOOD -> LocalStatusColors.current.pass
    SignalQuality.FAIR -> LocalStatusColors.current.warn
    SignalQuality.WEAK, SignalQuality.UNUSABLE -> LocalStatusColors.current.fail
}
