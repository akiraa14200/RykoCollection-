package com.rykocollection.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.hazeEffect

/** Shared source for live, source-backed frosted surfaces across Ryko. */
val LocalRykoHazeState = staticCompositionLocalOf<HazeState?> { null }

enum class RykoGlassLevel(val fill: Color, val highlight: Float, val radius: Dp) {
    Primary(Color(0x331A1A1D), 0.34f, 24.dp),
    Secondary(Color(0x291B1B1F), 0.28f, 20.dp),
    Floating(Color(0x3D202024), 0.40f, 22.dp),
    Subtle(Color(0x1F1B1B1F), 0.20f, 14.dp)
}

/**
 * Haze 1.7 provides live source-backed blur without requiring the newer
 * AGP 9 / compile SDK 37 toolchain. The tint and fine border create Ryko's
 * dark Apple-inspired frosted finish above the captured, blurred backdrop.
 */
@Composable
fun Modifier.rykoGlass(
    cornerRadius: Dp = 20.dp,
    surfaceTint: Color = RykoGlassLevel.Secondary.fill,
    highlight: Float = 0.28f
): Modifier {
    val hazeState = LocalRykoHazeState.current
    val glassShape = RoundedCornerShape(cornerRadius)
    val safeHighlight = highlight.coerceIn(0.08f, 0.65f)
    val blurStyle = HazeStyle(
        backgroundColor = Color.Transparent,
        blurRadius = 26.dp,
        noiseFactor = 0.08f
    )

    val base = this
        .shadow(12.dp, glassShape, clip = false, ambientColor = Color(0x33000000), spotColor = Color(0x44000000))
        .clip(glassShape)

    return if (hazeState != null) {
        base
            .hazeEffect(state = hazeState, style = blurStyle)
            .background(surfaceTint.copy(alpha = (surfaceTint.alpha * 0.72f).coerceIn(0.06f, 0.22f)), glassShape)
            .border(0.8.dp, Color.White.copy(alpha = safeHighlight * 0.34f), glassShape)
    } else {
        base.background(surfaceTint.copy(alpha = 0.16f), glassShape)
            .border(0.8.dp, Color.White.copy(alpha = 0.14f), glassShape)
    }
}
