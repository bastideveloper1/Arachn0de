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

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28])
class NodeCopyUiTest {
    private val compose=createComposeRule()
    private lateinit var app:Arachn0deApplication
    private lateinit var project:String
    private lateinit var root:String
    private lateinit var note:String
    private val clipboard get()=app.getSystemService(ClipboardManager::class.java)
    @get:Rule val rules:RuleChain=RuleChain.outerRule(object:ExternalResource(){
        override fun before(){
            app=ApplicationProvider.getApplicationContext()
            runBlocking{
                project=app.projectRepository.createProject("Proyecto").id
                root=app.nodeRepository.createNode(project,null,"Bugs","Descripción raíz").id
                val task=app.nodeRepository.createNode(project,root,"Pendiente",obligation=Obligation(25000,"CLP")).id
                app.nodeRepository.setCompleted(task,true)
                note=app.nodeRepository.createNode(project,root,"Investigación","Texto informativo",purpose=NodePurpose.NOTE).id
            }
        }
        override fun after(){app.database.close()}
    }).around(compose)
    private fun await(text:String)=compose.waitUntil(10000){compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()}
    private fun mount(){
        compose.setContent{Arachn0deTheme{AppSafeArea{AppRoot(app.projectRepository,app.nodeRepository)}}}
        await("Proyecto");compose.onNodeWithText("Proyecto").performClick();await("Bugs")
        compose.onNodeWithTag("nodes-list").performScrollToNode(hasText("Bugs"))
    }
    private fun copied(expected:String){
        compose.waitUntil(10000){
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
            clipboard.primaryClip?.getItemAt(0)?.text?.toString()==expected
        }
        assertEquals("Contexto copiado",ShadowToast.getTextOfLatestToast())
    }
    private fun expected(id:String,descendants:Boolean)=runBlocking{
        NodeMarkdownRenderer.render(NodeExportSnapshot.capture(NodeTreeSnapshot(app.nodeRepository.getProjectNodes(project)),id,descendants))
    }
    @Test fun cardMenuCopiesBothScopesWithoutWritesOrNavigationAndLeafHasNoRedundantScope(){
        mount()
        val before=runBlocking{app.nodeRepository.getProjectNodes(project)}
        compose.onNodeWithContentDescription("Más opciones").performClick()
        compose.onNode(hasText("Copiar este elemento") and hasAnyAncestor(isDialog())).performScrollTo().performClick()
        copied(expected(root,false))
        compose.onNodeWithText("Bugs").assertExists()
        compose.onNodeWithContentDescription("Más opciones").performClick()
        compose.onNode(hasText("Copiar con descendientes") and hasAnyAncestor(isDialog())).performScrollTo().performClick()
        copied(expected(root,true))
        compose.onNodeWithText("Bugs").performClick();await("CAPA 1")
        compose.onNodeWithTag("nodes-list").performScrollToNode(hasText("Investigación"))
        compose.onNode(hasContentDescription("Más opciones") and hasAnyAncestor(hasText("Investigación"))).performClick()
        compose.onNode(hasText("Copiar con descendientes") and hasAnyAncestor(isDialog())).assertDoesNotExist()
        compose.onNode(hasText("Copiar este elemento") and hasAnyAncestor(isDialog())).performScrollTo().performClick()
        copied(expected(note,false))
        assertEquals(before,runBlocking{app.nodeRepository.getProjectNodes(project)})
        assertEquals(13, app.database.openHelper.writableDatabase.version)
    }
    @Test fun openNodeContextCopiesFromItsOwnRoot(){
        mount();compose.onNodeWithText("Bugs").performClick();await("CAPA 1")
        compose.onNodeWithTag("nodes-list").performScrollToNode(hasText("Copiar con descendientes"))
        compose.onNodeWithText("Copiar con descendientes").performClick()
        copied(expected(root,true))
        compose.onNodeWithText("CAPA 1").assertExists()
    }
}
