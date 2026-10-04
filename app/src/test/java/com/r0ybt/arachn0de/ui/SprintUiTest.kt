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
        open();modeMenu("Modo Sprint");await("Activar Modo Sprint")
        compose.onNodeWithText("Cancelar").performClick();assertFalse(runBlocking { app.nodeRepository.getNode(layer.id)!!.sprintMode })
        modeMenu("Modo Sprint");compose.onNodeWithText("Activar").performClick()
        compose.waitUntil(10000) { runBlocking { app.nodeRepository.getNode(layer.id)!!.sprintMode } }
        scroll("▼ No planificada · 1");compose.onNodeWithText("▼ No planificada · 1").assertExists()
        modeMenu("Desactivar Modo Sprint");compose.onNodeWithText("Desactivar").performClick()
        compose.waitUntil(10000) { runBlocking { !app.nodeRepository.getNode(layer.id)!!.sprintMode } }
        scroll("Implement feature");compose.onNodeWithContentDescription("Completar: Implement feature").assertExists()
        assertNull(runBlocking { app.nodeRepository.getNode(task.id)!!.workState })
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
    @Test fun primaryControlAdvancesAndMenuCorrectsWithoutWrap() {
        runBlocking { app.nodeRepository.setSprintMode(layer.id,true) };open()
        for(state in WorkState.entries.dropLast(1)) {
            scroll("Implement feature")
            compose.onNodeWithContentDescription("Estado: ${state.label}. Activar para avanzar a ${state.next().label}.").performClick()
            compose.waitUntil(10000) { runBlocking { app.nodeRepository.getNode(task.id)!!.workState==state.next() } }
        }
        scroll("Implement feature");compose.onNodeWithContentDescription("Estado: Validada. Estado final.").assertIsNotEnabled()
        compose.onNodeWithTag("node-options:${task.id}").performClick();compose.onNodeWithText("Cambiar estado").performScrollTo().performClick();await("Cambiar estado · Implement feature")
        compose.onNode(hasText("Haciendo") and hasAnyAncestor(isDialog())).performClick()
        compose.waitUntil(10000) { runBlocking { app.nodeRepository.getNode(task.id)!!.workState==WorkState.DOING } }
        assertFalse(runBlocking { app.nodeRepository.getNode(task.id)!!.isCompleted })
        assertEquals(1,runBlocking { app.database.nodeEventDao().forNode(task.id).count { it.type=="COMPLETED" } })
        assertEquals(1,runBlocking { app.database.nodeEventDao().forNode(task.id).count { it.type=="REOPENED" } })
    }
}
