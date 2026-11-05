package com.r0ybt.arachn0de.ui.theme

import kotlinx.coroutines.launch

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.*
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.platform.LocalContext

internal fun AppearancePalette.materialColors(): androidx.compose.material3.ColorScheme {
    val base = (if(light) lightColorScheme() else darkColorScheme()).copy(
    primary = primary, onPrimary = background, primaryContainer = accentSurface, onPrimaryContainer = text,
    // Preserve the current Material secondary treatment in the default palette.
    secondary = if (this == AppearanceTheme.Arachn0de.palette) primary else secondary,
    onSecondary = background, secondaryContainer = selection, onSecondaryContainer = text,
    tertiary = secondary, onTertiary = background,
    background = background, onBackground = text, surface = surface, onSurface = text,
    surfaceVariant = controls, onSurfaceVariant = muted, surfaceTint = primary,
    outline = outline, outlineVariant = outline, error = semantic(SemanticColors.Error), onError = background,
    )
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
    val vault=(context as? com.r0ybt.arachn0de.Arachn0deApplication)?.security?.current?.value
    val resolved = if(locked || (context is com.r0ybt.arachn0de.Arachn0deApplication && privateContext===context)) null else preferences ?: vault?.appearance ?: remember(privateContext) {
        AppearancePreferences(privateContext.getSharedPreferences(AppearancePreferences.FILE, android.content.Context.MODE_PRIVATE))
    }
    val reloadScope=androidx.compose.runtime.rememberCoroutineScope()
    DisposableEffect(resolved,vault) {
        val changes=kotlinx.coroutines.channels.Channel<Unit>(kotlinx.coroutines.channels.Channel.CONFLATED)
        val reload=(vault?.scope ?: reloadScope).launch(kotlinx.coroutines.Dispatchers.IO) {
            for(change in changes) {
                try {resolved?.reload()}
                catch(cancelled:kotlinx.coroutines.CancellationException) {throw cancelled}
                catch(_:Exception) {if(vault?.access?.caches?.isOpen!=false) android.util.Log.w("Appearance","No se pudo actualizar la apariencia; se conserva el estado anterior.")}
            }
        }
        val listener=android.content.SharedPreferences.OnSharedPreferenceChangeListener {_,_->changes.trySend(Unit);Unit}
        resolved?.observe(listener)
        changes.trySend(Unit)
        onDispose {resolved?.unobserve(listener);changes.close();reload.cancel()}
    }
    val settings = resolved?.settings ?: AppearanceSettings(theme=UnlockAppearanceCache.read(context))
    val activity=LocalContext.current.let {source->generateSequence(source) {(it as? android.content.ContextWrapper)?.baseContext}.filterIsInstance<android.app.Activity>().firstOrNull()}
    SideEffect {
        if(!locked && (context as? com.r0ybt.arachn0de.Arachn0deApplication)?.security?.current?.value?.primary==true) UnlockAppearanceCache.update(context,settings.theme)
        activity?.let {
            val bars=androidx.core.view.WindowCompat.getInsetsController(it.window,it.window.decorView)
            bars.isAppearanceLightStatusBars=settings.theme.applicationPalette.light
            bars.isAppearanceLightNavigationBars=settings.theme.applicationPalette.light
        }
    }
    val palette=settings.palette
    val imageBitmaps=remember(privateContext,locked) {CardPhotoBitmaps((privateContext as? com.r0ybt.arachn0de.security.VaultContext)?.session?.caches)}
    DisposableEffect(imageBitmaps) {onDispose {imageBitmaps.dispose()}}
    CompositionLocalProvider(LocalCardPhotoBitmaps provides imageBitmaps,LocalAppearancePreferences provides resolved, LocalAppearancePalette provides palette,
        LocalContentSizes provides contentSizes(settings.textSize)) {
        MaterialTheme(colorScheme = palette.materialColors().copy(background=palette.background), typography = appearanceTypography(settings.textSize)) {
            content()
        }
    }
}
