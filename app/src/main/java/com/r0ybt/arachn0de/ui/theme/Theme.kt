package com.r0ybt.arachn0de.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext

internal fun AppearancePalette.materialColors(): androidx.compose.material3.ColorScheme {
    val base = darkColorScheme(
    primary = primary, onPrimary = background, primaryContainer = accentSurface, onPrimaryContainer = text,
    // Preserve the current Material secondary treatment in the default palette.
    secondary = if (this == AppearanceTheme.Arachn0de.palette) primary else secondary,
    onSecondary = background, secondaryContainer = selection, onSecondaryContainer = text,
    tertiary = secondary, onTertiary = background,
    background = background, onBackground = text, surface = surface, onSurface = text,
    surfaceVariant = controls, onSurfaceVariant = muted, surfaceTint = primary,
    outline = muted, outlineVariant = outline, error = SemanticColors.Error, onError = background,
    )
    if (this == AppearanceTheme.Arachn0de.palette) return base
    // Cover Material surface/fixed roles too: custom palettes never inherit decorative violet.
    return base.copy(tertiaryContainer = accentSurface, onTertiaryContainer = text,
        inverseSurface = text, inverseOnSurface = background, inversePrimary = solidPrimary, surfaceDim = background, surfaceBright = controls, surfaceContainerLowest = background,
        surfaceContainerLow = raised, surfaceContainer = surface, surfaceContainerHigh = controls, surfaceContainerHighest = selection,
        primaryFixed = primary, primaryFixedDim = primary, onPrimaryFixed = background, onPrimaryFixedVariant = background,
        secondaryFixed = secondary, secondaryFixedDim = secondary, onSecondaryFixed = background, onSecondaryFixedVariant = background,
        tertiaryFixed = secondary, tertiaryFixedDim = secondary, onTertiaryFixed = background, onTertiaryFixedVariant = background)
}

@Composable
fun Arachn0deTheme(preferences: AppearancePreferences? = null, content: @Composable () -> Unit) {
    val context = LocalContext.current.applicationContext
    val resolved = preferences ?: remember(context) { AppearancePreferences(context.getSharedPreferences(AppearancePreferences.FILE, android.content.Context.MODE_PRIVATE)) }
    val settings = resolved.settings
    CompositionLocalProvider(LocalAppearancePreferences provides resolved, LocalAppearancePalette provides settings.theme.palette,
        LocalContentSizes provides contentSizes(settings.textSize)) {
        MaterialTheme(colorScheme = settings.theme.palette.materialColors(), typography = appearanceTypography(settings.textSize), content = content)
    }
}
