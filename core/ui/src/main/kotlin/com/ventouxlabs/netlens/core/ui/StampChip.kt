package com.ventouxlabs.netlens.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * NetLens's signature "stamp" motif: a pill chip with a dashed border and a
 * slight -1° rotation, used for network type, connection tags, and posture
 * labels. This is the ONE recurring decorative device in the app — don't
 * introduce competing ornaments elsewhere.
 *
 * Non-interactive by design (it's a label, not a button); tints default to
 * the teal "normal" family and should always come from [LocalStatusColors]
 * so a stamp's color carries the same three-state meaning everywhere.
 */
@Composable
fun StampChip(
    text: String,
    modifier: Modifier = Modifier,
    containerColor: Color = LocalStatusColors.current.passContainer,
    contentColor: Color = LocalStatusColors.current.onPassContainer,
) {
    val shape = RoundedCornerShape(percent = 50)
    Box(
        modifier = modifier
            .rotate(degrees = -1f)
            .background(containerColor, shape)
            .drawBehind {
                val strokeWidth = 1.dp.toPx()
                val inset = strokeWidth / 2
                val dash = 4.dp.toPx()
                drawRoundRect(
                    color = contentColor.copy(alpha = 0.55f),
                    topLeft = Offset(inset, inset),
                    size = Size(size.width - strokeWidth, size.height - strokeWidth),
                    cornerRadius = CornerRadius((size.height - strokeWidth) / 2),
                    style = Stroke(
                        width = strokeWidth,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(dash, dash)),
                    ),
                )
            }
            .padding(horizontal = Spacing.md, vertical = Spacing.xs),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = contentColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
