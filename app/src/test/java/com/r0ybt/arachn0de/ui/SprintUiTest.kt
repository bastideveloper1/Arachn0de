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
class SprintUiTest {
    private val compose=createAndroidComposeRule<MainActivity>()
    private lateinit var app:Arachn0deApplication;private lateinit var project:Project;private lateinit var layer:Node;private lateinit var task:Node
    @get:Rule val rules:RuleChain=RuleChain.outerRule(object:ExternalResource() {
        override fun before() { app=ApplicationProvider.getApplicationContext();runBlocking {
            project=app.projectRepository.createProject("Work")
            layer=app.nodeRepository.createNode(project.id,null,"Development",purpose=NodePurpose.LAYER)
            task=app.nodeRepository.createNode(project.id,layer.id,"Implement feature")
            app.nodeRepository.createNode(project.id,layer.id,"Notes",purpose=NodePurpose.NOTE)
            app.nodeRepository.createNode(project.id,layer.id,"Normal sublayer",purpose=NodePurpose.LAYER)
        } }
        override fun after() { app.database.close() }
    }).around(compose)
    private fun await(text:String)=compose.waitUntil(10000) { org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }
    private fun open() {
        await("Work");compose.onNodeWithText("Work").performClick();await("Development")
        compose.onNodeWithTag("nodes-list").performScrollToNode(hasText("Development"));compose.onNodeWithText("Development").performClick();await("CAPA 1")
    }
    private fun scroll(text:String) { compose.onNodeWithTag("nodes-list").performScrollToNode(hasText(text)) }
    private fun modeMenu(text:String) {
        compose.onNodeWithTag("nodes-list").performScrollToNode(hasContentDescription("Opciones del elemento"))
        compose.onNodeWithContentDescription("Opciones del elemento").performClick()
        compose.onNodeWithText(text).performScrollTo().performClick()
    }
    @Test fun activationConfirmationCancellationAndDeactivationReturnToNormal() {
        open()
        scroll("Progreso");compose.onNodeWithText("Progreso").assertIsDisplayed()
        compose.onNodeWithText("0% · 1 de 1 pendientes").assertIsDisplayed()
        modeMenu("Modo Sprint");await("Activar Modo Sprint")
        compose.onNodeWithText("Cancelar").performClick();assertFalse(runBlocking { app.nodeRepository.getNode(layer.id)!!.sprintMode })
        modeMenu("Modo Sprint");compose.onNodeWithText("Activar").performClick()
        compose.waitUntil(10000) { runBlocking { app.nodeRepository.getNode(layer.id)!!.sprintMode } }
        scroll("CAPA 1");compose.onNodeWithText("Progreso").assertDoesNotExist()
        compose.onNodeWithText("Progreso Sprint · 0 %").assertIsDisplayed()
        compose.onNodeWithText("0% · 1 de 1 pendientes").assertDoesNotExist()
        for (state in WorkState.entries) {
            val label="▼ ${state.label} · ${if(state==WorkState.UNPLANNED) 1 else 0}"
            scroll(label);compose.onNodeWithText(label).assertIsDisplayed()
        }
        scroll("Implement feature")
        compose.onNodeWithContentDescription("Confirmar No planificada y avanzar a Planificada: Implement feature").performClick()
        compose.waitUntil(10000) { runBlocking { app.nodeRepository.getNode(task.id)!!.workState==WorkState.PLANNED } }
        scroll("CAPA 1");compose.onNodeWithText("Progreso").assertDoesNotExist()
        compose.onNodeWithText("Progreso Sprint · 25 %").assertIsDisplayed()
        compose.onNodeWithText("0% · 1 de 1 pendientes").assertDoesNotExist()
        for (state in WorkState.entries) {
            val label="▼ ${state.label} · ${if(state==WorkState.PLANNED) 1 else 0}"
            scroll(label);compose.onNodeWithText(label).assertIsDisplayed()
        }
        modeMenu("Desactivar Modo Sprint");compose.onNodeWithText("Desactivar").performClick()
        compose.waitUntil(10000) { runBlocking { !app.nodeRepository.getNode(layer.id)!!.sprintMode } }
        scroll("Implement feature");compose.onNodeWithContentDescription("Completar: Implement feature").assertExists()
        assertNull(runBlocking { app.nodeRepository.getNode(task.id)!!.workState })
        scroll("Progreso");compose.onNodeWithText("Progreso").assertIsDisplayed()
        compose.onNodeWithText("0% · 1 de 1 pendientes").assertIsDisplayed()
    }
    @Test fun verticalSectionsCountersCollapseAndStructureRemainIndependent() {
        runBlocking { app.nodeRepository.setSprintMode(layer.id,true) };open()
        scroll("▼ Capas y notas · 2");compose.onNodeWithText("▼ Capas y notas · 2").assertExists()
        scroll("▼ No planificada · 1");compose.onNodeWithText("▼ No planificada · 1").performClick()
        compose.onNodeWithText("Implement feature").assertDoesNotExist()
        compose.onNodeWithText("▶ No planificada · 1").assertExists().performClick()
        scroll("Implement feature");compose.onNodeWithText("Implement feature").assertExists()
        for(state in WorkState.entries.drop(1)) { scroll("▼ ${state.label} · 0");compose.onNodeWithText("▼ ${state.label} · 0").assertExists() }
        scroll("Normal sublayer");compose.onNodeWithText("Normal sublayer").performClick();await("CAPA 2")
        assertFalse(runBlocking { app.nodeRepository.getProjectNodes(project.id).single { it.title=="Normal sublayer" }.sprintMode })
    }
    @Test fun sprintCheckConfirmsEachPhaseAndNormalCheckStillToggles() {
        runBlocking { app.nodeRepository.setSprintMode(layer.id,true) };open()
        for(state in WorkState.entries.dropLast(1)) {
            scroll("CAPA 1")
            compose.onNodeWithText("Progreso Sprint · ${state.ordinal*25} %").assertIsDisplayed()
            scroll("Implement feature")
            val check=compose.onNodeWithContentDescription("Confirmar ${state.label} y avanzar a ${state.next().label}: Implement feature")
            check.assertIsDisplayed().assertIsEnabled()
                .assert(SemanticsMatcher.expectValue(androidx.compose.ui.semantics.SemanticsProperties.StateDescription,"Pendiente"))
            check.assert(hasText("${state.ordinal+1}/5").not())
            compose.onNodeWithText("${state.ordinal+1}/5").assertIsDisplayed()
            check.performClick()
            compose.waitUntil(10000) { runBlocking {
                val current=app.nodeRepository.getNode(task.id)!!
                current.workState==state.next() && current.isCompleted==state.next().completed
            } }
        }
        scroll("Implement feature")
        scroll("CAPA 1");compose.onNodeWithText("Progreso Sprint · 100 %").assertIsDisplayed()
        scroll("Implement feature")
        compose.onNodeWithText("5/5").assertIsDisplayed()
        val finalCheck=compose.onNodeWithContentDescription("Sprint validado: Implement feature")
        finalCheck.assertIsDisplayed().assertIsNotEnabled()
            .assert(SemanticsMatcher.expectValue(androidx.compose.ui.semantics.SemanticsProperties.StateDescription,"Completada"))
        val finalNode=runBlocking { app.nodeRepository.getNode(task.id)!! }
        finalCheck.performClick();compose.waitForIdle()
        assertEquals(finalNode,runBlocking { app.nodeRepository.getNode(task.id)!! })
        assertEquals(1,runBlocking { app.database.nodeEventDao().forNode(task.id).count { it.type=="COMPLETED" } })
        assertEquals(0,runBlocking { app.database.nodeEventDao().forNode(task.id).count { it.type=="REOPENED" } })
        compose.onNodeWithTag("node-options:${task.id}").performClick()
        compose.onNodeWithText("Cambiar estado").performScrollTo().performClick();await("Cambiar estado · Implement feature")
        compose.onNode(hasText("Haciendo") and hasAnyAncestor(isDialog())).performClick()
        compose.waitUntil(10000) { runBlocking { app.nodeRepository.getNode(task.id)!!.workState==WorkState.DOING } }
        scroll("Implement feature")
        compose.onNodeWithContentDescription("Confirmar Haciendo y avanzar a Terminada: Implement feature")
            .assertIsEnabled().assert(SemanticsMatcher.expectValue(androidx.compose.ui.semantics.SemanticsProperties.StateDescription,"Pendiente"))
        compose.onNodeWithContentDescription("Estado: Haciendo. Activar para avanzar a Terminada.").performClick()
        compose.waitUntil(10000) { runBlocking { app.nodeRepository.getNode(task.id)!!.workState==WorkState.DONE } }
        modeMenu("Desactivar Modo Sprint");compose.onNodeWithText("Desactivar").performClick()
        compose.waitUntil(10000) { runBlocking { !app.nodeRepository.getNode(layer.id)!!.sprintMode } }
        scroll("Implement feature")
        compose.onNodeWithContentDescription("Marcar pendiente: Implement feature").performClick()
        compose.waitUntil(10000) { runBlocking { !app.nodeRepository.getNode(task.id)!!.isCompleted } }
        assertNull(runBlocking { app.nodeRepository.getNode(task.id)!!.workState })
        scroll("Implement feature")
        compose.onNodeWithContentDescription("Completar: Implement feature").performClick()
        compose.waitUntil(10000) { runBlocking { app.nodeRepository.getNode(task.id)!!.isCompleted } }
        assertNull(runBlocking { app.nodeRepository.getNode(task.id)!!.workState })
    }
    @Test fun sprintProgressShowsRoundedMixtureAndNoPercentageWithoutTasks() {
        val tasks=runBlocking {
            app.nodeRepository.setSprintMode(layer.id,true)
            val result=listOf(task)+listOf("Planned","Doing","Validated").map { app.nodeRepository.createNode(project.id,layer.id,it) }
            for ((node,phase) in result.zip(listOf(WorkState.UNPLANNED,WorkState.PLANNED,WorkState.DOING,WorkState.VALIDATED)))
                app.nodeRepository.setWorkState(node.id,phase)
            result
        }
        open();scroll("CAPA 1")
        compose.onNodeWithText("Progreso Sprint · 44 %").assertIsDisplayed()
        compose.onNodeWithText("Progreso").assertDoesNotExist()
        runBlocking { tasks.forEach { app.nodeRepository.deleteNode(it.id) } }
        scroll("CAPA 1")
        scroll("No hay tareas por realizar");compose.onAllNodesWithText("No hay tareas por realizar").onFirst().assertIsDisplayed()
        compose.onNodeWithText("Progreso Sprint",substring=true).assertDoesNotExist()
    }

}
