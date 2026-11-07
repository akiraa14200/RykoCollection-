package com.rykocollection.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val RykoColors = darkColorScheme(
    primary = Color(0xFFA99BFF),
    onPrimary = Color(0xFF100D1D),
    primaryContainer = Color(0xFF332B55),
    onPrimaryContainer = Color(0xFFE7E0FF),
    secondary = Color(0xFFC5BDF4),
    background = Color(0xFF080810),
    surface = Color(0xCC171522),
    surfaceDim = Color(0xFF090910),
    surfaceBright = Color(0xFF292638),
    surfaceContainerLowest = Color(0xFF080810),
    surfaceContainerLow = Color(0xCC14121F),
    surfaceContainer = Color(0xD91B1929),
    surfaceContainerHigh = Color(0xE6232032),
    surfaceContainerHighest = Color(0xF02C293D),
    surfaceVariant = Color(0xB31C1A2B),
    onBackground = Color(0xFFF7F7FB),
    onSurface = Color(0xFFF7F7FB),
    onSurfaceVariant = Color(0xFFBDB8D0),
    outline = Color(0x45FFFFFF),
    outlineVariant = Color(0x26C8BFFF)
)

@Composable
fun RykoTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = RykoColors, content = content)
}
