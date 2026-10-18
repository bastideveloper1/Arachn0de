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
    @Test fun availableThemesAndThreeSizesDefaultToCharcoal() {
        storage.edit().clear().commit()
        assertEquals(listOf("Charcoal", "Dark", "King Crimson", "Deep Moss", "Heavy Gold", "Azul noche", "Gris día", "Green Bash"), AppearanceTheme.entries.map { it.label })
        assertEquals(listOf("Pequeña", "Mediana", "Grande"), AppearanceTextSize.entries.map { it.label })
        assertEquals(AppearanceSettings(), AppearancePreferences(storage).settings)
    }
    @Test fun darkUsesSharedSurfacesAndPreservesBrandAndFunctionalColors() {
        val charcoal=AppearanceTheme.Arachn0de.palette
        val dark=AppearanceTheme.Dark.palette
        assertEquals(BlackWhite90.palette,charcoal)
        assertEquals(Color(0xFF141517),dark.background)
        assertEquals(Color(0xFF27292D),dark.surface)
        assertEquals(Color(0xFF1D1F22),dark.panel)
        assertEquals(dark.panel,dark.taskPanel)
        assertEquals(Color(0xFF36393E),dark.outline)
        assertEquals(charcoal.primary,dark.primary)
        assertEquals(charcoal.secondary,dark.secondary)
        assertEquals(Color(0xFFB86A2C),BlackWhite90.Copper)
        assertEquals(SemanticColors.Error,dark.semantic(SemanticColors.Error))
        assertEquals(SemanticColors.HighPriority,dark.semantic(SemanticColors.HighPriority))
        assertEquals(dark.background,dark.materialColors().background)
        assertEquals(dark.surface,dark.materialColors().surface)
        listOf(dark.background,dark.panel,dark.surface,dark.controls).forEach { surface ->
            assertNotEquals(Color.Black,surface)
            assertTrue(contrast(dark.text,surface)>=4.5f)
            assertTrue(contrast(dark.muted,surface)>=4.5f)
        }
        storage.edit().clear().commit()
        assertEquals(AppearanceTheme.Arachn0de,AppearancePreferences(storage).settings.theme)
        val settings=AppearancePreferences(storage)
        settings.setTheme(AppearanceTheme.Dark)
        assertEquals(AppearanceTheme.Dark,settings.settings.theme)
        assertEquals(AppearanceTheme.Dark,AppearancePreferences(storage).settings.theme)
        val context=RuntimeEnvironment.getApplication()
        UnlockAppearanceCache.update(context,AppearanceTheme.Dark)
        assertEquals(AppearanceTheme.Dark,UnlockAppearanceCache.read(context))
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
        assertEquals(16, medium.cardTitle); assertEquals(14, medium.description); assertEquals(19, medium.projectTitle)
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
                assertTrue("${theme.label}: $it", contrast(p.semantic(it),p.surface) >= 4.5f)
            }
            assertTrue(contrast(p.onSolidPrimary,p.solidPrimary) >= 4.5f)
            assertEquals(p.semantic(SemanticColors.Error), p.materialColors().error)
            assertNotEquals(p.primary, SemanticColors.Error); assertNotEquals(p.secondary, SemanticColors.Error)
            if (theme != AppearanceTheme.Arachn0de) assertNotEquals(p.secondary, SemanticColors.HighPriority)
            assertEquals(Color(0xFF83CFFF), SemanticColors.ImageLink)
            assertEquals(BlackWhite90.Text, SemanticColors.SprintDoing)
        }
    }
    @Test fun dayGreyHasNoPureWhiteSurfaceOrDefaultViolet() {
        val p=AppearanceTheme.DayGrey.palette;val scheme=p.materialColors()
        listOf(p.background,p.surface,p.raised,p.controls,p.selection,p.accentSurface,scheme.background,scheme.surface,scheme.surfaceContainer,scheme.surfaceContainerHigh,scheme.surfaceContainerLowest).forEach {assertNotEquals(Color.White,it)}
        assertTrue(p.light);assertEquals(p.primary,scheme.primary);assertEquals(p.secondary,scheme.secondary)
    }
    @Test fun blackWhite90UsesSoftNeutralTokensAndKeepsExistingPreferenceKey() {
        val p=AppearanceTheme.Arachn0de.palette
        assertEquals(Color(0xFF202124),p.background)
        assertEquals(Color(0xFF2B2D30),p.surface)
        assertEquals(Color(0xFFD6D6D6),p.text)
        assertEquals(p.background,p.middle);assertEquals(p.background,p.end)
        storage.edit().putString("theme","Arachn0de").commit()
        assertEquals(AppearanceTheme.Arachn0de,AppearancePreferences(storage).settings.theme)
        assertTrue(contrast(p.text,p.surface)>=4.5f)
        assertTrue(contrast(BlackWhite90.Inactive,p.surface)>=3f)
    }
}
