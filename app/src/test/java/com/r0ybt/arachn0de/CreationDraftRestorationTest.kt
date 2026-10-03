package com.r0ybt.arachn0de

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
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
class CreationDraftRestorationTest {
    private val compose=createAndroidComposeRule<MainActivity>()
    private lateinit var app:Arachn0deApplication
    private lateinit var project:String
    private lateinit var x:String
    private lateinit var y:String
    @get:Rule val rules:RuleChain=RuleChain.outerRule(object:ExternalResource() {
        override fun before() { app=ApplicationProvider.getApplicationContext();runBlocking {
            project=app.projectRepository.createProject("Project").id
            x=app.nodeRepository.createNode(project,null,"Node X").id
            y=app.nodeRepository.createNode(project,null,"Node Y").id
        } }
        override fun after() { app.database.close() }
    }).around(compose)
    private fun await(label:String)=compose.waitUntil(10000) { compose.onAllNodesWithText(label).fetchSemanticsNodes().isNotEmpty() }
    private fun input(label:String,value:String)=compose.onNodeWithText(label).performScrollTo().performTextReplacement(value)
    private fun openProject() { await("Project");compose.onNodeWithText("Project").performClick();await("Nuevo elemento") }
    private fun edit(title:String) {
        compose.onNodeWithTag("nodes-list").performScrollToNode(hasText(title))
        // The title identifies a stable card, independent of current list position.
        compose.onNodeWithText(title).performClick();await("CAPA 1")
        compose.onNodeWithTag("nodes-list").performScrollToNode(hasText("Editar"));compose.onNodeWithText("Editar").performClick();await("Guardar")
    }
    @Test fun backKeepsCreationAndClosedDraftSurvivesActualActivityRecreation() {
        openProject();compose.onNodeWithText("Nuevo elemento").performClick();input("Título","Pending");input("Descripción","Details")
        compose.onNodeWithTag("option:Crear varios").performScrollTo().performClick();input("Cantidad","12")
        compose.onNodeWithText("Al final").performScrollTo().performClick();input("Número inicial","4")
        compose.runOnUiThread {
            @Suppress("DEPRECATION")
            org.robolectric.shadows.ShadowDialog.getLatestDialog().onBackPressed()
        }
        compose.onNodeWithText("Crear").assertDoesNotExist()
        compose.activityRule.scenario.recreate();await("Nuevo elemento");compose.onNodeWithText("Nuevo elemento").performClick()
        compose.onNodeWithText("Título").performScrollTo().assert(hasText("Pending"));compose.onNodeWithText("Descripción").performScrollTo().assert(hasText("Details"))
        compose.onNodeWithText("Cantidad").performScrollTo().assert(hasText("12"));compose.onNodeWithText("Número inicial").performScrollTo().assert(hasText("4"))
        assertEquals(2,runBlocking { app.nodeRepository.getProjectNodes(project).size })
    }
    @Test fun newAndDifferentNodeEditsNeverContaminateEachOther() {
        openProject();compose.onNodeWithText("Nuevo elemento").performClick();input("Título","New pending");compose.onNodeWithText("Cerrar").performClick()
        edit("Node X");input("Título","X pending");compose.onNodeWithText("Cerrar").performClick()
        compose.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() };await("Nuevo elemento")
        edit("Node Y");compose.onNodeWithText("Título").assert(hasText("Node Y"));input("Descripción","Y pending");compose.onNodeWithText("Cerrar").performClick()
        compose.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() };await("Nuevo elemento")
        edit("Node X");compose.onNodeWithText("Título").assert(hasText("X pending"));compose.onNodeWithText("Descripción").assert(hasText(""));compose.onNodeWithText("Cerrar").performClick()
        compose.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() };await("Nuevo elemento")
        compose.onNodeWithText("Nuevo elemento").performClick();compose.onNodeWithText("Título").assert(hasText("New pending"))
        assertEquals("Node X",runBlocking { app.nodeRepository.getNode(x)!!.title });assertEquals("Node Y",runBlocking { app.nodeRepository.getNode(y)!!.title })
    }
}
