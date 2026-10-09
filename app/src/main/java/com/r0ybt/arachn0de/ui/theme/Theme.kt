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
fun Arachn0deTheme(preferences: AppearancePreferences? = null, locked:Boolean=false, content: @Composable () -> Unit) {
    val context = LocalContext.current.applicationContext
    val privateContext=(context as? com.r0ybt.arachn0de.Arachn0deApplication)?.security?.current?.value?.context ?: context
    val resolved = if(locked || (context is com.r0ybt.arachn0de.Arachn0deApplication && privateContext===context)) null else preferences ?: remember(privateContext) { AppearancePreferences(privateContext.getSharedPreferences(AppearancePreferences.FILE, android.content.Context.MODE_PRIVATE)) }
    DisposableEffect(resolved) {
        val listener=android.content.SharedPreferences.OnSharedPreferenceChangeListener {_,_->resolved?.reload()}
        resolved?.observe(listener)
        onDispose {resolved?.unobserve(listener)}
    }
    val settings = resolved?.settings ?: AppearanceSettings()
    CompositionLocalProvider(LocalAppearancePreferences provides resolved, LocalAppearancePalette provides settings.theme.palette,
        LocalContentSizes provides contentSizes(settings.textSize)) {
        MaterialTheme(colorScheme = settings.theme.palette.materialColors(), typography = appearanceTypography(settings.textSize), content = content)
    }
}
