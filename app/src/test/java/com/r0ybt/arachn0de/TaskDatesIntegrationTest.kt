package com.r0ybt.arachn0de

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import com.r0ybt.arachn0de.ui.formatTaskDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.*
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application=com.r0ybt.arachn0de.security.LegacyUiTestApplication::class,sdk = [28])
class TaskDatesIntegrationTest {
    private val compose = createAndroidComposeRule<MainActivity>()
    private lateinit var app: Arachn0deApplication
    private lateinit var project: String
    private lateinit var task: String
    private val start = 1_800_000_000_000L
    private val due = start + 86_400_000L
    @get:Rule val rules: RuleChain = RuleChain.outerRule(object : ExternalResource() {
        override fun before() {
            app = ApplicationProvider.getApplicationContext()
            runBlocking {
                project = app.projectRepository.createProject("Dates project").id
                task = app.nodeRepository.createNode(project, null, "Dated task", startAt = start, dueAt = due).id
                app.personRepository.save("p", "Roy", null)
                app.personRepository.setResponsiblePeople(task, setOf("p"))
            }
        }
        override fun after() { app.database.close() }
    }).around(compose)
    private fun await(text: String) = compose.waitUntil(10_000) { compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }

    @Test fun editingDatesThroughScreenRestoresDraftAndKeepsAssignmentsAndLayerDates() {
        await("Dates project"); compose.onNodeWithText("Dates project").performClick()
        await("Dated task")
        compose.onNodeWithTag("nodes-list").performScrollToNode(hasText("Dated task"))
        compose.onNodeWithContentDescription("Roy").assertExists()
        compose.onNodeWithContentDescription("Más opciones").performClick()
        compose.onNodeWithText("Editar").performClick()
        await("Guardar")
        compose.onNodeWithText("Inicio: ${formatTaskDate(start)}").assertExists()
        compose.onNodeWithText("Vencimiento: ${formatTaskDate(due)}").assertExists()
        compose.onNodeWithText("Quitar Inicio").performScrollTo().performClick()
        compose.activityRule.scenario.recreate()
        await("Guardar")
        compose.onNodeWithText("Inicio: Sin fecha").assertDoesNotExist()
        compose.onNode(hasText("Guardar") or hasText("Crear")).performClick()
        compose.waitUntil(10_000) { runBlocking { app.nodeRepository.getNode(task)!!.startAt == null } }
        runBlocking {
            assertEquals(due, app.nodeRepository.getNode(task)!!.dueAt)
            assertEquals("p", app.personRepository.observeAssignments(project).first().getValue(task).single().id)
            assertTrue(app.nodeRepository.convertPurpose(task, com.r0ybt.arachn0de.domain.model.NodePurpose.LAYER))
            app.nodeRepository.createNode(project, task, "Child")
        }
        compose.waitUntil(10_000) { compose.onAllNodesWithContentDescription("Capa").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithContentDescription("Más opciones").performClick()
        compose.onNodeWithText("Editar").performClick()
        await("Guardar")
        compose.onNodeWithText("Vencimiento: ${formatTaskDate(due)}").assertDoesNotExist()
        compose.onNodeWithText("Título").performTextReplacement("Layer renamed")
        compose.onNode(hasText("Guardar") or hasText("Crear")).performClick()
        compose.waitUntil(10_000) { runBlocking { app.nodeRepository.getNode(task)!!.title == "Layer renamed" } }
        runBlocking {
            assertEquals(due, app.nodeRepository.getNode(task)!!.dueAt)
            assertEquals("p", app.personRepository.observeAssignments(project).first().getValue(task).single().id)
        }
    }
}
