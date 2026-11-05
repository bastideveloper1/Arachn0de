package com.r0ybt.arachn0de.ui.theme

import android.app.Application
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.test.core.app.ApplicationProvider
import com.r0ybt.arachn0de.media.ContentGif
import com.r0ybt.arachn0de.security.EncryptedPreferences
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application=Application::class,sdk=[28])
class InstitutionalIdentityTest {
    private val themes=listOf(AppearanceTheme.DarkGrey,AppearanceTheme.PureBlack,AppearanceTheme.BlackAndWhite,AppearanceTheme.Graphite)
    @Test fun newThemesPersistAndRoundTripThroughExistingPreferencePayload() {
        val app=ApplicationProvider.getApplicationContext<Application>()
        val source=app.getSharedPreferences("identity-source",0)
        val target=app.getSharedPreferences("identity-target",0)
        source.edit().clear().commit()
        val preferences=AppearancePreferences(source)
        assertTrue(AppearanceTheme.entries.containsAll(listOf(AppearanceTheme.Arachn0de,AppearanceTheme.Dark,AppearanceTheme.GreenBash,AppearanceTheme.KingCrimson,AppearanceTheme.HeavyGold)))
        for(theme in themes) {
            preferences.setTheme(theme)
            assertEquals(theme,AppearancePreferences(source).settings.theme)
            val payload=EncryptedPreferences.encode(source.all)
            AppearancePreferences.validate(payload)
            target.edit().clear().apply {
                EncryptedPreferences.decode(payload).forEach {(key,value)->putString(key,value as String)}
            }.commit()
            assertEquals(theme,AppearancePreferences(target).settings.theme)
            UnlockAppearanceCache.update(app,theme)
            assertEquals(theme,UnlockAppearanceCache.read(app))
        }
    }
    @Test fun monochromeThemesKeepContrastAndFunctionalColors() {
        fun contrast(a:Color,b:Color)=(maxOf(a.luminance(),b.luminance())+.05f)/(minOf(a.luminance(),b.luminance())+.05f)
        assertEquals(4,themes.map {it.palette}.distinct().size)
        assertEquals(Color.Black,AppearanceTheme.PureBlack.palette.background)
        for(theme in themes+AppearanceTheme.Arachn0de) {
            val p=theme.palette
            for(c in listOf(p.background,p.surface,p.primary,p.text,p.muted)) {
                assertEquals(c.red,c.green,0.001f);assertEquals(c.green,c.blue,0.001f)
            }
            for(surface in listOf(p.background,p.surface,p.panel,p.controls)) {
                assertTrue("$theme text",contrast(p.text,surface)>=4.5f)
                assertTrue("$theme muted",contrast(p.muted,surface)>=4.5f)
            }
            assertTrue(contrast(BlackWhite90.Inactive,p.surface)>=3f)
            assertEquals(SemanticColors.Error,p.semantic(SemanticColors.Error))
            assertEquals(SemanticColors.HighPriority,p.semantic(SemanticColors.HighPriority))
        }
    }
    @Test fun bundledAnimationHasOriginalDimensionsAndMultipleFrames() {
        val app=ApplicationProvider.getApplicationContext<Application>()
        val info=app.assets.open("branding/logomovimiento.gif").use {ContentGif.inspect(it.readBytes())}
        assertEquals(ContentGif.Info(480,498,180),info)
    }
}
