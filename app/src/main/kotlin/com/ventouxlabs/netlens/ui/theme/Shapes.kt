package com.ventouxlabs.netlens.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * NetLens shape scale: 16dp cards, 20dp sheets/dialogs, fully-rounded pill
 * chips. `small` drives Material chips, so it is a pill by design — do not
 * "fix" it back to a radius.
 */
val NetLensShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(percent = 50),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(20.dp),
)
