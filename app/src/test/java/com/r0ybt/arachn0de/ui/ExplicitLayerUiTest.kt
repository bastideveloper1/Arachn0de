package com.r0ybt.arachn0de.ui

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import com.r0ybt.arachn0de.Arachn0deApplication
import com.r0ybt.arachn0de.MainActivity
import com.r0ybt.arachn0de.domain.model.*
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class) @Config(sdk=[28])
class ExplicitLayerUiTest {
    private val compose=createAndroidComposeRule<MainActivity>()
    private lateinit var app:Arachn0deApplication;private lateinit var source:Project;private lateinit var target:Project;private lateinit var empty:Node
    @get:Rule val rules:RuleChain=RuleChain.outerRule(object:ExternalResource() {
        override fun before() { app=ApplicationProvider.getApplicationContext();runBlocking {
            source=app.projectRepository.createProject("Source");target=app.projectRepository.createProject("Target")
            empty=app.nodeRepository.createNode(target.id,null,"Empty",purpose=NodePurpose.LAYER)
        } }
        override fun after() { app.database.close() }
    }).around(compose)
    private fun await(text:String)=compose.waitUntil(10000) { org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }
    @Test fun emptyLayerOpensWithoutCompletionAndAcceptsNewTask() {
        await("Target");compose.onNodeWithText("Target").performClick();await("Empty")
        compose.onNodeWithTag("nodes-list").performScrollToNode(hasText("Empty"))
        compose.onNodeWithText("Sin pendientes").assertExists()
        compose.onNodeWithContentDescription("Completar: Empty").assertDoesNotExist()
        compose.onNodeWithText("Empty").performClick();await("CAPA 1")
        compose.onNodeWithText("Nuevo elemento").assertIsEnabled().performClick();await("Título")
        compose.onNodeWithText("Título").performTextInput("New task");compose.onNodeWithText("Crear").performClick()
        compose.waitUntil(10000) { runBlocking { app.nodeRepository.getProjectNodes(target.id).any { it.title=="New task" && it.parentId==empty.id } } }
        assertEquals(NodePurpose.LAYER,runBlocking { app.nodeRepository.getNode(empty.id)!!.purpose })
    }
    @Test fun projectOverflowConfirmsMoveAndConvertedEmptyLayerIsNavigableWithoutRestart() {
        await("Source");compose.onNodeWithText("Source").performClick();await("Nuevo elemento")
        compose.onNodeWithContentDescription("Opciones del proyecto").performScrollTo().performClick()
        compose.onNodeWithText("Mover dentro de…").performScrollTo().performClick();await("Elige otro proyecto")
        compose.onNode(hasText("Target") and hasAnyAncestor(isDialog())).performClick();await("En la raíz del proyecto")
        await("Empty");compose.onNodeWithText("Empty").performClick();await("Mover proyecto")
        compose.onNodeWithText("Cancelar") // Both selector and confirmation retain cancel actions.
        assertNotNull(runBlocking { app.projectRepository.getProject(source.id) })
        compose.onNodeWithText("Mover").performClick()
        compose.waitUntil(10000) { runBlocking { app.projectRepository.getProject(source.id)==null } }
        await("Target");compose.onNodeWithText("Target").performClick();await("Empty")
        compose.onNodeWithTag("nodes-list").performScrollToNode(hasText("Empty"));compose.onNodeWithText("Empty").performClick();await("Source")
        compose.onNodeWithTag("nodes-list").performScrollToNode(hasText("Source"));compose.onNodeWithText("Source").performClick()
        await("CAPA 2");compose.onNodeWithText("Nuevo elemento").assertIsEnabled()
    }
    @Test fun dashboardMenuMovesToProjectRootAndConfirmationCanBeCancelled() {
        await("Source")
        compose.onNodeWithTag("projects-list").performScrollToNode(hasText("Source"))
        compose.onNode(hasContentDescription("Opciones del proyecto") and hasAnyAncestor(hasText("Source"))).performClick()
        compose.onNodeWithText("Mover dentro de…").performScrollTo().performClick();await("Elige otro proyecto")
        compose.onNode(hasText("Target") and hasAnyAncestor(isDialog())).performClick();await("En la raíz del proyecto")
        compose.onNodeWithText("En la raíz del proyecto").performClick();await("Mover proyecto")
        compose.onAllNodesWithText("Cancelar").onLast().performClick()
        assertNotNull(runBlocking { app.projectRepository.getProject(source.id) })
        compose.onNodeWithText("En la raíz del proyecto").performClick();compose.onNodeWithText("Mover").performClick()
        compose.waitUntil(10000) { runBlocking { app.projectRepository.getProject(source.id)==null } }
        val converted=runBlocking { app.nodeRepository.getProjectNodes(target.id).single { it.title=="Source" } }
        assertEquals(NodePurpose.LAYER,converted.purpose);assertNull(converted.parentId)
        compose.onNodeWithText("Source").assertDoesNotExist()
    }
    @Test fun actionRequiresExplicitLayerConversionBeforeCreatingChildren() {
        val task=runBlocking { app.nodeRepository.createNode(target.id,null,"Action") }
        await("Target");compose.onNodeWithText("Target").performClick();await("Action")
        compose.onNodeWithTag("nodes-list").performScrollToNode(hasText("Action"));compose.onNodeWithText("Action").performClick()
        compose.onNodeWithText("Nuevo elemento").assertIsNotEnabled()
        compose.onNodeWithContentDescription("Opciones del elemento").performScrollTo().performClick();compose.onNodeWithText("Convertir en capa").performScrollTo().performClick()
        compose.waitUntil(10000) { runBlocking { app.nodeRepository.getNode(task.id)?.purpose==NodePurpose.LAYER } }
        compose.onNodeWithText("Nuevo elemento").assertIsEnabled()
    }
}
