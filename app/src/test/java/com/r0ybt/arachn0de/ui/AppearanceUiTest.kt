package com.r0ybt.arachn0de.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.r0ybt.arachn0de.Arachn0deApplication
import com.r0ybt.arachn0de.ui.theme.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class AppearanceUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private fun preferences() = AppearancePreferences(compose.activity.getSharedPreferences("appearance_ui_test", 0).apply { edit().clear().commit() })
    private fun titleLayout(): androidx.compose.ui.text.TextLayoutResult {
        val layouts = mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
        compose.onNodeWithTag("appearance-preview-title").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.GetTextLayoutResult) { it(layouts) }
        return layouts.single()
    }
    @Test fun previewRespondsToAllThemesAndSizesWithoutDatabaseWrites() {
        val prefs = preferences()
        var observed: androidx.compose.ui.graphics.Color? = null
        compose.setContent { Arachn0deTheme(prefs) {
            observed = Arachn0deColors.PathHighlight
            AppearanceScreen({})
        } }
        AppearanceTheme.entries.forEach { theme ->
            compose.onNodeWithTag("appearance-theme:${theme.name}").performScrollTo().performClick().assertIsSelected()
            compose.runOnIdle { assertEquals(theme.palette.secondary, observed) }
            compose.onNodeWithTag("appearance-preview").performScrollTo()
            assertEquals(theme.palette.secondary, titleLayout().layoutInput.style.color)
        }
        AppearanceTextSize.entries.forEach { size ->
            compose.onNodeWithTag("appearance-size:${size.name}").performScrollTo().performClick().assertIsSelected()
            compose.onNodeWithTag("appearance-preview").performScrollTo()
            assertEquals(contentSizes(size).cardTitle.toFloat(), titleLayout().layoutInput.style.fontSize.value, .01f)
        }
        compose.onNodeWithText("Guardar").assertDoesNotExist()
        val restored = AppearancePreferences(compose.activity.getSharedPreferences("appearance_ui_test", 0))
        assertEquals(prefs.settings, restored.settings)
        // This screen has no repository dependency; no database was opened to draw its preview.
        val app = compose.activity.application as Arachn0deApplication
        assertFalse(app.getDatabasePath("arachn0de.db").exists())
    }
    @Test fun androidFontScaleIsPreservedAndLargeRemainsScrollableOnSmallScreens() {
        val prefs = preferences().apply { setTextSize(AppearanceTextSize.Large) }
        var fontScale = 0f
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f, 1.6f)) {
                Arachn0deTheme(prefs) {
                    fontScale = LocalDensity.current.fontScale
                    Box(Modifier.requiredSize(320.dp, 480.dp)) { AppearanceScreen({}) }
                }
            }
        }
        assertEquals(1.6f, fontScale, .01f)
        assertEquals(18f, titleLayout().layoutInput.style.fontSize.value, .01f)
        compose.onNodeWithTag("appearance-size:Large").performScrollTo().assertIsDisplayed().assertIsSelected()
        compose.onNodeWithTag("appearance-theme:Minimalist").performScrollTo().performClick()
        compose.onNodeWithTag("appearance-preview").performScrollTo().assertIsDisplayed()
    }
    @Test fun drawerOpensAppearanceAndBackReturnsWithoutLosingSelection() {
        val app = compose.activity.application as Arachn0deApplication
        val prefs = preferences()
        compose.setContent { Arachn0deTheme(prefs) { AppSafeArea { AppRoot(app.projectRepository, app.nodeRepository) } } }
        compose.waitUntil(10000) { compose.onAllNodesWithContentDescription("Abrir menú").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithContentDescription("Abrir menú").performClick()
        compose.onNodeWithText("Configuración").assertDoesNotExist()
        compose.onNodeWithText("Apariencia").performScrollTo().performClick()
        compose.onNodeWithTag("appearance-theme:DeepMoss").performScrollTo().performClick()
        compose.onNodeWithTag("appearance-size:Medium").performScrollTo().performClick()
        compose.onNodeWithText("Apariencia").performScrollTo()
        compose.onNodeWithContentDescription("Volver").performClick()
        compose.onNodeWithContentDescription("Abrir menú").performClick()
        compose.onNodeWithText("Apariencia").performScrollTo().performClick()
        compose.onNodeWithTag("appearance-theme:DeepMoss").assertIsSelected()
        compose.onNodeWithTag("appearance-size:Medium").performScrollTo().assertIsSelected()
    }
    @Test fun avatarUsesRoundedSquareAtExistingSize() {
        val outline = PersonAvatarShape.createOutline(androidx.compose.ui.geometry.Size(28f,28f), androidx.compose.ui.unit.LayoutDirection.Ltr, Density(1f))
        val rounded = outline as androidx.compose.ui.graphics.Outline.Rounded
        assertEquals(7f, rounded.roundRect.topLeftCornerRadius.x, .01f)
        assertTrue(rounded.roundRect.topLeftCornerRadius.x < 14f)
    }
    @Test fun realCardKeepsSemanticColorsFormattingLinksAndCompactSizesAcrossPalettes() {
        val prefs = preferences()
        val id = "00000000-0000-0000-0000-000000000009"
        val task = com.r0ybt.arachn0de.domain.model.Node("demo", "demo-project", null, "Revisar UI",
            "**Antes** *detalle* __después__ [[arachnode:image:$id]]", false, 0, 0, 0, false,
            dueAt = 1L, priority = com.r0ybt.arachn0de.domain.model.Priority.HIGH,
            sprintMode = true, workState = com.r0ybt.arachn0de.domain.model.WorkState.DOING)
        compose.setContent { Arachn0deTheme(prefs) {
            Box(Modifier.requiredSize(320.dp, 480.dp)) {
                NodeCard(task, null, false, true, {}, {}, {}, false, false, { _, done -> done() }, {},
                    now = 100000L, responsiblePeople = listOf(com.r0ybt.arachn0de.domain.model.Person("alex", "Alex")),
                    tags = listOf(com.r0ybt.arachn0de.domain.model.Tag("tag", "Desktop", "desktop")))
            }
        } }
        fun layout(text: String, substring: Boolean = false): androidx.compose.ui.text.TextLayoutResult {
            val layouts = mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
            compose.onNodeWithText(text, substring = substring, useUnmergedTree = true).performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.GetTextLayoutResult) { it(layouts) }
            return layouts.single()
        }
        AppearanceTheme.entries.forEach { theme -> AppearanceTextSize.entries.forEach { size ->
            compose.runOnIdle { prefs.setTheme(theme); prefs.setTextSize(size) }
            val title = layout("Revisar UI")
            assertEquals(theme.palette.secondary, title.layoutInput.style.color)
            assertEquals(contentSizes(size).cardTitle.toFloat(), title.layoutInput.style.fontSize.value, .01f)
            assertEquals(SemanticColors.HighPriority, layout("Prioridad alta").layoutInput.style.color)
            assertEquals(SemanticColors.Error, layout("Vencida ·", true).layoutInput.style.color)
            assertEquals(SemanticColors.SprintText, layout("Haciendo").layoutInput.style.color)
            val body = layout("Antes detalle después Imagen no disponible")
            assertTrue(body.layoutInput.text.spanStyles.any { it.item.fontWeight == androidx.compose.ui.text.font.FontWeight.Bold })
            val link = body.layoutInput.text.getLinkAnnotations(0, body.layoutInput.text.length).single().item
            assertEquals(SemanticColors.ImageLink, link.styles!!.style!!.color)
            compose.onNodeWithText("Desktop").assertExists()
            compose.onNodeWithContentDescription("Alex").assertExists()
            compose.onAllNodesWithText("arachnode:image", substring = true).assertCountEquals(0)
        } }
        assertEquals(com.r0ybt.arachn0de.domain.model.WorkState.DOING, task.workState)
    }
}
