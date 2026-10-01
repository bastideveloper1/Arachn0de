package com.r0ybt.arachn0de.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val DarkColorScheme = darkColorScheme(
    primary = Arachn0deColors.Accent,
    onPrimary = Arachn0deColors.Background,
    primaryContainer = Arachn0deColors.AccentSurface,
    onPrimaryContainer = Arachn0deColors.TextPrimary,
    secondary = Arachn0deColors.Accent,
    onSecondary = Arachn0deColors.Background,
    secondaryContainer = Arachn0deColors.Selection,
    onSecondaryContainer = Arachn0deColors.TextPrimary,
    tertiary = Arachn0deColors.PathHighlight,
    onTertiary = Arachn0deColors.Background,
    background = Arachn0deColors.Background,
    onBackground = Arachn0deColors.TextPrimary,
    surface = Arachn0deColors.Surface,
    onSurface = Arachn0deColors.TextPrimary,
    surfaceVariant = Arachn0deColors.ControlSurface,
    onSurfaceVariant = Arachn0deColors.TextSecondary,
    surfaceTint = Arachn0deColors.Accent,
    outline = Arachn0deColors.TextSecondary,
    outlineVariant = Arachn0deColors.Outline,
    error = Arachn0deColors.Destructive,
    onError = Arachn0deColors.Background,
)

/** Dark brand theme; light and wallpaper-derived palettes are not implemented yet. */
@Composable
fun Arachn0deTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = DarkColorScheme, typography = Typography, content = content)
}
