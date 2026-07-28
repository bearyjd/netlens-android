package com.ventouxlabs.netlens.feature.wifi.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.ventouxlabs.netlens.feature.wifi.model.SignalQuality

/**
 * The walk trail: recent RSSI readings as a left-to-right line, newest at the right edge.
 * Its job is to show which direction signal is moving as you walk, so the axis is fixed to the
 * survey's -95..-30 dBm window rather than auto-scaled — an auto-scaled trail would look
 * identical in a great room and a terrible one.
 */
@Composable
fun SignalTrail(
    trail: List<Int>,
    modifier: Modifier = Modifier,
) {
    val lineColor = MaterialTheme.colorScheme.primary
    val gridColor = MaterialTheme.colorScheme.outlineVariant

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(72.dp),
    ) {
        // -67 dBm is the practical "good enough" line; drawing it gives the trail a reference
        // the eye can read at a glance while walking.
        val thresholdY = size.height * (1f - SignalQuality.normalize(SignalQuality.GOOD.minRssi))
        drawLine(
            color = gridColor,
            start = Offset(0f, thresholdY),
            end = Offset(size.width, thresholdY),
            strokeWidth = 1.dp.toPx(),
        )

        if (trail.size < 2) return@Canvas

        val stepX = size.width / (trail.size - 1).toFloat()
        val path = Path()
        trail.forEachIndexed { index, rssi ->
            val x = index * stepX
            val y = size.height * (1f - SignalQuality.normalize(rssi))
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(
            path = path,
            brush = Brush.horizontalGradient(
                listOf(lineColor.copy(alpha = 0.25f), lineColor),
            ),
            style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round),
        )
    }
}
