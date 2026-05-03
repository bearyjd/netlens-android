package com.ventoux.netlens.ui.home.latency

import androidx.compose.foundation.Canvas
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

@Composable
fun LatencyChart(
    dataPoints: List<LatencyDataPoint>,
    thresholdMs: Int,
    modifier: Modifier = Modifier,
    lineColor: Color = MaterialTheme.colorScheme.primary,
    errorColor: Color = MaterialTheme.colorScheme.error,
    gridColor: Color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
) {
    Canvas(modifier = modifier) {
        if (dataPoints.isEmpty()) return@Canvas

        val canvasWidth = size.width
        val canvasHeight = size.height
        val strokeWidth = 2.dp.toPx()
        val gridStrokeWidth = 0.5.dp.toPx()

        val latencies = dataPoints.mapNotNull { it.latencyMs }
        val maxLatency = if (latencies.isEmpty()) {
            thresholdMs.toFloat() * 1.2f
        } else {
            maxOf(latencies.max(), thresholdMs.toFloat()) * 1.2f
        }.coerceAtLeast(10f)

        // Draw subtle horizontal grid lines
        val gridLines = 4
        for (i in 0..gridLines) {
            val y = canvasHeight * i / gridLines
            drawLine(
                color = gridColor,
                start = Offset(0f, y),
                end = Offset(canvasWidth, y),
                strokeWidth = gridStrokeWidth,
            )
        }

        // Draw dashed threshold line
        val thresholdY = canvasHeight - (thresholdMs.toFloat() / maxLatency) * canvasHeight
        if (thresholdY >= 0f && thresholdY <= canvasHeight) {
            drawLine(
                color = errorColor.copy(alpha = 0.6f),
                start = Offset(0f, thresholdY),
                end = Offset(canvasWidth, thresholdY),
                strokeWidth = strokeWidth,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(8.dp.toPx(), 4.dp.toPx())),
            )
        }

        // Draw the latency line
        if (dataPoints.size < 2) return@Canvas

        val xStep = canvasWidth / (dataPoints.size - 1).toFloat()

        fun latencyToY(latencyMs: Float): Float =
            canvasHeight - (latencyMs / maxLatency) * canvasHeight

        // Draw segments — gap on timeouts, color-coded per threshold
        var segmentStart: Int? = null

        fun drawSegment(from: Int, to: Int) {
            if (from >= to) return
            val path = Path()
            var moved = false
            for (i in from..to) {
                val point = dataPoints[i]
                val latency = point.latencyMs ?: continue
                val x = i * xStep
                val y = latencyToY(latency)
                if (!moved) {
                    path.moveTo(x, y)
                    moved = true
                } else {
                    path.lineTo(x, y)
                }
            }
            // Determine color: above threshold = error color
            val segmentLatencies = (from..to).mapNotNull { dataPoints[it].latencyMs }
            val color = if (segmentLatencies.any { it > thresholdMs }) errorColor else lineColor
            drawPath(
                path = path,
                color = color,
                style = Stroke(
                    width = strokeWidth,
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round,
                ),
            )
        }

        for (i in dataPoints.indices) {
            val point = dataPoints[i]
            if (point.latencyMs != null) {
                if (segmentStart == null) segmentStart = i
            } else {
                segmentStart?.let { drawSegment(it, i - 1) }
                segmentStart = null
            }
        }
        segmentStart?.let { drawSegment(it, dataPoints.lastIndex) }
    }
}
