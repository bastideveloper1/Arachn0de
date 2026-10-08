package com.r0ybt.arachn0de.ui.theme

import android.app.Application
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class AppearancePreferencesTest {
    private val storage get() = RuntimeEnvironment.getApplication().getSharedPreferences(AppearancePreferences.FILE, 0)
    @Test fun exactlyFiveThemesAndThreeSizesDefaultToCurrentAppearance() {
        storage.edit().clear().commit()
        assertEquals(listOf("Arachn0de", "King Crimson", "Deep Moss", "Heavy Gold", "Minimalist"), AppearanceTheme.entries.map { it.label })
        assertEquals(listOf("Pequeña", "Mediana", "Grande"), AppearanceTextSize.entries.map { it.label })
        assertEquals(AppearanceSettings(), AppearancePreferences(storage).settings)
    }
    @Test fun everyThemeAndSizePersistAndRestoreBeforeUiIsDrawn() {
        val preferences = AppearancePreferences(storage)
        AppearanceTheme.entries.forEach { theme ->
            preferences.setTheme(theme)
            AppearanceTextSize.entries.forEach { size ->
                preferences.setTextSize(size)
                assertEquals(AppearanceSettings(theme, size), preferences.settings)
                assertEquals(preferences.settings, AppearancePreferences(storage).settings)
            }
        }
    }
    @Test fun unknownOrWrongTypedPreferencesHaveSafeDefaults() {
        storage.edit().putString("theme", "missing").putInt("text_size", 99).commit()
        assertEquals(AppearanceSettings(), AppearancePreferences(storage).settings)
    }
    @Test fun designedSizesKeepSmallValuesAndCompactControlsUnscaled() {
        assertEquals(ContentSizes(14,16,12,14,17,14,20,12,11,24,15), contentSizes(AppearanceTextSize.Small))
        val medium = contentSizes(AppearanceTextSize.Medium); val large = contentSizes(AppearanceTextSize.Large)
        assertEquals(16, medium.cardTitle); assertEquals(14, medium.description); assertEquals(20, medium.projectTitle)
        assertEquals(18, large.cardTitle); assertEquals(16, large.description); assertEquals(21, large.projectTitle)
        assertEquals(13, large.smallMetadata)
        AppearanceTextSize.entries.forEach {
            val typography = appearanceTypography(it)
            assertEquals(Typography.labelLarge, typography.labelLarge)
            assertEquals(Typography.labelSmall, typography.labelSmall)
        }
        assertEquals(Typography, appearanceTypography(AppearanceTextSize.Small))
    }
    private fun contrast(a: Color, b: Color): Float {
        val x = a.luminance(); val y = b.luminance()
        return (maxOf(x,y) + .05f) / (minOf(x,y) + .05f)
    }
    @Test fun everyPaletteHasReadableContentLinksAndDistinctSemanticColors() {
        AppearanceTheme.entries.forEach { theme ->
            val p = theme.palette
            listOf(p.text,p.muted,p.primary,p.secondary,SemanticColors.Error,SemanticColors.HighPriority,SemanticColors.ImageLink,SemanticColors.SprintText,SemanticColors.CompletedText).forEach {
                assertTrue("${theme.label}: $it", contrast(it,p.surface) >= 4.5f)
            }
            assertTrue(contrast(p.onSolidPrimary,p.solidPrimary) >= 4.5f)
            assertEquals(SemanticColors.Error, p.materialColors().error)
            assertNotEquals(p.primary, SemanticColors.Error); assertNotEquals(p.secondary, SemanticColors.Error)
            if (theme != AppearanceTheme.Arachn0de) assertNotEquals(p.secondary, SemanticColors.HighPriority)
            assertEquals(Color(0xFF83CFFF), SemanticColors.ImageLink)
            assertEquals(Color(0xFF7E61FF), SemanticColors.SprintDoing)
        }
    }
    @Test fun minimalistHasNoDecorativeColorInPaletteOrMaterialSurfaces() {
        val p = AppearanceTheme.Minimalist.palette
        val colors = listOf(p.background,p.middle,p.end,p.scrim,p.surface,p.raised,p.text,p.muted,p.primary,p.secondary,p.solidPrimary,p.outline,p.controls,p.selection,p.accentSurface)
        val scheme = p.materialColors()
        (colors + listOf(scheme.primary,scheme.secondary,scheme.tertiary,scheme.surfaceContainer,scheme.surfaceContainerHigh,scheme.surfaceContainerLow,scheme.surfaceContainerHighest,scheme.surfaceTint,scheme.primaryFixed,scheme.secondaryFixed)).forEach {
            assertEquals(it.red, it.green, .0001f); assertEquals(it.red, it.blue, .0001f)
        }
    }
}
