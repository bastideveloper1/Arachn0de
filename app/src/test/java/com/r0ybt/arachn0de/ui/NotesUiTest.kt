package com.r0ybt.arachn0de.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.test.core.app.ApplicationProvider
import com.r0ybt.arachn0de.Arachn0deApplication
import com.r0ybt.arachn0de.domain.model.NodePurpose
import com.r0ybt.arachn0de.ui.theme.Arachn0deTheme
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class NotesUiTest {
    private val compose = createComposeRule()
    private lateinit var app: Arachn0deApplication
    private lateinit var project: String
    private lateinit var task: String
    @get:Rule val rules: RuleChain = RuleChain.outerRule(object : ExternalResource() {
        override fun before() {
            app = ApplicationProvider.getApplicationContext()
            runBlocking {
                project = app.projectRepository.createProject("Project").id
                task = app.nodeRepository.createNode(project, null, "Dated task", dueAt = 20).id
            }
        }
        override fun after() { app.database.close() }
    }).around(compose)
    private fun await(text: String) = compose.waitUntil(10_000) { compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }
    private fun openProject(restorer: StateRestorationTester? = null) {
        val content: @Composable () -> Unit = { Arachn0deTheme { AppSafeArea { AppRoot(app.projectRepository, app.nodeRepository) { 100L } } } }
        if (restorer == null) compose.setContent(content) else restorer.setContent(content)
        await("Contiene 1 vencida")
        compose.onNodeWithText("Project").performClick()
        await("Dated task")
    }
    private fun scroll(text: String) { compose.onNodeWithTag("nodes-list").performScrollToNode(hasText(text)) }
    @Test fun conversionReactivelyRemovesWorkAndUrgencyAndBlocksChildrenWithoutLosingDates() {
        openProject()
        compose.onNodeWithContentDescription("Más opciones").performClick()
        compose.onNodeWithText("Convertir en nota").performClick()
        compose.waitUntil(10_000) { runBlocking { app.nodeRepository.getNode(task)!!.purpose == NodePurpose.NOTE } }
        compose.onAllNodesWithContentDescription("Nota").assertCountEquals(1)
        compose.onNodeWithContentDescription("Completar: Dated task").assertDoesNotExist()
        compose.onNodeWithText("Dated task").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick) { it() }
        scroll("Nota · Convierte en capa para añadir hijos"); await("Nota · Convierte en capa para añadir hijos")
        compose.onNodeWithText("Nuevo elemento").assertIsNotEnabled()
        compose.onNodeWithTag("nodes-list").performScrollToNode(hasContentDescription("Opciones del elemento")); compose.onNodeWithContentDescription("Opciones del elemento").performClick(); compose.onNodeWithText("Editar").performClick()
        compose.onNodeWithText("Inicio").assertDoesNotExist()
        compose.onNodeWithText("Descripción").performTextReplacement("Information")
        compose.onNode(hasText("Guardar") or hasText("Crear")).performClick()
        compose.waitUntil(10_000) { runBlocking { app.nodeRepository.getNode(task)!!.description == "Information" } }
        assertEquals(20L, runBlocking { app.nodeRepository.getNode(task)!!.dueAt })
        compose.onNodeWithContentDescription("Opciones del elemento").performScrollTo().performClick(); compose.onNodeWithText("Convertir en tarea").performClick()
        await("Completar")
        compose.onNodeWithText("Nuevo elemento").assertIsEnabled()
        assertEquals(1, runBlocking { app.nodeRepository.calculateProjectProgress(project)!!.total })
    }
    @Test fun noteCreationSelectionAndDraftSurviveRestoration() {
        val restorer = StateRestorationTester(compose)
        openProject(restorer)
        compose.waitUntil(10_000) { compose.onAllNodes(hasText("Nuevo elemento") and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Nuevo elemento").performClick()
        await("Título")
        compose.onNodeWithText("Nota").performScrollTo().performClick()
        compose.onNodeWithText("Título").performTextInput("New note")
        compose.onNodeWithText("Descripción").performTextInput("Note content")
        restorer.emulateSavedInstanceStateRestore()
        await("Nuevo elemento")
        await("Nota")
        compose.onNodeWithText("Nota").assertIsSelected()
        compose.onNode(hasText("Guardar") or hasText("Crear")).performClick()
        compose.waitUntil(10_000) { runBlocking { app.nodeRepository.getProjectNodes(project).any { it.title == "New note" } } }
        compose.onNodeWithTag("nodes-list").performScrollToNode(hasText("New note"))
        compose.onNodeWithText("New note").assertExists()
        val note = runBlocking { app.nodeRepository.getProjectNodes(project).single { it.title == "New note" } }
        assertEquals(NodePurpose.NOTE, note.purpose); assertEquals("Note content", note.description)
        assertEquals(1, runBlocking { app.nodeRepository.calculateProjectProgress(project)!!.total })
    }
    @Test fun conversionUpdatesProjectAndAttentionViewWithoutRestart() {
        compose.setContent { Arachn0deTheme { AppSafeArea { AppRoot(app.projectRepository, app.nodeRepository) { 100L } } } }
        await("Contiene 1 vencida")
        runBlocking { app.nodeRepository.convertPurpose(task, NodePurpose.NOTE) }
        await("No hay tareas por realizar")
        compose.onNodeWithText("Contiene 1 vencida").assertDoesNotExist()
        compose.onNodeWithContentDescription("Abrir menú").performClick()
        compose.onNodeWithText("Atención").performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("attention-list").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("attention-task:$task").assertDoesNotExist()
        runBlocking { app.nodeRepository.convertPurpose(task, NodePurpose.ACTION) }
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("attention-task:$task").fetchSemanticsNodes().isNotEmpty() }
        runBlocking { app.nodeRepository.convertPurpose(task, NodePurpose.NOTE) }
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("attention-task:$task").fetchSemanticsNodes().isEmpty() }
    }

}
