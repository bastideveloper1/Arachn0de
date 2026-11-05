package com.r0ybt.arachn0de.ui

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.r0ybt.arachn0de.ui.theme.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class) @Config(application=Application::class,sdk=[28],qualifiers="w411dp-h891dp")
class SkinRetirementUiTest {
    @get:Rule val compose=createAndroidComposeRule<ComponentActivity>()
    @Test fun appearanceHasOnlyOfficialChoicesAndPreservesOtherSettingsWithLegacyData() {
        val storage=compose.activity.getSharedPreferences("retired-ui",0)
        storage.edit().putString("custom_theme","retired").putString("custom_themes","not JSON: not consumed by UI").commit()
        val preferences=AppearancePreferences(storage)
        compose.setContent {Arachn0deTheme(preferences) {AppearanceScreen({},preferences)}}
        listOf("Crear tema","Crear copia personalizada","Importar tema","Exportar tema","Editar tema").forEach {compose.onNodeWithText(it).assertDoesNotExist()}
        for(theme in listOf(AppearanceTheme.NightBlue,AppearanceTheme.GreenBash,AppearanceTheme.Arachn0de)) {
            compose.onNodeWithTag("appearance-theme:${theme.name}").performScrollTo().performClick()
            compose.waitUntil(5000) {preferences.settings.theme==theme}
            assertEquals(theme,AppearancePreferences(storage).settings.theme)
        }
        compose.onNodeWithText("Tamaño de texto").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("appearance-size:Large").performScrollTo().performClick()
        compose.waitUntil(5000) {preferences.settings.textSize==AppearanceTextSize.Large}
        assertTrue(storage.contains("custom_themes"))
    }
}
