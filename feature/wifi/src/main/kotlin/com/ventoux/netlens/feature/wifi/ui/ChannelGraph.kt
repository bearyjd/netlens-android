package com.ventoux.netlens.feature.wifi.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ventoux.netlens.feature.wifi.model.WifiNetwork
import kotlin.math.exp

@Composable
fun ChannelGraph(
    networks: List<WifiNetwork>,
    connectedBssid: String?,
    modifier: Modifier = Modifier,
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val tertiaryColor = MaterialTheme.colorScheme.tertiary
    val outlineColor = MaterialTheme.colorScheme.outline
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val textMeasurer = rememberTextMeasurer()

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(200.dp),
    ) {
        if (networks.isEmpty()) return@Canvas

        val minChannel = (networks.minOf { it.channelNumber } - 1).coerceAtLeast(0)
        val maxChannel = (networks.maxOf { it.channelNumber } + 1).coerceAtMost(250)
        val channelRange = (maxChannel - minChannel).coerceAtLeast(1)

        val padding = 40.dp.toPx()
        val graphWidth = size.width - padding * 2
        val graphHeight = size.height - padding * 2

        val minDbm = -100f
        val maxDbm = -30f
        val dbmRange = maxDbm - minDbm

        drawAxisLines(padding, graphWidth, graphHeight, outlineColor)
        drawChannelLabels(
            textMeasurer, labelColor, padding, graphWidth, graphHeight,
            minChannel, maxChannel, channelRange,
        )
        drawDbmLabels(
            textMeasurer, labelColor, padding, graphHeight,
            minDbm, maxDbm,
        )

        networks.forEach { network ->
            val isConnected = network.bssid == connectedBssid
            val color = if (isConnected) primaryColor else tertiaryColor.copy(alpha = 0.6f)
            val fillColor = color.copy(alpha = if (isConnected) 0.3f else 0.15f)

            val centerX = padding + ((network.channelNumber - minChannel).toFloat() / channelRange) * graphWidth
            val signalNorm = ((network.level - minDbm) / dbmRange).coerceIn(0f, 1f)
            val peakY = padding + graphHeight * (1f - signalNorm)

            val widthChannels = (network.channelWidth / 5).coerceAtLeast(4)
            val halfWidthPx = (widthChannels.toFloat() / channelRange) * graphWidth / 2f

            val path = buildCurvePath(centerX, peakY, halfWidthPx, padding + graphHeight)
            drawPath(path, fillColor)
            drawPath(path, color, style = Stroke(width = 2.dp.toPx()))

            val labelText = network.ssid.ifEmpty { "?" }
            val truncated = if (labelText.length > 10) labelText.take(9) + "…" else labelText
            val layoutResult = textMeasurer.measure(
                truncated,
                TextStyle(fontSize = 9.sp, color = color),
            )
            drawText(
                layoutResult,
                topLeft = Offset(
                    x = centerX - layoutResult.size.width / 2f,
                    y = peakY - layoutResult.size.height - 2.dp.toPx(),
                ),
            )
        }
    }
}

private fun buildCurvePath(
    centerX: Float,
    peakY: Float,
    halfWidthPx: Float,
    baselineY: Float,
): Path {
    val path = Path()
    val steps = 40
    for (i in 0..steps) {
        val t = (i.toFloat() / steps) * 2f - 1f
        val x = centerX + t * halfWidthPx
        val gaussianY = baselineY - (baselineY - peakY) * exp((-t * t * 4f).toDouble()).toFloat()
        if (i == 0) path.moveTo(x, gaussianY) else path.lineTo(x, gaussianY)
    }
    return path
}

private fun DrawScope.drawAxisLines(
    padding: Float,
    graphWidth: Float,
    graphHeight: Float,
    color: Color,
) {
    drawLine(
        color = color,
        start = Offset(padding, padding),
        end = Offset(padding, padding + graphHeight),
        strokeWidth = 1.dp.toPx(),
    )
    drawLine(
        color = color,
        start = Offset(padding, padding + graphHeight),
        end = Offset(padding + graphWidth, padding + graphHeight),
        strokeWidth = 1.dp.toPx(),
    )
}

private fun DrawScope.drawChannelLabels(
    textMeasurer: TextMeasurer,
    color: Color,
    padding: Float,
    graphWidth: Float,
    graphHeight: Float,
    minChannel: Int,
    maxChannel: Int,
    channelRange: Int,
) {
    val step = when {
        channelRange > 100 -> 20
        channelRange > 40 -> 10
        channelRange > 15 -> 5
        else -> 1
    }
    var ch = ((minChannel / step) + 1) * step
    while (ch <= maxChannel) {
        val x = padding + ((ch - minChannel).toFloat() / channelRange) * graphWidth
        val layoutResult = textMeasurer.measure(
            "$ch",
            TextStyle(fontSize = 9.sp, color = color),
        )
        drawText(
            layoutResult,
            topLeft = Offset(
                x = x - layoutResult.size.width / 2f,
                y = padding + graphHeight + 4.dp.toPx(),
            ),
        )
        ch += step
    }
}

private fun DrawScope.drawDbmLabels(
    textMeasurer: TextMeasurer,
    color: Color,
    padding: Float,
    graphHeight: Float,
    minDbm: Float,
    maxDbm: Float,
) {
    val dbmRange = maxDbm - minDbm
    val dbmSteps = listOf(-30, -50, -70, -90)
    dbmSteps.forEach { dbm ->
        val norm = (dbm - minDbm) / dbmRange
        val y = padding + graphHeight * (1f - norm)
        val layoutResult = textMeasurer.measure(
            "$dbm",
            TextStyle(fontSize = 8.sp, color = color),
        )
        drawText(
            layoutResult,
            topLeft = Offset(
                x = padding - layoutResult.size.width - 4.dp.toPx(),
                y = y - layoutResult.size.height / 2f,
            ),
        )
    }
}
