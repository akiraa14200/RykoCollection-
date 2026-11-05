package com.rykocollection.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val RykoColors = darkColorScheme(
    primary = Color(0xFFA99BFF),
    onPrimary = Color(0xFF100D1D),
    secondary = Color(0xFFB9B0E8),
    background = Color(0xFF000000),
    surface = Color(0xFF0C0C0C),
    surfaceVariant = Color(0xFF15151B),
    onBackground = Color(0xFFF7F7FB),
    onSurface = Color(0xFFF7F7FB),
    onSurfaceVariant = Color(0xFFA1A1AA),
    outline = Color(0x26FFFFFF)
)

@Composable
fun RykoTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = RykoColors,
        content = content
    )
}
