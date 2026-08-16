package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val CameraDarkColorScheme = darkColorScheme(
    primary = AmberGold,
    onPrimary = ObsidianBlack,
    primaryContainer = DarkSurfaceElevated,
    onPrimaryContainer = AmberGoldLight,
    secondary = CyanAccent,
    onSecondary = ObsidianBlack,
    background = ObsidianBlack,
    onBackground = TextPrimary,
    surface = DarkSurface,
    onSurface = TextPrimary,
    surfaceVariant = DarkSurfaceElevated,
    onSurfaceVariant = TextSecondary,
    error = LensRed,
    onError = Color.White
)

@Composable
fun MyApplicationTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = CameraDarkColorScheme,
        typography = Typography,
        content = content
    )
}

