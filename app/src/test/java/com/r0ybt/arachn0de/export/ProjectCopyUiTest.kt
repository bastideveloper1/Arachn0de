package com.r0ybt.arachn0de.export

import android.content.ClipboardManager
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import com.r0ybt.arachn0de.Arachn0deApplication
import com.r0ybt.arachn0de.domain.model.*
import com.r0ybt.arachn0de.ui.*
import com.r0ybt.arachn0de.ui.theme.Arachn0deTheme
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowToast

@RunWith(RobolectricTestRunner::class) @Config(sdk=[28])
class ProjectCopyUiTest {
    private val compose=createComposeRule()
    private lateinit var app:Arachn0deApplication
    private lateinit var project:Project
    @get:Rule val rules:RuleChain=RuleChain.outerRule(object:ExternalResource() {
        override fun before() { app=ApplicationProvider.getApplicationContext();runBlocking {
            project=app.projectRepository.createProject("Proyecto exportable","Contenido del proyecto")
            val layer=app.nodeRepository.createNode(project.id,null,"Android")
            app.nodeRepository.createNode(project.id,layer.id,"Tarea A")
            app.nodeRepository.createNode(project.id,layer.id,"Nota B",purpose=NodePurpose.NOTE)
        } }
        override fun after() { app.database.close() }
    }).around(compose)
    private fun await(label:String)=compose.waitUntil(10000) { compose.onAllNodesWithText(label).fetchSemanticsNodes().isNotEmpty() }
    private fun mount() { compose.setContent { Arachn0deTheme { AppSafeArea { AppRoot(app.projectRepository,app.nodeRepository) } } };await(project.name) }
    private fun expected(descendants:Boolean)=runBlocking { NodeMarkdownRenderer.render(NodeExportSnapshot.captureProject(project,NodeTreeSnapshot(app.nodeRepository.getProjectNodes(project.id)),descendants)) }
    private fun copied(text:String) {
        val clipboard=app.getSystemService(ClipboardManager::class.java)
        compose.waitUntil(10000) { org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();clipboard.primaryClip?.getItemAt(0)?.text?.toString()==text }
        assertEquals("Contexto copiado",ShadowToast.getTextOfLatestToast())
    }
    @Test fun dashboardOverflowOffersBothScopesWithFeedbackAndNoWrites() {
        mount();val before=runBlocking { app.nodeRepository.getProjectNodes(project.id) }
        compose.onNodeWithText("Copiar").assertDoesNotExist()
        compose.onNodeWithContentDescription("Opciones del proyecto").performClick();await("Copiar")
        compose.onNodeWithText("Copiar").performScrollTo().performClick();copied(expected(false))
        compose.onNodeWithContentDescription("Opciones del proyecto").performClick()
        compose.waitUntil(10000) { compose.onAllNodes(hasText("Copiar con descendientes") and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Copiar con descendientes").performScrollTo().performClick();copied(expected(true))
        assertEquals(before,runBlocking { app.nodeRepository.getProjectNodes(project.id) })
        compose.onNodeWithText("Proyectos").assertExists()
    }
    @Test fun projectContextOverflowCopiesWholeProjectWithoutPermanentCopyControls() {
        mount();compose.onNodeWithText(project.name).performClick();await("Android")
        compose.onNodeWithText("Copiar con descendientes").assertDoesNotExist()
        compose.onNodeWithContentDescription("Opciones del proyecto").performScrollTo().performClick();await("Copiar con descendientes")
        compose.onNodeWithText("Copiar con descendientes").performScrollTo().performClick();copied(expected(true))
        compose.onNodeWithText("Proyecto raíz").assertExists()
    }
    @Test fun oversizedProjectKeepsClipboardAndReportsExistingLimit() {
        runBlocking { app.projectRepository.updateProject(project.id,project.name,"*".repeat(100001)) }
        val clipboard=app.getSystemService(ClipboardManager::class.java)
        clipboard.setPrimaryClip(android.content.ClipData.newPlainText("Anterior","Contenido anterior"))
        mount()
        compose.onNodeWithContentDescription("Opciones del proyecto").performClick();await("Copiar")
        compose.onNodeWithText("Copiar").performScrollTo().performClick()
        compose.waitUntil(10000) {
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
            ShadowToast.getTextOfLatestToast()?.contains("demasiado grande")==true
        }
        assertEquals("Contenido anterior",clipboard.primaryClip!!.getItemAt(0).text.toString())
    }

}
