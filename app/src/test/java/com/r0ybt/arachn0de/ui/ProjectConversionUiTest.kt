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
import org.robolectric.annotation.GraphicsMode

@GraphicsMode(GraphicsMode.Mode.NATIVE)
@RunWith(RobolectricTestRunner::class)
@Config(application=com.r0ybt.arachn0de.security.LegacyUiTestApplication::class,sdk = [28])
class ProjectConversionUiTest {
    private val compose = createAndroidComposeRule<MainActivity>()
    private lateinit var app: Arachn0deApplication
    private lateinit var source: Project
    private lateinit var target: Project
    private lateinit var layer: Node
    @get:Rule val rules: RuleChain = RuleChain.outerRule(object : ExternalResource() {
        override fun before() {
            app = ApplicationProvider.getApplicationContext()
            runBlocking {
                source = app.projectRepository.createProject("Source")
                target = app.projectRepository.createProject("Target")
                app.nodeRepository.createNode(source.id, null, "Child")
                layer = app.nodeRepository.createNode(source.id, null, "Native layer", purpose = NodePurpose.LAYER)
                app.nodeRepository.createNode(source.id, layer.id, "Inside native layer")
            }
        }
        override fun after() { app.database.close() }
    }).around(compose)
    private fun await(text: String) {
        try { compose.waitUntil(10_000) {
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
            androidx.compose.runtime.snapshots.Snapshot.sendApplyNotifications()
            compose.mainClock.advanceTimeByFrame()
            compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
        } } catch (failure: Throwable) { throw AssertionError("Await: $text\n" + compose.onRoot().printToString(), failure) }
    }
    private fun chooseTarget() {
        await("Elige otro proyecto")
        compose.onNode(hasText("Target") and hasAnyAncestor(isDialog())).performClick()
        await("En la raíz del proyecto"); compose.onNodeWithText("En la raíz del proyecto").performClick()
        await("Confirmar conversión")
    }
    @Test fun projectCardConfirmsDestinationAndNavigatesToConvertedLayerWhileProjectsReopenAtRoot() {
        await("Source")
        compose.onNode(hasContentDescription("Opciones del proyecto") and hasAnyAncestor(hasText("Source"))).performClick()
        compose.onNodeWithText("Convertir en capa").performScrollTo().performClick(); chooseTarget()
        compose.onAllNodesWithText("Cancelar").onLast().performClick()
        assertNotNull(runBlocking { app.projectRepository.getProject(source.id) })
        compose.onNodeWithText("En la raíz del proyecto").performClick(); compose.onNodeWithText("Convertir").performClick()
        await("CAPA 1"); compose.onNodeWithTag("nodes-list").performScrollToNode(hasText("Child")); await("Child")
        assertNull(runBlocking { app.projectRepository.getProject(source.id) })
        assertEquals(target.id, runBlocking { app.nodeRepository.getNode(source.id)!!.projectId })
        compose.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        await("Nuevo elemento")
        compose.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        await("Proyectos")
        compose.onNodeWithTag("projects-list").performScrollToNode(hasText("Target"))
        compose.onNodeWithText("Target").performClick(); await("Nuevo elemento")
        compose.onNodeWithContentDescription("Opciones del proyecto").assertExists()
    }
    @Test fun layerMenuConfirmsPromotionThenEditedProjectConvertsBackWithCurrentChildren() {
        await("Source"); compose.onNodeWithText("Source").performClick(); await("Nuevo elemento")
        compose.onNodeWithTag("nodes-list").performScrollToNode(hasText("Native layer"))
        compose.onNodeWithTag("node-options:${layer.id}").performClick()
        compose.onNodeWithText("Convertir en proyecto").performScrollTo().performClick()
        compose.onNodeWithText("Cancelar").performClick()
        assertNotNull(runBlocking { app.nodeRepository.getNode(layer.id) })
        compose.onNodeWithTag("node-options:${layer.id}").performClick()
        compose.onNodeWithText("Convertir en proyecto").performScrollTo().performClick()
        compose.onNodeWithText("Convertir").performClick(); await("Inside native layer")
        compose.onNodeWithContentDescription("Opciones del proyecto").assertExists()
        assertNotNull(runBlocking { app.projectRepository.getProject(layer.id) })
        compose.onNodeWithText("Nuevo elemento").performClick(); await("Título")
        compose.onNodeWithText("Título").performTextInput("Added while project")
        compose.onNodeWithText("Crear").performClick(); await("Deshacer"); compose.onNodeWithTag("nodes-list").performScrollToNode(hasText("Added while project")); await("Added while project")
        compose.onNodeWithTag("nodes-list").performScrollToIndex(0)
        compose.onNodeWithContentDescription("Opciones del proyecto").performClick()
        compose.onNodeWithText("Convertir en capa").performScrollTo().performClick(); chooseTarget()
        compose.onNodeWithText("Convertir").performClick(); await("CAPA 1"); compose.onNodeWithTag("nodes-list").performScrollToNode(hasText("Added while project")); await("Added while project")
        val children = runBlocking { app.nodeRepository.getProjectNodes(target.id).filter { it.parentId == layer.id } }
        assertEquals(setOf("Inside native layer", "Added while project"), children.map { it.title }.toSet())
        assertNull(runBlocking { app.projectRepository.getProject(layer.id) })
    }
}
