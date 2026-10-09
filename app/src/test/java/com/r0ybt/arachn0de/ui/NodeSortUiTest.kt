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
    private fun sort(mode:NodeSortMode) {
        order().performClick()
        compose.onNodeWithText(mode.label).performScrollTo().performClick()
        order().assert(hasStateDescription("Orden: ${mode.label}" + if(mode!=NodeSortMode.MANUAL) "; arrastre deshabilitado" else ""))
    }
    private fun node(label:String):SemanticsNodeInteraction {
        compose.onNodeWithTag("nodes-list").performScrollToNode(hasText(label))
        return compose.onNode(hasText(label) and hasAnyAncestor(hasTestTag("nodes-list")))
    }
    private fun menu(id:String) {
        compose.onNodeWithTag("nodes-list").performScrollToNode(hasTestTag("node-options:$id"))
        compose.onNodeWithTag("node-options:$id").performClick()
    }
    private fun first(expected:String, labels:Set<String> = setOf("A","B","C","D","Cuentas")) {
        compose.onNodeWithTag("nodes-list").performScrollToIndex(2)
        compose.waitUntil(10000) {
            val nodes=compose.onAllNodes(hasAnyAncestor(hasTestTag("nodes-list"))).fetchSemanticsNodes()
                .filter { n -> n.config.getOrElse(androidx.compose.ui.semantics.SemanticsProperties.Text) { emptyList() }.any { it.text in labels } && n.boundsInRoot.height>0 }
            nodes.minByOrNull { it.boundsInRoot.top }?.config?.getOrElse(androidx.compose.ui.semantics.SemanticsProperties.Text) { emptyList() }?.any { it.text==expected }==true
        }
    }
    private fun persisted()=runBlocking { app.nodeRepository.getProjectNodes(project.id) }
    @Test fun allModesHaveSelectedSemanticsAndPreservePositionsAndStructuralCopy() {
        open();val original=persisted();val events=runBlocking { app.database.nodeEventDao().all() }
        first("A")
        for(mode in NodeSortMode.entries) {
            sort(mode)
            val expected=NodePresentationSort.children(original.filter { it.parentId==null },mode).first().title
            first(expected)
            order().performClick()
            compose.onNodeWithText(mode.label).performScrollTo().assertIsSelected()
            compose.onNodeWithText("Cancelar").performClick()
            assertEquals(original,persisted())
        }
        sort(NodeSortMode.DUE_ASC)
        compose.onNodeWithTag("nodes-list").performScrollToIndex(0)
        compose.onNodeWithContentDescription("Opciones del proyecto").performScrollTo().performClick()
        compose.onNodeWithText("Copiar con descendientes").performScrollTo().performClick()
        val expected=PendingChatRenderer.render(NodeTreeSnapshot(original),null,project)
        val clipboard=app.getSystemService(ClipboardManager::class.java)
        compose.waitUntil(10000) { org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();clipboard.primaryClip?.getItemAt(0)?.text?.toString()==expected }
        sort(NodeSortMode.MANUAL);first("A")
        assertEquals(original,persisted());assertEquals(events,runBlocking { app.database.nodeEventDao().all() })
    }
    @Test fun projectDefaultIsClearlyLabeledInheritedAndManualOverrideSurvivesRecreation() {
        open();order().performClick()
        compose.onNodeWithText("Orden predeterminado del Proyecto").assertExists()
        compose.onNodeWithText("Las Capas sin orden propio utilizan este criterio.").assertExists()
        compose.onNodeWithText(NodeSortMode.DUE_PRIORITY.label).performScrollTo().performClick()
        node("Cuentas").performClick();node("Idea").assertExists()
        order().assert(hasStateDescription("Orden: Vencimiento y prioridad; arrastre deshabilitado"))
        sort(NodeSortMode.MANUAL)
        compose.activityRule.scenario.recreate();await("Nuevo elemento");node("Idea").assertExists()
        order().assert(hasStateDescription("Orden: Manual"))
        order().performClick();compose.onNodeWithText("Usar orden predeterminado del Proyecto").performScrollTo().performClick()
        order().assert(hasStateDescription("Orden: Vencimiento y prioridad; arrastre deshabilitado"))
    }
    @Test fun activityRecreationRestoresIndependentProjectAndLayerModes() {
        open();sort(NodeSortMode.DUE_ASC);first("C")
        node("Cuentas").performClick();node("Idea").assertExists()
        order().assert(hasStateDescription("Orden: Vencimiento próximo; arrastre deshabilitado"))
        sort(NodeSortMode.CREATED_OLDEST)
        compose.activityRule.scenario.recreate();await("Nuevo elemento");node("Idea").assertExists()
        order().assert(hasStateDescription("Orden: Más antiguos; arrastre deshabilitado"))
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() };await("Nuevo elemento")
        order().assert(hasStateDescription("Orden: Vencimiento próximo; arrastre deshabilitado"))
        node("Cuentas").performClick();node("Idea").assertExists()
        order().assert(hasStateDescription("Orden: Más antiguos; arrastre deshabilitado"))
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        await("Proyectos");compose.onNodeWithText(project.name).performClick();await("Nuevo elemento")
        order().assert(hasStateDescription("Orden: Vencimiento próximo; arrastre deshabilitado"))
    }
    @Test fun differentProjectsKeepIndependentRootPreferencesAndDashboardHasNoSelector() {
        runBlocking {
            val other=app.projectRepository.createProject("Other project")
            app.nodeRepository.createNode(other.id,null,"Other task",dueAt=10)
        }
        open();sort(NodeSortMode.DUE_ASC)
        compose.onNodeWithContentDescription("Abrir menú").performClick()
        compose.onNodeWithText("Proyectos").performScrollTo().performClick();await("Proyectos")
        compose.onNodeWithContentDescription("Ordenar").assertDoesNotExist()
        compose.onNodeWithText("Other project").performClick();await("Other task")
        order().assert(hasStateDescription("Orden: Manual"))
        sort(NodeSortMode.PRIORITY)
        compose.onNodeWithContentDescription("Abrir menú").performClick()
        compose.onNodeWithText("Proyectos").performScrollTo().performClick();await("Proyectos")
        compose.onNodeWithText(project.name).performClick();await("Nuevo elemento")
        order().assert(hasStateDescription("Orden: Vencimiento próximo; arrastre deshabilitado"))
    }
    @Test fun automaticDragCannotWriteAndManualDragWorksAgain() {
        runBlocking {
            persisted().filter { it.parentId==null }.forEach { app.nodeRepository.deleteNode(it.id) }
            app.nodeRepository.createNode(project.id,null,"Alpha")
            app.nodeRepository.createNode(project.id,null,"Beta")
        }
        await(project.name);compose.onNodeWithText(project.name).performClick();await("Alpha")
        sort(NodeSortMode.DUE_ASC)
        compose.onNodeWithTag("nodes-list").performScrollToNode(hasText("Alpha"))
        val original=persisted();drag("Alpha","Beta")
        assertEquals(original,persisted())
        if(compose.onAllNodesWithText("Salir").fetchSemanticsNodes().isNotEmpty()) compose.onNodeWithText("Salir").performClick()
        sort(NodeSortMode.MANUAL)
        compose.onNodeWithTag("nodes-list").performScrollToNode(hasText("Alpha"));drag("Alpha","Beta")
        compose.waitUntil(10000) { persisted().first { it.parentId==null }.title=="Beta" }
        sort(NodeSortMode.DUE_ASC);sort(NodeSortMode.MANUAL)
        assertEquals(listOf("Beta","Alpha"),persisted().map { it.title })
    }
    private fun drag(fromLabel:String,toLabel:String) {
        val from=compose.onNodeWithText(fromLabel).fetchSemanticsNode().boundsInRoot.center
        val to=compose.onNodeWithText(toLabel).fetchSemanticsNode().boundsInRoot.center
        compose.mainClock.autoAdvance=false
        compose.onRoot().performTouchInput { down(from);advanceEventTime(700);moveTo(from) }
        compose.mainClock.advanceTimeBy(32)
        repeat(6) { step -> compose.onRoot().performTouchInput { moveTo(from+(to-from)*((step+1)/6f),delayMillis=40) };compose.mainClock.advanceTimeBy(48) }
        compose.onRoot().performTouchInput { up() };compose.mainClock.autoAdvance=true;compose.waitForIdle()
    }
    @Test fun automaticSelectionBulkDeleteUsesIdentitiesAndKeepsUnselectedRows() {
        open();sort(NodeSortMode.DUE_ASC);first("C")
        menu(a.id);compose.onNodeWithText("Seleccionar").performScrollTo().performClick()
        node("D").performClick();await("2 seleccionados")
        compose.onNodeWithText("Eliminar").performClick();await("¿Eliminar 2 elementos?")
        compose.onNodeWithText("Eliminar seleccionados").performClick();await("2 elementos eliminados")
        val retained=persisted()
        assertFalse(retained.any { it.id in setOf(a.id,d.id) })
        assertTrue(retained.any { it.title=="C" });assertTrue(retained.any { it.title=="B" })
        compose.onNodeWithText("2 seleccionados").assertDoesNotExist()
        order().assert(hasStateDescription("Orden: Vencimiento próximo; arrastre deshabilitado"))
    }
    @Test fun automaticBulkMoveKeepsGroupAndRelationsAndDestinationSort() {
        val tag=runBlocking { app.nodeRepository.tags.create("Test") }
        val personId=java.util.UUID.randomUUID().toString()
        runBlocking { app.personRepository.save(personId,"Persona",null) }
        val rows=runBlocking { app.nodeRepository.createBatch(project.id,null,"sort-group",listOf(
            GeneratedNodeSpec("Moved late","",NodePurpose.ACTION,30,null,Priority.HIGH),
            GeneratedNodeSpec("Moved early","",NodePurpose.ACTION,2,null,Priority.HIGH)),responsibleIds=setOf(personId),tagIds=setOf(tag.id)) }
        open();sort(NodeSortMode.DUE_ASC)
        node("Cuentas").performClick();node("Idea").assertExists();sort(NodeSortMode.DUE_ASC)
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() };await("Nuevo elemento")
        menu(rows[0].id);compose.onNodeWithText("Seleccionar").performScrollTo().performClick();node("Moved early").performClick();await("2 seleccionados")
        compose.onNodeWithText("Mover").performClick();await("Mover 2 elementos")
        compose.onNodeWithTag("layer-navigator").performScrollToNode(hasTestTag("navigator-node:${layer.id}"))
        compose.onNodeWithTag("navigator-node:${layer.id}").performClick();await("2 elementos movidos")
        val moved=persisted().filter { it.id in rows.map { r -> r.id } }
        assertTrue(moved.all { it.parentId==layer.id && it.creationGroupId=="sort-group" })
        assertEquals(setOf(1,2),moved.map { it.position }.toSet())
        moved.forEach { assertEquals(listOf(personId),runBlocking { app.database.personDao().assignmentIds(it.id) }) }
        assertEquals(rows.map { it.id }.toSet(),runBlocking { app.database.tagDao().nodeTags() }.filter { it.tagId==tag.id }.map { it.nodeId }.toSet())
        node("Cuentas").performClick();node("Moved early").assertExists()
        order().assert(hasStateDescription("Orden: Vencimiento próximo; arrastre deshabilitado"))
        compose.onNodeWithTag("nodes-list").performScrollToNode(hasText("Moved early"))
        first("Moved early",setOf("Moved early","Moved late","Idea"))
        val tree=NodeTreeSnapshot(persisted())
        assertEquals(listOf("Moved early","Moved late","Idea"),NodePresentationSort.children(tree.childrenOf(layer.id),NodeSortMode.DUE_ASC).map { it.title })
    }
    @Test fun creationAndBatchUndoRefreshAutomaticViewWithoutChangingOldPositions() {
        open();sort(NodeSortMode.DUE_ASC);val original=persisted()
        compose.onNodeWithText("Nuevo elemento").performClick();await("Título")
        compose.onNodeWithText("Título").performScrollTo().performTextReplacement("New")
        compose.onNodeWithText("Crear").performClick();await("Deshacer")
        node("New").assertExists();compose.onNodeWithText("Deshacer").performClick();await("Creación deshecha")
        assertEquals(original,persisted())
        compose.onNodeWithText("Nuevo elemento").performClick();await("Título")
        compose.onNodeWithText("Título").performScrollTo().performTextReplacement("Batch")
        compose.onNodeWithTag("option:Crear varios").performScrollTo().performClick()
        compose.onNodeWithText("Cantidad").performScrollTo().performTextReplacement("3")
        compose.onNodeWithText("Crear").performClick();await("3 elementos creados")
        assertEquals(original.size+3,persisted().size)
        compose.onNodeWithText("Deshacer").performClick();await("Creación deshecha")
        assertEquals(original,persisted())
        order().assert(hasStateDescription("Orden: Vencimiento próximo; arrastre deshabilitado"))
    }
}
