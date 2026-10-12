package com.r0ybt.arachn0de.ui

import androidx.activity.ComponentActivity
import androidx.room.withTransaction
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.r0ybt.arachn0de.Arachn0deApplication
import com.r0ybt.arachn0de.ui.theme.Arachn0deTheme
import com.r0ybt.arachn0de.data.repository.*
import com.r0ybt.arachn0de.domain.model.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class) @GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application=com.r0ybt.arachn0de.security.LegacyUiTestApplication::class,sdk=[28],qualifiers="w411dp-h891dp")
class KanbanUiTest {
    @get:Rule val compose=createAndroidComposeRule<ComponentActivity>()
    private val app get()=compose.activity.application as Arachn0deApplication
    @After fun close() {
        compose.runOnUiThread {val content=compose.activity.findViewById<android.view.ViewGroup>(android.R.id.content);for(i in 0 until content.childCount) (content.getChildAt(i) as? androidx.compose.ui.platform.AbstractComposeView)?.disposeComposition()}
        compose.waitForIdle();runBlocking {app.database.withTransaction {}}
    }
    private fun pump() {org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();androidx.compose.runtime.snapshots.Snapshot.sendApplyNotifications();compose.mainClock.advanceTimeByFrame()}
    private fun await(text:String)=compose.waitUntil(15000) {pump();compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()}
    private fun scroll(text:String)=compose.waitUntil(15000) {pump();runCatching {compose.onNodeWithTag("kanban-list").performScrollToNode(hasText(text))}.isSuccess}
    private fun click(text:String) {await(text);val node=if(compose.onAllNodes(hasText(text) and hasAnyAncestor(isDialog())).fetchSemanticsNodes().isNotEmpty()) compose.onNode(hasText(text) and hasAnyAncestor(isDialog())) else compose.onNodeWithText(text);if(runCatching {node.assertIsDisplayed()}.isFailure) node.performScrollTo();node.performClick();if(text=="Confirmar") compose.waitUntil(15000) {pump();compose.onAllNodesWithText(text).fetchSemanticsNodes().isEmpty()}}
    private fun selectScope(label:String) {
        compose.onNodeWithTag("kanban-list").performScrollToNode(hasTestTag("kanban-filter:Ámbito"))
        compose.onNodeWithTag("kanban-filter:Ámbito").performClick()
        compose.onNode(hasText(label) and hasAnyAncestor(isPopup())).performClick()
    }
    @Test fun managedEntryFiltersSameTaskChangesRealPhaseAndOpensOriginal() {
        val data=runBlocking {
            val p=app.projectRepository.createProject("Managed Kanban")
            val id=app.nodeRepository.management.configure(p.id,null,true,ManagementMethodology.AGILE)!!.id
            val sprint=app.nodeRepository.createNode(p.id,null,"Existing Sprint",purpose=NodePurpose.LAYER);app.nodeRepository.setSprintMode(sprint.id,true)
            val origin=app.nodeRepository.createNode(p.id,null,"Source layer",purpose=NodePurpose.LAYER)
            val task=app.nodeRepository.createNode(p.id,origin.id,"Original task",description="Original details")
            app.nodeRepository.management.backlog.addTask(id,task.id)
            val plan=app.nodeRepository.management.iterations.configure(id,sprint.id)
            val story=app.nodeRepository.createNode(p.id,null,"HU — Context story",purpose=NodePurpose.LAYER)
            app.nodeRepository.management.backlog.setRole(id,story.id,LayerRole.STORY)
            app.nodeRepository.management.relations.addStory(sprint.id,story.id)
            app.nodeRepository.management.iterations.add(plan.id,setOf(task.id));app.nodeRepository.management.iterations.start(plan.id)
            Triple(id,task,plan)
        }
        val initial=runBlocking {app.database.iterationDao().commitments()}
        compose.setContent {Arachn0deTheme {AppRoot(app.projectRepository,app.nodeRepository)}}
        click("Managed Kanban");compose.waitUntil(15000) {pump();runCatching {compose.onNodeWithTag("nodes-list").performScrollToNode(hasText("Kanban"))}.isSuccess};click("Kanban")
        compose.onNodeWithText("Tablero").assertDoesNotExist();compose.onAllNodes(isDialog()).assertCountEquals(0)
        scroll("Original task");compose.onAllNodesWithText("Original task").assertCountEquals(1)
        compose.onNodeWithText("HU — Context story").assertDoesNotExist()
        selectScope("Backlog");compose.onNodeWithTag("kanban-list").performScrollToNode(hasContentDescription("Opciones Kanban: Original task"));compose.onNodeWithContentDescription("Opciones Kanban: Original task").performClick();compose.onNodeWithContentDescription("Cambiar fase: Original task").performClick()
        compose.onNode(hasText("No planificada") and hasAnyAncestor(isDialog())).assertIsSelected()
        compose.onNode(hasText("Haciendo") and hasAnyAncestor(isDialog())).performClick()
        compose.waitUntil(15000) {pump();runBlocking {app.nodeRepository.getNode(data.second.id)!!.workState==WorkState.DOING}}
        scroll("▼ Haciendo · 1");compose.onNodeWithText("▼ Haciendo · 1").assertExists()
        selectScope("Iteración / Sprint");scroll("Sprint seleccionado: Existing Sprint");click("Sprint seleccionado: Existing Sprint");click("Existing Sprint")
        compose.onNodeWithText("Compromiso inicial").assertDoesNotExist()
        scroll("Original task");click("Original task");await("Original details")
        compose.runOnUiThread {compose.activity.onBackPressedDispatcher.onBackPressed()}
        await("Sprint seleccionado: Existing Sprint")
        compose.onNodeWithText("Tablero").assertDoesNotExist()
        runBlocking {
            val task=app.nodeRepository.getNode(data.second.id)!!
            assertEquals(data.second.parentId,task.parentId);assertFalse(task.isCompleted)
            assertEquals(initial,app.database.iterationDao().commitments());assertEquals(1,app.database.sprintLinkDao().all().size)
        }
    }
    @Test fun largeFontsClosedIterationShowsLiveWorkAndHistoryWithoutScopeControls() {
        val title="Título extenso ".repeat(6).trim()
        val data=runBlocking {
            val p=app.projectRepository.createProject("Closed")
            val id=app.nodeRepository.management.configure(p.id,null,true,ManagementMethodology.HYBRID)!!.id
            val sprint=app.nodeRepository.createNode(p.id,null,"Sprint",purpose=NodePurpose.LAYER);app.nodeRepository.setSprintMode(sprint.id,true)
            val task=app.nodeRepository.createNode(p.id,null,title)
            app.nodeRepository.management.backlog.addTask(id,task.id)
            val plan=app.nodeRepository.management.iterations.configure(id,sprint.id)
            app.nodeRepository.management.iterations.add(plan.id,setOf(task.id));app.nodeRepository.management.iterations.start(plan.id)
            app.nodeRepository.management.iterations.close(plan.id,listOf(app.database.nodeDao().getById(task.id)!!))
            Triple(id,task.id,app.database.iterationDao().closures())
        }
        var history=false
        compose.setContent {Arachn0deTheme {val d=LocalDensity.current;CompositionLocalProvider(LocalDensity provides Density(d.density,1.6f)) {ManagedKanbanDialog(data.first,app.nodeRepository.management.kanban,{}, {history=true}) {}}}}
        val filterBounds=listOf("Ámbito","Responsable","Fase").map {compose.onNodeWithTag("kanban-filter:$it").fetchSemanticsNode().boundsInRoot}
        assertEquals(filterBounds[0].top,filterBounds[1].top,1f);assertEquals(filterBounds[1].top,filterBounds[2].top,1f)
        assertTrue(filterBounds.zipWithNext().all {(a,b)->a.right<=b.left})
        selectScope("Iteración / Sprint");scroll("Sprint seleccionado: Sprint");click("Sprint seleccionado: Sprint");click("Sprint")
        scroll("Trabajo actual. El resultado del cierre se conserva en la planificación.")
        scroll(title);compose.onNodeWithText(title).assertIsDisplayed()
        compose.onNodeWithTag("kanban-list").performScrollToNode(hasContentDescription("Opciones Kanban: $title"));compose.onNodeWithContentDescription("Opciones Kanban: $title").performClick();compose.onNodeWithContentDescription("Cambiar fase: $title").performClick()
        compose.onNode(hasText("Validada") and hasAnyAncestor(isDialog()) and !hasTestTag("kanban-phase:VALIDATED")).performScrollTo().performClick()
        compose.waitUntil(15000) {pump();runBlocking {app.nodeRepository.getNode(data.second)!!.workState==WorkState.VALIDATED}}
        runBlocking {assertFalse(app.nodeRepository.getNode(data.second)!!.isCompleted);assertEquals(data.third,app.database.iterationDao().closures())}
        compose.onAllNodesWithText("Retirar",substring=true).assertCountEquals(0)
        scroll("Planificación e historial");click("Planificación e historial");assertTrue(history)
    }
    @Test fun compactDropdownsFilterTasksAndKeepCardAndFavoriteActionsIndependent() {
        val fixture=runBlocking {
            val p=app.projectRepository.createProject("Filters")
            val id=app.nodeRepository.management.configure(p.id,null,true,ManagementMethodology.AGILE)!!.id
            val task=app.nodeRepository.createNode(p.id,null,"Unassigned task")
            app.nodeRepository.management.backlog.addTask(id,task.id)
            id to task
        }
        var opened:String?=null
        val access=com.r0ybt.arachn0de.access.AccessRepository(app.database)
        compose.setContent {Arachn0deTheme {CompositionLocalProvider(LocalElementAccess provides access,LocalElementAccessState provides access.observe().collectAsState(initial=com.r0ybt.arachn0de.access.AccessState()).value) {ManagedKanbanDialog(fixture.first,app.nodeRepository.management.kanban,{opened=it},{},description="Board description") {}}}}
        selectScope("Proyecto")
        compose.onNodeWithTag("kanban-filter:Responsable").performClick();click("Sin responsable")
        compose.onNodeWithTag("kanban-filter:Fase").performClick();click("Sin fase Sprint")
        scroll("Unassigned task")
        compose.onNodeWithText("Origen:",substring=true).assertDoesNotExist()
        compose.onNodeWithTag("kanban-task:${fixture.second.id}").performClick();assertEquals(fixture.second.id,opened)
        opened=null
        compose.onNodeWithTag("favorite:node:${fixture.second.id}").performClick()
        compose.waitUntil(15000) {pump();runBlocking {"node:${fixture.second.id}" in access.snapshot().favorites}}
        assertNull(opened)
        compose.onNodeWithContentDescription("Completar: Unassigned task").performClick()
        compose.waitUntil(15000) {pump();runBlocking {app.nodeRepository.getNode(fixture.second.id)!!.isCompleted}}
        assertNull(opened)
        compose.onNodeWithTag("kanban-list").performScrollToNode(hasTestTag("kanban-filter:Fase"))
        compose.onNodeWithTag("kanban-filter:Fase").performClick();click("Haciendo")
        compose.onNodeWithText("Unassigned task").assertDoesNotExist()
        compose.onNodeWithTag("kanban-filter:Fase").performClick();click("Todas")
        scroll("Unassigned task");compose.onNodeWithText("Unassigned task").assertIsDisplayed()
    }
    @Test fun emptyAndSearchViewsHaveClearMessagesWithoutInventingTasks() {
        val id=runBlocking {val p=app.projectRepository.createProject("Empty");app.nodeRepository.management.configure(p.id,null,true,ManagementMethodology.AGILE)!!.id}
        compose.setContent {Arachn0deTheme {ManagedKanbanDialog(id,app.nodeRepository.management.kanban,{}, {}) {}}}
        scroll("No hay tareas en esta vista.");compose.onNodeWithText("No hay tareas en esta vista.").assertIsDisplayed()
        selectScope("Iteración / Sprint");scroll("No hay Capas Sprint en este contexto.")
        selectScope("Proyecto");scroll("Buscar tareas u origen")
        compose.onNodeWithText("Buscar tareas u origen").performTextReplacement("Nothing")
        scroll("Sin coincidencias");runBlocking {assertTrue(app.database.backupDao().nodes().none {it.purpose=="ACTION"})}
    }
    @Test fun directLegacyTaskUsesOfficialCheckForEveryPhaseAndSharedProgress() {
        val fixture=runBlocking {
            val p=app.projectRepository.createProject("Legacy check")
            val ctx=app.nodeRepository.management.configure(p.id,null,true,ManagementMethodology.AGILE)!!.id
            val sprint=app.nodeRepository.createNode(p.id,null,"Legacy Sprint",purpose=NodePurpose.LAYER)
            app.nodeRepository.setSprintMode(sprint.id,true)
            val task=app.nodeRepository.createNode(p.id,sprint.id,"Legacy task")
            val plan=app.nodeRepository.management.iterations.configure(ctx,sprint.id)
            app.nodeRepository.management.iterations.start(plan.id)
            Triple(ctx,sprint.id,task.id)
        }
        compose.setContent {Arachn0deTheme {ManagedKanbanDialog(fixture.first,app.nodeRepository.management.kanban,{}, {}) {}}}
        for(phase in WorkState.entries.dropLast(1)) {
            val description="Confirmar ${phase.label} y avanzar a ${phase.next().label}: Legacy task"
            compose.waitUntil(15000) {pump();runCatching {compose.onNodeWithTag("kanban-list").performScrollToNode(hasContentDescription(description))}.isSuccess}
            compose.onNodeWithContentDescription(description).performClick()
            compose.waitUntil(15000) {pump();runBlocking {app.nodeRepository.getNode(fixture.third)!!.workState==phase.next()}}
            runBlocking {
                val current=app.nodeRepository.getNode(fixture.third)!!
                assertEquals(fixture.second,current.parentId)
                assertEquals(phase.next() in listOf(WorkState.DONE,WorkState.VALIDATED),current.isCompleted)
                val state=app.nodeRepository.management.iterations.observe(fixture.first).first()
                assertEquals(state.progress(fixture.second),com.r0ybt.arachn0de.ui.state.sprintProgressPercentage(app.nodeRepository.getProjectNodes(current.projectId),fixture.second))
            }
        }
        compose.waitUntil(15000) {pump();compose.onAllNodesWithContentDescription("Sprint validado: Legacy task").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithContentDescription("Sprint validado: Legacy task").assertIsNotEnabled()
        runBlocking {assertTrue(app.database.sprintLinkDao().all().isEmpty());assertEquals(1,app.database.iterationDao().commitments().size)}
    }

}
