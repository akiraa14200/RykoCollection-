package com.rykocollection.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Reusable glass treatment: translucent layered tint, a fine highlight edge,
 * and a subtle lower edge for depth. This avoids expensive per-frame blur.
 */
fun Modifier.rykoGlass(
    cornerRadius: Dp = 20.dp,
    tint: Color = Color(0xB31B1A2B)
): Modifier {
    val shape = RoundedCornerShape(cornerRadius)
    return this
        .clip(shape)
        .background(
            Brush.linearGradient(
                colors = listOf(
                    tint.copy(alpha = 0.94f),
                    Color(0x8C29263D),
                    Color(0xA611111C)
                )
            )
        )
        .border(
            width = 1.dp,
            brush = Brush.linearGradient(
                colors = listOf(
                    Color(0x66FFFFFF),
                    Color(0x22C8BFFF),
                    Color(0x12FFFFFF)
                )
            ),
            shape = shape
        )
}
