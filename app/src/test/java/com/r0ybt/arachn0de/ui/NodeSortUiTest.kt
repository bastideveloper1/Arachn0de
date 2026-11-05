package com.r0ybt.arachn0de.ui

import android.content.ClipboardManager
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import com.r0ybt.arachn0de.Arachn0deApplication
import com.r0ybt.arachn0de.MainActivity
import com.r0ybt.arachn0de.domain.model.*
import com.r0ybt.arachn0de.export.*
import com.r0ybt.arachn0de.ui.state.*
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class) @Config(application=com.r0ybt.arachn0de.security.LegacyUiTestApplication::class,sdk=[28])
class NodeSortUiTest {
    private val compose=createAndroidComposeRule<MainActivity>()
    private lateinit var app:Arachn0deApplication
    private lateinit var project:Project
    private lateinit var layer:Node
    private lateinit var a:Node
    private lateinit var d:Node
    @get:Rule val rules:RuleChain=RuleChain.outerRule(object:ExternalResource() {
        override fun before() { app=ApplicationProvider.getApplicationContext();runBlocking {
            project=app.projectRepository.createProject("Sort project")
            a=app.nodeRepository.createNode(project.id,null,"A",dueAt=15,priority=Priority.HIGH)
            app.nodeRepository.createNode(project.id,null,"B",purpose=NodePurpose.NOTE)
            app.nodeRepository.createNode(project.id,null,"C",dueAt=5,priority=Priority.LOW)
            d=app.nodeRepository.createNode(project.id,null,"D",dueAt=20,priority=Priority.MEDIUM)
            layer=app.nodeRepository.createNode(project.id,null,"Cuentas",purpose=com.r0ybt.arachn0de.domain.model.NodePurpose.LAYER)
            app.nodeRepository.createNode(project.id,layer.id,"Idea",purpose=NodePurpose.NOTE)
        } }
        override fun after() { app.database.close() }
    }).around(compose)
    private fun await(label:String) { try { compose.waitUntil(10000) { compose.onAllNodesWithText(label).fetchSemanticsNodes().isNotEmpty() } } catch(e:Throwable) { throw AssertionError("SORT-$label\n"+compose.onRoot().printToString(),e) } }
    private fun open() { await(project.name);compose.onNodeWithText(project.name).performClick();await("A") }
    private fun order():SemanticsNodeInteraction {
        compose.onNodeWithTag("nodes-list").performScrollToIndex(0)
        return compose.onNodeWithContentDescription("Ordenar")
    }
    private fun node(label:String):SemanticsNodeInteraction {
        compose.onNodeWithTag("nodes-list").performScrollToNode(hasText(label))
        return compose.onNode(hasText(label) and hasAnyAncestor(hasTestTag("nodes-list")))
    }
    private fun menu(id:String) {
        compose.onNodeWithTag("nodes-list").performScrollToNode(hasTestTag("node-options:$id"))
        compose.onNodeWithTag("node-options:$id").performClick()
    }
    private fun first(expected:String,labels:Set<String> = emptySet()) {
        val id=persisted().first {it.title==expected}.id
        compose.onNodeWithTag("nodes-list").performScrollToNode(hasText(expected))
        compose.onNodeWithTag("node-title:$id",useUnmergedTree=true).assertIsDisplayed()
    }
    private fun dueCriterion() {
        compose.onNodeWithTag("sort-criteria").performScrollToNode(hasTestTag("sort-rule:DUE"))
        compose.onNodeWithTag("sort-check:DUE").assertIsDisplayed().performClick()
    }
    private fun persisted()=runBlocking { app.nodeRepository.getProjectNodes(project.id) }
    @Test fun dashboardUsesExistingHierarchyFallbackAndTagsRemainAccessibleFromDrawer() {
        await(project.name)
        compose.onNodeWithText("Proyectos").assertDoesNotExist()
        compose.onNodeWithText("Etiquetas").assertDoesNotExist()
        compose.onNodeWithContentDescription("Proyecto sin fotografía").assertExists()
        compose.onNodeWithContentDescription("Abrir menú").performClick()
        compose.onNodeWithText("Recursos").performScrollTo().performClick(); compose.onNodeWithText("Etiquetas").performScrollTo().performClick()
        await("Etiquetas globales")
        compose.onNodeWithText("Cerrar").performClick()
        await(project.name)
    }
    @Test fun savedCriteriaDoNotMoveCardsUntilApplyAndCanBeReappliedAfterManualMovement() {
        open();val before=persisted()
        order().performClick()
        dueCriterion()
        compose.waitUntil(10000) {runBlocking {app.database.nodeSortPreferenceDao().all().any {it.context=="${project.id}:project-root" && it.mode.startsWith(SortConfigurationCodec.PREFIX)}}}
        assertEquals(before,persisted())
        compose.onNodeWithTag("sort-apply").performClick()
        compose.waitUntil(10000) {persisted().filter {it.parentId==null}.minBy {it.position}.title=="C"}
        first("C")
        val c=persisted().first {it.title=="C"}
        assertTrue(runBlocking {app.nodeRepository.reorderNodeTo(a.id,null,c.id)})
        assertEquals("A",persisted().filter {it.parentId==null}.minBy {it.position}.title)
        first("A")
        order().performClick();compose.onNodeWithTag("sort-apply").performClick()
        compose.waitUntil(10000) {persisted().filter {it.parentId==null}.minBy {it.position}.title=="C"}
        first("C")
        assertEquals(before.map {it.copy(position=0,updatedAt=0)}.sortedBy {it.id},persisted().map {it.copy(position=0,updatedAt=0)}.sortedBy {it.id})
    }
    @Test fun layerOverrideSurvivesRecreationAndCanReturnToCurrentCoreConfiguration() {
        open();order().performClick();dueCriterion();compose.onNodeWithTag("sort-apply").performClick()
        node("Cuentas").performClick();node("Idea").assertExists()
        order().performClick();compose.onNodeWithTag("sort-inherit").assertIsOn()
        compose.onNodeWithTag("sort-manual").performClick();compose.onNodeWithTag("sort-apply").performClick()
        compose.activityRule.scenario.recreate();await("Idea")
        order().performClick();compose.onNodeWithTag("sort-inherit").assertIsOff();compose.onNodeWithTag("sort-manual").assertIsOn()
        compose.onNodeWithTag("sort-inherit").performClick()
        compose.onNodeWithTag("sort-inherit").assertIsOn();compose.onNodeWithTag("sort-manual").assertIsOff()
        compose.onNodeWithTag("sort-criteria").performScrollToNode(hasTestTag("sort-rule:DUE"))
        compose.onNodeWithTag("sort-check:DUE").assertIsOn();compose.onNodeWithTag("sort-apply").performClick()
    }
    @Test fun layersFirstIsSavedSeparatelyAndOnlyChangesPresentationWhenApplied() {
        open();val before=persisted()
        order().performClick();compose.onNodeWithTag("sort-layers-first").performClick()
        assertEquals(before,persisted())
        compose.onNodeWithTag("sort-apply").performClick()
        compose.waitUntil(10000) {runBlocking {app.database.nodeSortPreferenceDao().all().any {it.context=="${project.id}:project-root" && it.layersFirst}}}
        first("Cuentas",setOf("A","Cuentas"))
        order().performClick();compose.onNodeWithTag("sort-layers-first").assertIsOn()
    }
}
