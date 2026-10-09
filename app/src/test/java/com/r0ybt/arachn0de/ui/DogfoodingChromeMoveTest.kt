package com.r0ybt.arachn0de.ui

import androidx.compose.ui.test.*
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.test.core.app.ApplicationProvider
import com.r0ybt.arachn0de.Arachn0deApplication
import com.r0ybt.arachn0de.domain.model.*
import com.r0ybt.arachn0de.ui.theme.Arachn0deTheme
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class) @Config(application=com.r0ybt.arachn0de.security.LegacyUiTestApplication::class,sdk=[28])
class DogfoodingChromeMoveTest {
    private val compose=createComposeRule()
    private lateinit var app:Arachn0deApplication
    private lateinit var project:Project
    private lateinit var layer:Node
    private lateinit var inner:Node
    @get:Rule val rules:RuleChain=RuleChain.outerRule(object:ExternalResource() {
        override fun before() { app=ApplicationProvider.getApplicationContext();runBlocking {
            project=app.projectRepository.createProject("Choroy Reader")
            layer=app.nodeRepository.createNode(project.id,null,"Bugs",purpose=NodePurpose.LAYER)
            inner=app.nodeRepository.createNode(project.id,layer.id,"Actualizador",purpose=NodePurpose.LAYER)
            app.projectRepository.createProject("Other project")
        } }
        override fun after() { app.database.close() }
    }).around(compose)
    private fun await(text:String) {
        if (text in listOf("Bugs", "Actualizador", "CAPA 1", "CAPA 2")) {
            compose.waitUntil(10000) { compose.onAllNodesWithTag("nodes-list").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithTag("nodes-list").performScrollToNode(hasText(text))
        }
        compose.waitUntil(10000) { org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }
    }
    private fun title(text:String)=compose.onNodeWithTag("app-bar-title").assertTextEquals(text)
    @Test fun projectTitleSurvivesDeepNavigationRestorationAndSwitching() {
        val restoration=StateRestorationTester(compose)
        restoration.setContent { Arachn0deTheme { AppSafeArea { AppRoot(app.projectRepository,app.nodeRepository) } } }
        await("Choroy Reader");title("Arachn0de");compose.onNodeWithText("Choroy Reader").performClick();await("Bugs");title("Choroy Reader")
        compose.onNodeWithTag("nodes-list").performScrollToNode(hasText("Bugs"));compose.onNodeWithText("Bugs").performClick();await("Actualizador");title("Choroy Reader")
        compose.onNodeWithTag("nodes-list").performScrollToNode(hasText("Actualizador"));compose.onNodeWithText("Actualizador").performClick();await("CAPA 2");title("Choroy Reader")
        restoration.emulateSavedInstanceStateRestore();await("CAPA 2");title("Choroy Reader")
        compose.onNodeWithTag("nodes-list").performScrollToNode(hasContentDescription("Volver a la capa anterior"));compose.onNodeWithContentDescription("Volver a la capa anterior").performClick();await("CAPA 1");title("Choroy Reader")
        runBlocking { app.projectRepository.updateProject(project.id,"Choroy Renamed","") }
        compose.waitUntil(10000) { compose.onAllNodes(hasTestTag("app-bar-title") and hasText("Choroy Renamed")).fetchSemanticsNodes().isNotEmpty() };title("Choroy Renamed")
        compose.onNodeWithContentDescription("Abrir menú").performClick();compose.onNodeWithText("Proyectos").performClick();await("Other project");title("Arachn0de")
        compose.onNodeWithText("Other project").performClick();compose.waitUntil(10000) { compose.onAllNodes(hasTestTag("app-bar-title") and hasText("Other project")).fetchSemanticsNodes().isNotEmpty() };title("Other project")
    }
    @Test fun longTitleUsesSingleLineEllipsis() {
        val name="Proyecto muy largo ".repeat(5)
        compose.setContent { Arachn0deTheme { Box(Modifier.width(180.dp).testTag("header-width")) { HeaderBar({},title=name) } } }
        title(name);compose.onNodeWithTag("app-bar-title").assertIsDisplayed()
        var result:androidx.compose.ui.text.TextLayoutResult?=null
        compose.onNodeWithTag("app-bar-title").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.GetTextLayoutResult) { action -> val out=mutableListOf<androidx.compose.ui.text.TextLayoutResult>();action(out);result=out.single() }
        org.junit.Assert.assertEquals(1,result!!.lineCount)
        org.junit.Assert.assertTrue(compose.onNodeWithTag("app-bar-title").fetchSemanticsNode().boundsInRoot.right <= compose.onNodeWithTag("header-width").fetchSemanticsNode().boundsInRoot.right)
    }
    @Test fun moveSelectorOffersOnlyValidContainersAndExcludesOwnSubtree() {
        val pending=Node("pending","p",null,"Pending","",false,0,1,1,false)
        val completed=pending.copy(id="done",title="Done",isCompleted=true)
        val note=pending.copy(id="note",title="Note",purpose=NodePurpose.NOTE)
        val root=pending.copy(id="source",title="Source",purpose=NodePurpose.LAYER)
        val child=pending.copy(id="descendant",parentId="source",purpose=NodePurpose.LAYER)
        val destination=pending.copy(id="layer",title="Layer",purpose=NodePurpose.LAYER)
        compose.setContent { Arachn0deTheme { LayerNavigator(listOf(pending,completed,note,root,child,destination),listOf("source"),{},null,{},"Project",{},{},movingId="source") } }
        for(id in listOf("done","note","source","descendant")) compose.onNodeWithTag("navigator-node:$id").assertDoesNotExist()
        for(id in listOf("pending","layer")) compose.onNodeWithTag("navigator-node:$id").assertExists()
        compose.onNodeWithTag("navigator-project").assertExists()
    }
}
