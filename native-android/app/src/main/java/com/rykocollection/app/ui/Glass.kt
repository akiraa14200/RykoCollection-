package com.rykocollection.app.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.glass.GlassStyle
import dev.chrisbanes.haze.glass.hazeGlass

/** Shared source for true, live backdrop glass surfaces across Ryko. */
val LocalRykoHazeState = staticCompositionLocalOf<HazeState?> { null }

enum class RykoGlassLevel(val fill: Color, val highlight: Float, val radius: Dp) {
    Primary(Color(0x331A1A1D), 0.34f, 24.dp),
    Secondary(Color(0x291B1B1F), 0.28f, 20.dp),
    Floating(Color(0x3D202024), 0.40f, 22.dp),
    Subtle(Color(0x1F1B1B1F), 0.20f, 14.dp)
}

/**
 * Uses Haze's source-backed Glass renderer. Android 12+ receives live,
 * hardware-backed background blur; older Android versions receive the
 * library's translucent fallback rather than a fake opaque panel.
 */
@Composable
fun Modifier.rykoGlass(
    cornerRadius: Dp = 20.dp,
    tint: Color = RykoGlassLevel.Secondary.fill,
    highlight: Float = 0.28f
): Modifier {
    val hazeState = LocalRykoHazeState.current
    val shape = RoundedCornerShape(cornerRadius)
    val safeHighlight = highlight.coerceIn(0.08f, 0.65f)
    val style = remember(cornerRadius, tint, safeHighlight) {
        GlassStyle.regular.then {
            tint(tint.copy(alpha = (tint.alpha * 0.72f).coerceIn(0.04f, 0.24f)))
            shape(shape)
            specularIntensity(safeHighlight)
            ambientResponse(0.34f)
            edgeSoftness(1.5.dp)
        }
    }
    return if (hazeState != null) {
        this
            .shadow(12.dp, shape, clip = false, ambientColor = Color(0x33000000), spotColor = Color(0x44000000))
            .hazeGlass(input = HazeInput.Sources(hazeState), style = style)
            .border(0.8.dp, Color.White.copy(alpha = safeHighlight * 0.34f), shape)
    } else {
        this.shadow(10.dp, shape, clip = false)
            .border(0.8.dp, Color.White.copy(alpha = 0.14f), shape)
    }
}
