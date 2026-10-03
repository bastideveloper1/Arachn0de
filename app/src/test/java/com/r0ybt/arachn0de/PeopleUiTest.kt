package com.r0ybt.arachn0de

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class PeopleUiTest {
    private val compose = createAndroidComposeRule<MainActivity>()
    private lateinit var app: Arachn0deApplication
    private lateinit var project: String
    private lateinit var layer: String
    @get:Rule val rules: RuleChain = RuleChain.outerRule(object : ExternalResource() {
        override fun before() {
            app = ApplicationProvider.getApplicationContext()
            runBlocking {
                project = app.projectRepository.createProject("People project").id
                layer = app.nodeRepository.createNode(project, null, "Layer").id
                app.nodeRepository.createNode(project, layer, "Child")
            }
        }
        override fun after() { app.database.close() }
    }).around(compose)
    private fun waitText(text: String) = compose.waitUntil(10_000) { compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }

    @Test fun peopleCrudAssignmentAndRecreationWorkThroughUi() {
        waitText("People project")
        compose.onNodeWithContentDescription("Abrir menú").performClick()
        compose.onNodeWithText("Personas").performClick()
        compose.onNodeWithText("Nueva Persona").performClick()
        compose.onNodeWithText("Guardar").assertIsNotEnabled()
        compose.onNode(hasSetTextAction()).performTextInput("Roy")
        compose.onNodeWithText("Guardar").performClick()
        waitText("Roy")
        compose.onNodeWithText("Editar").performClick()
        compose.onNode(hasSetTextAction()).assertTextContains("Roy").performTextReplacement("María")
        compose.onNodeWithText("Guardar").performClick()
        waitText("María")
        compose.onNodeWithText("Volver").performClick()
        waitText("People project")
        compose.onNodeWithText("People project").performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("nodes-list").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("nodes-list").performScrollToNode(hasText("Layer"))
        compose.onNodeWithText("Layer").performClick()
        compose.onNodeWithTag("nodes-list").performScrollToNode(hasText("Responsables"))
        compose.onNodeWithText("Responsables").performClick()
        waitText("María")
        compose.onNode(hasText("María") and hasAnyAncestor(isDialog())).performClick()
        compose.onNodeWithText("Guardar").performClick()
        compose.waitUntil(10_000) { runBlocking { app.personRepository.observeAssignments(project).first()[layer]?.size == 1 } }
        compose.activityRule.scenario.recreate()
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("nodes-list").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("nodes-list").performScrollToNode(hasContentDescription("María"))
        compose.onNodeWithContentDescription("María").assertExists()
        compose.onNodeWithContentDescription("Abrir menú").performClick()
        compose.onNodeWithText("Personas").performClick()
        waitText("María")
        compose.onNodeWithText("Editar").performClick()
        compose.onNode(hasSetTextAction()).performTextReplacement("Mary")
        compose.onNodeWithText("Guardar").performClick()
        waitText("Mary")
        compose.onNodeWithText("Volver").performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("nodes-list").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("nodes-list").performScrollToNode(hasText("Responsables"))
        compose.waitUntil(10_000) { compose.onAllNodesWithContentDescription("Mary").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithContentDescription("Abrir menú").performClick()
        compose.onNodeWithText("Personas").performClick()
        waitText("Mary")
        compose.onNodeWithText("Eliminar").performClick()
        compose.onNode(hasText("Eliminar") and hasAnyAncestor(isDialog())).performClick()
        waitText("Aún no hay Personas.")
        check(runBlocking { app.nodeRepository.getNode(layer) != null })
    }
}
