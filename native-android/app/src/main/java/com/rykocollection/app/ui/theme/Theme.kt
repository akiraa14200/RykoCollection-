package com.rykocollection.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val RykoColors = darkColorScheme(
    primary = Color(0xFFA99BFF),
    onPrimary = Color(0xFF100D1D),
    secondary = Color(0xFFC5BDF4),
    background = Color(0xFF080810),
    surface = Color(0xCC171722),
    surfaceDim = Color(0xFF090910),
    surfaceBright = Color(0xFF292838),
    surfaceContainerLowest = Color(0xFF080810),
    surfaceContainerLow = Color(0xCC14141F),
    surfaceContainer = Color(0xD91B1A28),
    surfaceContainerHigh = Color(0xE6232232),
    surfaceContainerHighest = Color(0xF02C2A3B),
    surfaceVariant = Color(0xB31C1B2A),
    onBackground = Color(0xFFF7F7FB),
    onSurface = Color(0xFFF7F7FB),
    onSurfaceVariant = Color(0xFFB5B1C7),
    outline = Color(0x45FFFFFF),
    outlineVariant = Color(0x26C8BFFF)
)

@Composable
fun RykoTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = RykoColors,
        content = content
    )
}
