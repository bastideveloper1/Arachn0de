package com.r0ybt.arachn0de.ui

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.r0ybt.arachn0de.Arachn0deApplication
import com.r0ybt.arachn0de.domain.model.*
import com.r0ybt.arachn0de.ui.theme.Arachn0deTheme
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application=com.r0ybt.arachn0de.security.LegacyUiTestApplication::class,sdk=[28],qualifiers="w411dp-h891dp")
class SprintLinksUiTest {
    @get:Rule val compose=createAndroidComposeRule<ComponentActivity>()
    private val app get()=compose.activity.application as Arachn0deApplication
    private lateinit var project:Project;private lateinit var sprint:Node;private lateinit var origin:Node;private lateinit var task:Node
    @Before fun seed()=runBlocking {
        project=app.projectRepository.createProject("Work")
        sprint=app.nodeRepository.createNode(project.id,null,"Iteration",purpose=NodePurpose.LAYER);app.nodeRepository.setSprintMode(sprint.id,true)
        origin=app.nodeRepository.createNode(project.id,null,"Origin",purpose=NodePurpose.LAYER)
        task=app.nodeRepository.createNode(project.id,origin.id,"External",description="Original description")
    }
    @After fun close() {app.database.close()}
    private fun exists(text:String)=compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
    private fun await(predicate:()->Boolean)=compose.waitUntil(15000) {
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
        androidx.compose.runtime.snapshots.Snapshot.sendApplyNotifications();compose.mainClock.advanceTimeByFrame();predicate()
    }
    private fun screen() {
        compose.runOnUiThread {compose.activity.setContent {Arachn0deTheme {AppRoot(app.projectRepository,app.nodeRepository)}}}
        await {exists("Work")};compose.onNodeWithText("Work").performClick()
        await {exists("Iteration")};compose.onNodeWithText("Iteration").performClick()
        await {exists("Vincular tareas")}
    }
    @Test fun selectionShowsOriginCountsOriginalOnceAndOpensItsRealDetail() {
        screen();compose.onNodeWithText("Vincular tareas").performClick()
        await {exists("Buscar tarea o capa de origen")}
        compose.onNodeWithText("Buscar tarea o capa de origen").performTextInput("External")
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithText("Origen: Origin").assertExists()
        compose.onAllNodes(isToggleable())[0].performClick();compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithText("Vincular").performClick()
        await {exists("Vinculada · Origen: Origin") && exists("External")}
        compose.onAllNodesWithText("External").assertCountEquals(1)
        runBlocking {assertEquals(origin.id,app.nodeRepository.getNode(task.id)!!.parentId);app.nodeRepository.setWorkState(task.id,WorkState.DOING)}
        await {exists("Progreso Sprint · 50 %")}
        compose.onNodeWithText("External").performScrollTo().performClick()
        await {exists("Original description")}
        compose.onNodeWithText("Vincular tareas").assertDoesNotExist()
        runBlocking {assertEquals(3,app.nodeRepository.getProjectNodes(project.id).size)}
    }
    @Test fun cancelUnlinkPreservesPhaseAndExplicitConfirmationDiscardsOnlyPhase()=runBlocking<Unit> {
        app.nodeRepository.sprintLinks.link(sprint.id,setOf(task.id));app.nodeRepository.setWorkState(task.id,WorkState.VALIDATED)
        screen();await {exists("Desvincular")}
        compose.onNodeWithText("Desvincular").performScrollTo().performClick()
        compose.onNodeWithText("se descartará la fase Sprint «Validada»",substring=true).assertExists()
        compose.onNodeWithText("Cancelar").performClick()
        assertEquals(WorkState.VALIDATED,app.nodeRepository.getNode(task.id)!!.workState)
        compose.onNodeWithText("Desvincular").performClick();compose.onNodeWithText("Desvincular y descartar fase").performClick()
        await {runBlocking {app.database.sprintLinkDao().all().isEmpty()}}
        val saved=app.nodeRepository.getNode(task.id)!!;assertEquals(origin.id,saved.parentId);assertNull(saved.workState);assertFalse(saved.isCompleted)
        compose.onNodeWithText("External").assertDoesNotExist()
    }
}
