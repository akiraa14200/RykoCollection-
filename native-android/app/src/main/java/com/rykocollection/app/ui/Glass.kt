package com.rykocollection.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Lightweight native glass material for Compose.
 *
 * Uses layered translucent gradients, a specular top edge and a soft lower
 * edge instead of continuously blurring the screen. This keeps scrolling and
 * video playback responsive on mid/low-end Android devices.
 */
enum class RykoGlassLevel(val fill: Color, val highlight: Float, val radius: Dp) {
    Primary(Color(0xD91A1728), 0.34f, 24.dp),
    Secondary(Color(0xB31B1A2B), 0.25f, 20.dp),
    Floating(Color(0xED211D32), 0.42f, 22.dp),
    Subtle(Color(0x801B1A2B), 0.18f, 14.dp)
}

fun Modifier.rykoGlass(
    cornerRadius: Dp = 20.dp,
    tint: Color = RykoGlassLevel.Secondary.fill,
    highlight: Float = 0.25f
): Modifier {
    val shape = RoundedCornerShape(cornerRadius)
    return this
        .shadow(12.dp, shape, clip = false, ambientColor = Color(0x55211A3D), spotColor = Color(0x77211A3D))
        .clip(shape)
        .background(
            Brush.linearGradient(
                colors = listOf(
                    Color.White.copy(alpha = highlight * 0.22f),
                    tint,
                    Color(0xA6100F1B),
                    Color(0xC50C0B13)
                )
            )
        )
        .border(
            width = 1.dp,
            brush = Brush.linearGradient(
                colors = listOf(
                    Color.White.copy(alpha = highlight),
                    Color(0x35C9BEFF),
                    Color(0x14FFFFFF),
                    Color(0x24FFFFFF)
                )
            ),
            shape = shape
        )
}
