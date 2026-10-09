package com.r0ybt.arachn0de.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.r0ybt.arachn0de.Arachn0deApplication
import com.r0ybt.arachn0de.domain.model.*
import com.r0ybt.arachn0de.ui.theme.*
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@GraphicsMode(GraphicsMode.Mode.NATIVE)
@RunWith(RobolectricTestRunner::class)
@Config(application=com.r0ybt.arachn0de.security.LegacyUiTestApplication::class,sdk = [28])
class DogfoodingPresentationTest {
    private val compose = createComposeRule()
    private lateinit var app: Arachn0deApplication
    private lateinit var project: Project
    private lateinit var layer: Node
    @get:Rule val rules: RuleChain = RuleChain.outerRule(object : ExternalResource() {
        override fun before() {
            app = ApplicationProvider.getApplicationContext()
            runBlocking {
                project = app.projectRepository.createProject("Dogfooding project")
                layer = app.nodeRepository.createNode(project.id, null, "Dogfooding layer", purpose = NodePurpose.LAYER)
            }
        }
        override fun after() { app.database.close() }
    }).around(compose)
    private fun await(text: String) { compose.waitUntil(10_000) { compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() } }
    private fun openLayer() {
        compose.setContent { Arachn0deTheme { AppSafeArea { AppRoot(app.projectRepository, app.nodeRepository) } } }
        await(project.name); compose.onNodeWithText(project.name, useUnmergedTree = true).performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("nodes-list").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("nodes-list").performScrollToNode(hasText(layer.title)); compose.onNodeWithText(layer.title, useUnmergedTree = true).performClick()
        compose.onNodeWithTag("nodes-list").performScrollToNode(hasText("CAPA 1")); await("CAPA 1")
    }
    private fun status(text: String) {
        compose.waitUntil(10_000) {
            runCatching { compose.onNodeWithTag("nodes-list").performScrollToNode(hasTestTag("layer-content-status")) }.isSuccess &&
                compose.onAllNodes(hasTestTag("layer-content-status") and hasText(text)).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("layer-content-status").assertTextEquals(text).assertIsDisplayed()
    }
    @Test fun titlesWrapAtFullWidthAndShortTitlesKeepOneLine() {
        var title by mutableStateOf("Breve")
        compose.setContent { Arachn0deTheme { Box(Modifier.width(220.dp).testTag("available-title-width")) { DetailScopeTitle(title) } } }
        fun layout(): TextLayoutResult {
            var result: TextLayoutResult? = null
            compose.onNodeWithTag("detail-scope-title").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.GetTextLayoutResult) { action ->
                val results = mutableListOf<TextLayoutResult>(); action(results); result = results.single()
            }
            return result!!
        }
        assertEquals(1, layout().lineCount)
        compose.runOnIdle { title = "Título largo con suficientes palabras para explicar toda la tarea sin truncar su contenido" }
        val result = layout()
        assertTrue("lines=${result.lineCount}", result.lineCount > 2); assertFalse("overflow=${result.hasVisualOverflow}", result.hasVisualOverflow)
        for (i in 0 until result.lineCount) assertFalse(result.isLineEllipsized(i))
        assertTrue(result.layoutInput.style.fontSize.value <= 21f)
        assertEquals(compose.onNodeWithTag("available-title-width").fetchSemanticsNode().boundsInRoot.width,
            compose.onNodeWithTag("detail-scope-title").fetchSemanticsNode().boundsInRoot.width, .1f)
    }
    @Test fun fullDetailTitleIsBelowAccessibleActionsEvenForLongLayerNames() {
        val longTitle = "Capa con un título largo que explica todos los objetivos y debe permanecer completamente legible"
        runBlocking { app.nodeRepository.updateNode(layer.id, longTitle, "") }
        layer = runBlocking { app.nodeRepository.getNode(layer.id)!! }
        openLayer()
        compose.onNodeWithTag("nodes-list").performScrollToNode(hasContentDescription("Ordenar"))
        compose.onNodeWithTag("detail-scope-title").assertTextEquals(longTitle)
        val title = compose.onNodeWithTag("detail-scope-title").fetchSemanticsNode().boundsInRoot
        for (description in listOf("Volver a la capa anterior", "Ordenar", "Opciones del elemento", "Filtros")) {
            val action = compose.onNodeWithContentDescription(description).assertIsDisplayed().fetchSemanticsNode().boundsInRoot
            assertTrue(title.top >= action.bottom)
        }
    }
    @Test fun emptyNotesNestedContentAndCompletedTasksHaveDistinctMessagesWithoutHidingNotes() {
        openLayer(); status("Esta capa está vacía")
        val note = runBlocking { app.nodeRepository.createNode(project.id, layer.id, "Nota visible", purpose = NodePurpose.NOTE) }
        status("No hay tareas por realizar")
        compose.onNodeWithTag("nodes-list").performScrollToNode(hasText(note.title)); compose.onNodeWithText(note.title).assertIsDisplayed()
        val nested = runBlocking { app.nodeRepository.createNode(project.id, layer.id, "Subcapa", purpose = NodePurpose.LAYER) }
        val task = runBlocking { app.nodeRepository.createNode(project.id, nested.id, "Tarea pendiente") }
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("layer-content-status").fetchSemanticsNodes().isEmpty() }
        runBlocking { app.nodeRepository.setCompleted(task.id, true) }
        status("No hay tareas pendientes")
        compose.onNodeWithTag("nodes-list").performScrollToNode(hasText(note.title)); compose.onNodeWithText(note.title).assertExists()
        runBlocking { app.nodeRepository.deleteNode(nested.id); app.nodeRepository.deleteNode(note.id) }
        status("Esta capa está vacía")
    }
    @Test fun detailShowsEveryNamedResponsibleAndWrapsWhileCardsKeepCompactPlusN() {
        var count by mutableIntStateOf(1)
        val people = (1..20).map { Person("p$it", "Persona $it con apellido") }
        compose.setContent { Arachn0deTheme { Column(Modifier.width(180.dp).verticalScroll(rememberScrollState()).testTag("responsible-width")) {
            ResponsibleAvatars(people.take(count)); ResponsiblePeopleDetail(people.take(count))
        } } }
        for (number in listOf(1, 3, 20)) {
            compose.runOnIdle { count = number }
            for (person in people.take(number)) compose.onNode(hasText(person.name) and hasAnyAncestor(hasTestTag("responsibles-detail"))).assertExists()
            assertEquals(number, compose.onAllNodes(hasContentDescription("Persona", substring = true) and hasAnyAncestor(hasTestTag("responsibles-detail"))).fetchSemanticsNodes().size)
        }
        compose.onNodeWithText("+17").assertExists()
        compose.onNode(hasText("+17") and hasAnyAncestor(hasTestTag("responsibles-detail"))).assertDoesNotExist()
        val first = compose.onNode(hasText(people.first().name) and hasAnyAncestor(hasTestTag("responsibles-detail"))).getUnclippedBoundsInRoot()
        val last = compose.onNode(hasText(people.last().name) and hasAnyAncestor(hasTestTag("responsibles-detail"))).getUnclippedBoundsInRoot()
        assertTrue(last.top > first.top)
        val width = compose.onNodeWithTag("responsible-width").getUnclippedBoundsInRoot()
        for (person in people) {
            val item = compose.onNode(hasText(person.name) and hasAnyAncestor(hasTestTag("responsibles-detail")))
            item.performScrollTo().assertIsDisplayed()
            val bounds = item.getUnclippedBoundsInRoot()
            assertTrue(bounds.left >= width.left && bounds.right <= width.right)
        }
    }
    @Test fun completionFollowsEveryThemeWithContrastAndDisabledStateWhileSprintKeepsItsColors() {
        val prefs = AppearancePreferences(app.getSharedPreferences("dogfooding-theme-test", 0))
        var task by mutableStateOf(Node("task", "p", null, "Task", "", false, 0, 0, 0, false))
        var busy by mutableStateOf(false)
        var normal: CompletionControlColors? = null; var sprint: CompletionControlColors? = null
        var clicks = 0
        compose.setContent { Arachn0deTheme(prefs) {
            normal = completionControlColors(false, task.isCompleted, !busy)
            sprint = completionControlColors(true, true, false)
            NodeCard(task, null, false, true, {}, {}, {}, false, false, { _, done -> done() }, {
                clicks++; task = task.copy(isCompleted = !task.isCompleted)
            }, stateBusy = busy)
        } }
        for (theme in AppearanceTheme.entries) {
            compose.runOnIdle { prefs.setTheme(theme) }; compose.waitForIdle()
            assertEquals(theme.palette.primary, normal!!.fill); assertEquals(theme.palette.primary, normal!!.border)
            val light = normal!!.fill.luminance(); val dark = normal!!.mark.luminance()
            assertTrue((kotlin.math.max(light, dark) + .05f) / (kotlin.math.min(light, dark) + .05f) >= 3f)
            assertEquals(SemanticColors.SprintDone, sprint!!.fill)
        }
        compose.onNodeWithContentDescription("Completar: Task").assertIsEnabled().performClick()
        compose.onNodeWithContentDescription("Marcar pendiente: Task").assertIsEnabled()
        compose.runOnIdle { busy = true }; compose.waitForIdle()
        assertEquals(.38f, normal!!.fill.alpha, .001f)
        compose.onNodeWithContentDescription("Marcar pendiente: Task").assertIsNotEnabled().performClick()
        assertEquals(1, clicks); assertTrue(task.isCompleted)
        compose.runOnIdle { busy = false }; compose.onNodeWithContentDescription("Marcar pendiente: Task").performClick()
        assertEquals(2, clicks); assertFalse(task.isCompleted)
    }
}
