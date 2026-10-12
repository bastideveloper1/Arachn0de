package com.r0ybt.arachn0de.ui

import androidx.activity.ComponentActivity
import androidx.room.withTransaction
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.r0ybt.arachn0de.Arachn0deApplication
import com.r0ybt.arachn0de.ui.theme.Arachn0deTheme
import com.r0ybt.arachn0de.data.repository.*
import com.r0ybt.arachn0de.domain.model.*
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class) @GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application=com.r0ybt.arachn0de.security.LegacyUiTestApplication::class,sdk=[28],qualifiers="w411dp-h891dp")
class IterationUiTest {
    @get:Rule val compose=createAndroidComposeRule<ComponentActivity>()
    private val app get()=compose.activity.application as Arachn0deApplication
    @After fun close() {
        compose.runOnUiThread {val content=compose.activity.findViewById<android.view.ViewGroup>(android.R.id.content);for(i in 0 until content.childCount) (content.getChildAt(i) as? androidx.compose.ui.platform.AbstractComposeView)?.disposeComposition()}
        compose.waitForIdle();runBlocking {app.database.withTransaction {}}
    }
    private fun pump() {org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();androidx.compose.runtime.snapshots.Snapshot.sendApplyNotifications();compose.mainClock.advanceTimeByFrame()}
    private fun await(text:String)=compose.waitUntil(15000) {pump();compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()}
    private fun scroll(text:String)=compose.waitUntil(15000) {pump();runCatching {compose.onNodeWithTag("iteration-list").performScrollToNode(hasText(text))}.isSuccess}
    private fun click(text:String) {await(text);val node=compose.onNode(hasText(text) and hasClickAction());if(runCatching {node.assertIsDisplayed()}.isFailure) node.performScrollTo();node.performClick();if(text=="Confirmar") compose.waitUntil(15000) {pump();compose.onAllNodesWithText(text).fetchSemanticsNodes().isEmpty()}}
    @Test fun managedEntrySelectsExistingSprintAndBacklogTaskWithoutMovingAndOpensOriginal() {
        val fixture=runBlocking {
            val p=app.projectRepository.createProject("Iteration project")
            val ctx=app.nodeRepository.management.configure(p.id,null,true,ManagementMethodology.AGILE)!!.id
            val sprint=app.nodeRepository.createNode(p.id,null,"Existing Sprint",purpose=NodePurpose.LAYER);app.nodeRepository.setSprintMode(sprint.id,true)
            val origin=app.nodeRepository.createNode(p.id,null,"Original location",purpose=NodePurpose.LAYER)
            val task=app.nodeRepository.createNode(p.id,origin.id,"Searchable task",description="Original task text")
            app.nodeRepository.management.backlog.addTask(ctx,task.id);Triple(ctx,sprint,task)
        }
        compose.setContent {Arachn0deTheme {AppRoot(app.projectRepository,app.nodeRepository)}}
        click("Iteration project");compose.waitUntil(15000) {pump();runCatching {compose.onNodeWithTag("nodes-list").performScrollToNode(hasText("Existing Sprint"))}.isSuccess};click("Existing Sprint");click("Planificar")
        scroll("Consultar trabajo y tareas técnicas");click("Consultar trabajo y tareas técnicas");scroll("Incorporar tarea técnica");click("Incorporar tarea técnica")
        compose.onNodeWithText("Buscar tareas del proyecto").performTextReplacement("Searchable")
        scroll("Incorporar: Searchable task");click("Incorporar: Searchable task")
        compose.waitUntil(15000) {pump();runBlocking {app.database.sprintLinkDao().all().size==1}}
        click("Volver a planificación");compose.runOnUiThread {compose.activity.onBackPressedDispatcher.onBackPressed()};scroll("Iniciar iteración");click("Iniciar iteración");click("Confirmar")
        scroll("Consultar trabajo y tareas técnicas");click("Consultar trabajo y tareas técnicas");scroll("Searchable task");click("Searchable task");await("Original task text")
        runBlocking {assertEquals(fixture.third.parentId,app.nodeRepository.getNode(fixture.third.id)!!.parentId);assertTrue(app.database.iterationDao().commitments().single().initial)}
    }
    @Test fun largeFontsScopeRemovalCloseAndReopenPreserveHistoryAndPhaseIndependence() {
        val data=runBlocking {
            val p=app.projectRepository.createProject("Large iteration")
            val id=app.nodeRepository.management.configure(p.id,null,true,ManagementMethodology.HYBRID)!!.id
            val sprint=app.nodeRepository.createNode(p.id,null,"Sprint extenso",purpose=NodePurpose.LAYER);app.nodeRepository.setSprintMode(sprint.id,true)
            val task=app.nodeRepository.createNode(p.id,null,"Task")
            app.database.nodeDao().updateContent(task.id,"Tarea extensa ".repeat(15),"",2)
            app.nodeRepository.management.backlog.addTask(id,task.id)
            val plan=app.nodeRepository.management.iterations.configure(id,sprint.id,"Meta extensa ".repeat(10))
            app.nodeRepository.management.iterations.add(plan.id,setOf(task.id));app.nodeRepository.management.iterations.start(plan.id)
            Triple(id,plan.id,task.id)
        }
        compose.setContent {Arachn0deTheme {val d=LocalDensity.current;CompositionLocalProvider(LocalDensity provides Density(d.density,1.6f)) {IterationDialog(data.first,app.nodeRepository.management.iterations,{}) {}}}}
        scroll("Sprint extenso");click("Sprint extenso");scroll("Consultar trabajo y tareas técnicas");click("Consultar trabajo y tareas técnicas")
        val title=runBlocking {app.database.nodeDao().getById(data.third)!!.title}
        scroll(title);compose.onNodeWithContentDescription("Opciones trabajo: $title").performScrollTo().performClick();click("Retirar vínculo técnico");await("Confirmar");click("Cancelar")
        runBlocking {assertEquals(1,app.database.sprintLinkDao().all().size)}
        compose.onNodeWithContentDescription("Opciones trabajo: $title").performClick();click("Retirar vínculo técnico");click("Confirmar");scroll("Retirada del alcance")
        compose.runOnUiThread {compose.activity.onBackPressedDispatcher.onBackPressed()}
        scroll("Cerrar iteración");click("Cerrar iteración");click("Confirmar")
        runBlocking {assertEquals("CLOSED",app.database.iterationDao().plans().single().state);assertFalse(app.database.iterationDao().closures().single().inScope)}
        scroll("Reabrir iteración");click("Reabrir iteración");click("Confirmar")
        runBlocking {assertEquals(2,app.database.iterationDao().cycles().size);assertEquals(1,app.database.iterationDao().closures().size);assertEquals(1,app.database.iterationDao().commitments().size);assertNull(app.database.nodeDao().getById(data.third)!!.workState);assertFalse(app.database.nodeDao().getById(data.third)!!.isCompleted)}
    }
    @Test fun storyManagerUpdatesImmediatelyAndKeepsOldTechnicalTaskAndCanonicalParent() {
        val fixture=runBlocking {
            val p=app.projectRepository.createProject("Stories Sprint")
            val repo=app.nodeRepository.management
            val context=repo.configure(p.id,null,true,ManagementMethodology.AGILE)!!.id
            val layers=app.database.managementLayerDao().all().associateBy {it.kind}
            val epic=repo.cascade.create(context,layers.getValue("BACKLOG").nodeId,"Origin epic",LayerRole.EPIC)
            val story=repo.cascade.create(context,epic,"Incorporated story",LayerRole.STORY)
            val task=app.nodeRepository.createNode(p.id,story,"Story task")
            app.nodeRepository.setCompleted(task.id,true)
            val sprint=repo.cascade.create(context,layers.getValue("SPRINTS").nodeId,"Planning sprint",sprint=true)
            val direct=app.nodeRepository.createNode(p.id,sprint,"Old direct task")
            listOf(context,sprint,story,epic,task.id,direct.id)
        }
        compose.setContent {Arachn0deTheme {IterationDialog(fixture[0],app.nodeRepository.management.iterations,{},fixture[1]) {}}}
        scroll("Gestionar historias");click("Gestionar historias")
        compose.onNodeWithTag("sprint-story:${fixture[2]}").performClick()
        compose.waitUntil(15000) {pump();runBlocking {app.database.cascadeRelationDao().stories().size==1}}
        await("Incorporada · Toca para retirar")
        runBlocking {
            assertEquals(fixture[3],app.nodeRepository.getNode(fixture[2])!!.parentId)
            assertEquals(fixture[2],app.nodeRepository.getNode(fixture[4])!!.parentId)
            assertTrue(app.nodeRepository.getNode(fixture[4])!!.isCompleted)
            assertEquals(1,app.database.sprintLinkDao().all().count {it.taskId==fixture[4]})
        }
        compose.onNodeWithTag("sprint-story:${fixture[2]}").performClick();click("Cancelar")
        assertEquals(1,runBlocking {app.database.cascadeRelationDao().stories().size})
        compose.onNodeWithTag("sprint-story:${fixture[2]}").performClick();click("Confirmar")
        await("Disponible · Toca para incorporar")
        runBlocking {
            assertTrue(app.database.cascadeRelationDao().stories().isEmpty())
            assertEquals(fixture[1],app.nodeRepository.getNode(fixture[5])!!.parentId)
            assertEquals(WorkState.UNPLANNED,app.nodeRepository.getNode(fixture[5])!!.workState)
            assertTrue(app.nodeRepository.getNode(fixture[4])!!.isCompleted)
            assertNull(app.nodeRepository.getNode(fixture[4])!!.workState)
        }
    }
    @Test fun planningEditorSeparatesCalendarGoalAndNotesAndPreservesValues() {
        val fixture=runBlocking {
            val p=app.projectRepository.createProject("Planning editor")
            val repo=app.nodeRepository.management
            val context=repo.configure(p.id,null,true,ManagementMethodology.AGILE)!!.id
            val sprint=repo.cascade.create(context,app.database.managementLayerDao().all().first {it.kind=="SPRINTS"}.nodeId,"Calendar sprint",sprint=true)
            context to sprint
        }
        compose.setContent {Arachn0deTheme {IterationDialog(fixture.first,app.nodeRepository.management.iterations,{},fixture.second) {}}}
        scroll("Editar planificación");click("Editar planificación")
        compose.onNodeWithText("Objetivo del Sprint").performTextReplacement("Objetivo independiente")
        compose.onNodeWithText("Observaciones").performTextReplacement("Nota independiente")
        click("Inicio previsto: Sin definir");await("Seleccionar fecha");compose.onAllNodesWithText("Cancelar").onLast().performClick()
        click("Guardar planificación")
        compose.waitUntil(15000) {pump();runBlocking {app.database.iterationDao().plans().single().goal=="Objetivo independiente"}}
        runBlocking {val p=app.database.iterationDao().plans().single();assertEquals("Nota independiente",p.notes);assertNull(p.startAt);assertNull(p.endAt);assertEquals("PREPARED",p.state)}
    }

}
