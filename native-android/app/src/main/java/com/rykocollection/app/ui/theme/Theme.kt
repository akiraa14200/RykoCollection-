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
    secondaryContainer = Color(0xFF28243A),
    onSecondaryContainer = Color(0xFFEAE6FF),
    background = Color(0xFF080810),
    surface = Color(0xE61A1927),
    surfaceDim = Color(0xFF090910),
    surfaceBright = Color(0xFF292638),
    surfaceContainerLowest = Color(0xFF080810),
    surfaceContainerLow = Color(0xD9141320),
    surfaceContainer = Color(0xE31B1929),
    surfaceContainerHigh = Color(0xE6232032),
    surfaceContainerHighest = Color(0xF02C293D),
    surfaceVariant = Color(0xD91C1A2B),
    onBackground = Color(0xFFF7F7FB),
    onSurface = Color(0xFFF7F7FB),
    onSurfaceVariant = Color(0xFFBDB8D0),
    outline = Color(0x45FFFFFF),
    outlineVariant = Color(0x26C8BFFF),
    error = Color(0xFFFF8E9A),
    onError = Color(0xFF260A10)
)

@Composable
fun RykoTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = RykoColors, content = content)
}
