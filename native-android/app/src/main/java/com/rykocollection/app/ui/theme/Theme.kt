package com.rykocollection.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val RykoColors = darkColorScheme(
    primary = Color(0xFFA99BFF),
    onPrimary = Color(0xFF100D1D),
    primaryContainer = Color(0xFF242428),
    onPrimaryContainer = Color(0xFFE7E0FF),
    secondary = Color(0xFFD0D0D4),
    secondaryContainer = Color(0xFF252529),
    onSecondaryContainer = Color(0xFFEAE6FF),
    background = Color(0xFF080810),
    surface = Color(0xE61A1A1D),
    surfaceDim = Color(0xFF090910),
    surfaceBright = Color(0xFF29292D),
    surfaceContainerLowest = Color(0xFF080810),
    surfaceContainerLow = Color(0xD9161619),
    surfaceContainer = Color(0xE31B1B1F),
    surfaceContainerHigh = Color(0xE6242428),
    surfaceContainerHighest = Color(0xF02C2C30),
    surfaceVariant = Color(0xD91C1C20),
    onBackground = Color(0xFFF7F7FB),
    onSurface = Color(0xFFF7F7FB),
    onSurfaceVariant = Color(0xFFBDBDC2),
    outline = Color(0x45FFFFFF),
    outlineVariant = Color(0x26FFFFFF),
    error = Color(0xFFFF8E9A),
    onError = Color(0xFF260A10)
)

@Composable
fun RykoTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = RykoColors, content = content)
}
