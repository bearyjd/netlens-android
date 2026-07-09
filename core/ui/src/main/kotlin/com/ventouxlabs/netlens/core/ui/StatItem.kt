package com.ventouxlabs.netlens.core.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle

/**
 * Shared stat cell for live metrics; tabular figures so updating digits
 * don't reflow. Used for label-over-value readouts like ping stats and
 * speed test results. An optional [unit] renders as a third line below
 * the value (e.g. "Mbps", "ms"). [valueStyle] overrides the default
 * titleMedium value text for hero-sized readouts; tabular figures are
 * applied regardless.
 */
@Composable
fun StatItem(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    unit: String? = null,
    valueColor: Color = Color.Unspecified,
    valueStyle: TextStyle? = null,
) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = (valueStyle ?: MaterialTheme.typography.titleMedium).withTabularFigures(),
            color = valueColor,
        )
        if (unit != null) {
            Text(
                text = unit,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
