package com.rykocollection.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Ryko's shared frosted material.
 *
 * Compose's Modifier.blur blurs the composable it is applied to, not the
 * pixels behind it. This material therefore uses a deliberately cloudy,
 * high-opacity base, soft colour diffusion, layered edge reflections and
 * depth shadows instead of pretending to provide backdrop blur. The static
 * treatment stays inexpensive while lists scroll and videos play.
 */
enum class RykoGlassLevel(val fill: Color, val highlight: Float, val radius: Dp) {
    Primary(Color(0xE61A1A1D), 0.30f, 24.dp),
    Secondary(Color(0xD91B1B1F), 0.24f, 20.dp),
    Floating(Color(0xF0202024), 0.36f, 22.dp),
    Subtle(Color(0xB31B1B1F), 0.17f, 14.dp)
}

/**
 * Apply consistently to interactive surfaces, cards, panels and overlays.
 * Keep artwork and thumbnails outside this modifier so source images remain sharp.
 */
fun Modifier.rykoGlass(
    cornerRadius: Dp = 20.dp,
    tint: Color = RykoGlassLevel.Secondary.fill,
    highlight: Float = 0.25f
): Modifier {
    val shape = RoundedCornerShape(cornerRadius)
    val safeHighlight = highlight.coerceIn(0.08f, 0.5f)
    return this
        .shadow(
            elevation = 14.dp,
            shape = shape,
            clip = false,
            ambientColor = Color(0x55000000),
            spotColor = Color(0x77000000)
        )
        .clip(shape)
        // A cloudy base keeps text readable over both dark and image-heavy content.
        .background(
            Brush.linearGradient(
                colorStops = arrayOf(
                    0.0f to Color(0xF02B2B2F),
                    0.18f to Color(0xE61B1B1F),
                    0.62f to Color(0xE3151518),
                    1.0f to Color(0xF00A0A0C)
                )
            )
        )
        // Soft, static diffusion gives the surface a frosted-light appearance
        // without continuously rendering a costly animated blur.
        .background(
            Brush.radialGradient(
                colors = listOf(
                    Color(0x20FFFFFF),
                    Color(0x08FFFFFF),
                    Color.Transparent
                ),
                center = Offset.Zero,
                radius = 520f
            )
        )
        .drawBehind {
            // Inner top-edge reflection and a subtle lower shadow tint.
            drawRoundRect(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color.White.copy(alpha = safeHighlight * 0.24f),
                        Color.Transparent,
                        Color(0x16000000)
                    )
                )
            )
        }
        .border(
            width = 1.dp,
            brush = Brush.linearGradient(
                colorStops = arrayOf(
                    0.0f to Color.White.copy(alpha = safeHighlight * 0.72f),
                    0.28f to Color(0x24FFFFFF),
                    0.72f to Color(0x1EFFFFFF),
                    1.0f to Color(0x30FFFFFF)
                )
            ),
            shape = shape
        )
}
