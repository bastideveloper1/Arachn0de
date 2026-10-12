package com.r0ybt.arachn0de.data

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.r0ybt.arachn0de.backup.*
import com.r0ybt.arachn0de.data.local.*
import com.r0ybt.arachn0de.data.repository.*
import com.r0ybt.arachn0de.domain.model.*
import com.r0ybt.arachn0de.ui.sprintSectionNodes
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class) @Config(sdk=[28],application=Application::class)
class KanbanTest {
    private val app get()=ApplicationProvider.getApplicationContext<Application>()
    private lateinit var db:Arachn0deDatabase;private lateinit var nodes:NodeRepository
    private lateinit var context:String;private lateinit var sprint:String;private lateinit var task:String;private lateinit var normal:String
    private fun open() {db=Arachn0deDatabase.create(app);nodes=NodeRepository(db)}
    @Before fun setup()=runBlocking {
        app.deleteDatabase("arachn0de.db");open()
        listOf("p","q").forEach {db.projectDao().insert(ProjectEntity(it,it,"",0,1,1))}
        context=nodes.management.configure("p",null,true,ManagementMethodology.AGILE)!!.id
        sprint=nodes.createNode("p",null,"Sprint",purpose=NodePurpose.LAYER).id;nodes.setSprintMode(sprint,true)
        task=nodes.createNode("p",null,"External original").id;normal=nodes.createNode("p",null,"No membership").id
        nodes.management.backlog.addTask(context,task);nodes.sprintLinks.link(sprint,setOf(task))
    }
    @After fun close() {db.close()}
    private suspend fun state()=nodes.management.kanban.observe(context).first()
    @Test fun projectionsDeduplicateExternalTasksAndKeepUnassignedSeparate()=runBlocking {
        val state=state();val before=db.backupDao().nodes();val links=db.sprintLinkDao().all()
        assertEquals(setOf(task,normal),kanbanTasks(state,context,KanbanScope.PROJECT).map {it.id}.toSet())
        assertEquals(listOf(task),kanbanTasks(state,context,KanbanScope.BACKLOG).map {it.id})
        assertEquals(listOf(task),kanbanTasks(state,context,KanbanScope.SPRINT,sprint).map {it.id})
        assertEquals(listOf(task),kanbanTasks(state.copy(links=links+links),context,KanbanScope.SPRINT,sprint).map {it.id})
        val cards=kanbanTasks(state,context,KanbanScope.PROJECT).map {it.toNode(false)}
        assertEquals(listOf(normal),sprintSectionNodes(cards+cards,"unassigned").map {it.id})
        assertEquals(listOf(task),sprintSectionNodes(cards+cards,WorkState.UNPLANNED.name).map {it.id})
        assertEquals(before,db.backupDao().nodes());assertEquals(links,db.sprintLinkDao().all())
    }
    @Test fun allDirectPhasesPersistIndependentlyOfCheckAndBackupRestoresThem()=runBlocking {
        val repo=nodes.management.kanban;val original=db.nodeDao().getById(task)!!;val links=db.sprintLinkDao().all()
        for(phase in WorkState.entries.reversed()) {
            repo.phase(context,task,phase)
            val row=db.nodeDao().getById(task)!!
            assertEquals(original.copy(workState=phase.name,updatedAt=row.updatedAt),row)
            assertFalse(row.isCompleted);assertEquals(links,db.sprintLinkDao().all())
        }
        repo.phase(context,task,WorkState.VALIDATED)
        val snapshot=BackupRepository(db,app,BackupAvatarFiles(app,BackupFixture::syncDirectory)).snapshot()
        val encoded=BackupJson.encode(snapshot);val decoded=BackupJson.decode(encoded);assertEquals(snapshot,decoded)
        db.close();open();assertEquals(WorkState.VALIDATED,nodes.getNode(task)!!.workState);assertFalse(nodes.getNode(task)!!.isCompleted)
        nodes.management.kanban.phase(context,task,WorkState.DOING)
        BackupRepository(db,app,BackupAvatarFiles(app,BackupFixture::syncDirectory)).restore(decoded)
        assertEquals(WorkState.VALIDATED,nodes.getNode(task)!!.workState);assertEquals(links,db.sprintLinkDao().all())
    }
    @Test fun existingConfirmationGestureRetainsOfficialCompletionBoundary()=runBlocking {
        nodes.management.kanban.phase(context,task,WorkState.DOING)
        nodes.management.kanban.confirm(context,task)
        assertEquals(WorkState.DONE,nodes.getNode(task)!!.workState);assertTrue(nodes.getNode(task)!!.isCompleted)
        nodes.management.kanban.phase(context,task,WorkState.UNPLANNED)
        assertTrue(nodes.getNode(task)!!.isCompleted)
        nodes.management.kanban.confirm(context,normal);assertTrue(nodes.getNode(normal)!!.isCompleted);assertNull(nodes.getNode(normal)!!.workState)
    }
    @Test fun closedIterationHistoryAndInitialCommitmentAreUnaffectedByPhases()=runBlocking {
        val iterations=nodes.management.iterations;val plan=iterations.configure(context,sprint);iterations.start(plan.id)
        val initial=iterations.records().commitments
        nodes.management.kanban.phase(context,task,WorkState.DOING);assertEquals(initial,iterations.records().commitments)
        iterations.close(plan.id,state().current(sprint));val records=iterations.records()
        nodes.management.kanban.phase(context,task,WorkState.VALIDATED)
        assertEquals(records,iterations.records());assertFalse(nodes.getNode(task)!!.isCompleted)
        assertTrue(runCatching {iterations.add(plan.id,setOf(normal))}.isFailure)
        iterations.start(plan.id,true);assertEquals(records.closures,iterations.records().closures)
    }
    @Test fun convertedContextProjectsOnlyItsOwnTasksAndRejectsStaleCrossContextWrites()=runBlocking {
        val q=nodes.management.configure("q",null,true,ManagementMethodology.HYBRID)!!.id
        val foreign=nodes.createNode("q",null,"Foreign").id
        val layer=ProjectNestingRepository(db).move("p","q",null)
        val state=state();assertEquals(setOf(task,normal),kanbanTasks(state,context,KanbanScope.PROJECT).map {it.id}.toSet())
        assertEquals(listOf(foreign),kanbanTasks(state,q,KanbanScope.PROJECT).map {it.id})
        val before=db.backupDao().nodes();assertTrue(runCatching {nodes.management.kanban.phase(q,task,WorkState.DOING)}.isFailure);assertEquals(before,db.backupDao().nodes())
        ProjectNestingRepository(db).promote(layer);assertEquals(listOf(task),kanbanTasks(state(),context,KanbanScope.SPRINT,sprint).map {it.id})
    }
    @Test fun traditionalCrossContextLinkRemainsValidWithoutLeakingIntoManagedProjection()=runBlocking {
        val other=nodes.management.configure("q",null,true,ManagementMethodology.AGILE)!!.id
        val foreign=nodes.createNode("q",null,"Foreign linked").id
        ProjectNestingRepository(db).move("q","p",null)
        nodes.sprintLinks.link(sprint,setOf(foreign))
        nodes.management.kanban.phase(other,foreign,WorkState.VALIDATED)
        val state=state()
        assertEquals(setOf(task,foreign),state.current(sprint).map {it.id}.toSet())
        assertEquals(listOf(task),kanbanTasks(state,context,KanbanScope.SPRINT,sprint).map {it.id})
        assertEquals(listOf(foreign),kanbanTasks(state,other,KanbanScope.PROJECT).map {it.id})
        assertEquals(50.0,state.progress(sprint)!!,0.0)
        assertEquals(2,db.sprintLinkDao().all().size)
    }
    @Test fun disabledContextAndInvalidMembershipDoNotInventPhasesAndEmptySearchIsClear()=runBlocking {
        val before=db.backupDao().nodes();assertTrue(runCatching {nodes.management.kanban.phase(context,normal,WorkState.DOING)}.isFailure);assertEquals(before,db.backupDao().nodes())
        assertTrue(kanbanTasks(state(),context,KanbanScope.PROJECT,query="unmatched").isEmpty())
        assertTrue(kanbanTasks(state(),context,KanbanScope.SPRINT,"missing").isEmpty())
        nodes.management.configure("p",null,false,ManagementMethodology.AGILE)
        assertTrue(kanbanTasks(state(),context,KanbanScope.PROJECT).isEmpty())
        assertTrue(runCatching {nodes.management.kanban.phase(context,task,WorkState.DOING)}.isFailure)
    }
}
