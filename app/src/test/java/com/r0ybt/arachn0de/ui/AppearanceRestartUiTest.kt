package com.r0ybt.arachn0de.ui

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import com.r0ybt.arachn0de.Arachn0deApplication
import com.r0ybt.arachn0de.MainActivity
import com.r0ybt.arachn0de.ui.theme.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.junit.Rule
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application=com.r0ybt.arachn0de.security.LegacyUiTestApplication::class,sdk = [28])
class AppearanceRestartUiTest {
    private val compose = createAndroidComposeRule<MainActivity>()
    private lateinit var app: Arachn0deApplication
    @get:Rule val rules: RuleChain = RuleChain.outerRule(object : ExternalResource() {
        override fun before() {
            app = ApplicationProvider.getApplicationContext()
            val prefs = AppearancePreferences(app.getSharedPreferences(AppearancePreferences.FILE, 0).apply { edit().clear().commit() })
            prefs.setTheme(AppearanceTheme.HeavyGold); prefs.setTextSize(AppearanceTextSize.Medium)
        }
        override fun after() { app.database.close() }
    }).around(compose)
    private fun layout(tag: String): androidx.compose.ui.text.TextLayoutResult {
        val layouts = mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
        compose.onNodeWithTag(tag).performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.GetTextLayoutResult) { it(layouts) }
        return layouts.single()
    }
    @Test fun startupUsesStoredPaletteAndActivityRestartRetainsBothChoices() {
        compose.waitUntil(10000) { compose.onAllNodesWithContentDescription("Abrir menú").fetchSemanticsNodes().isNotEmpty() }
        assertEquals(AppearanceTheme.HeavyGold.palette.muted, layout("app-bar-title").layoutInput.style.color)
        compose.onNodeWithContentDescription("Abrir menú").performClick()
        compose.onNodeWithText("Apariencia").performScrollTo().performClick()
        compose.onNodeWithTag("appearance-preview").performScrollTo()
        assertEquals(16f, layout("appearance-preview-title").layoutInput.style.fontSize.value, .01f)
        compose.onNodeWithTag("appearance-theme:KingCrimson").performScrollTo().performClick()
        compose.onNodeWithTag("appearance-size:Large").performScrollTo().performClick()
        compose.activityRule.scenario.recreate()
        compose.onNodeWithTag("appearance-theme:KingCrimson").performScrollTo().assertIsSelected()
        compose.onNodeWithTag("appearance-size:Large").performScrollTo().assertIsSelected()
        compose.onNodeWithTag("appearance-preview").performScrollTo()
        assertEquals(18f, layout("appearance-preview-title").layoutInput.style.fontSize.value, .01f)
        assertEquals(AppearanceTheme.KingCrimson.palette.secondary, layout("appearance-preview-title").layoutInput.style.color)
    }
}
