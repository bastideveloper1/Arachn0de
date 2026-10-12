package com.r0ybt.arachn0de.data

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.r0ybt.arachn0de.backup.*
import com.r0ybt.arachn0de.data.local.*
import com.r0ybt.arachn0de.data.repository.*
import com.r0ybt.arachn0de.domain.model.*
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.json.JSONObject

@RunWith(RobolectricTestRunner::class) @Config(sdk=[28],application=Application::class)
class IterationTest {
    private val context get()=ApplicationProvider.getApplicationContext<Application>()
    private lateinit var db:Arachn0deDatabase
    private lateinit var nodes:NodeRepository
    private lateinit var repo:IterationRepository
    private lateinit var contextId:String
    private lateinit var sprint:String
    private lateinit var task:String
    private lateinit var extra:String
    private lateinit var plan:String
    private fun open() {db=Arachn0deDatabase.create(context);nodes=NodeRepository(db);repo=nodes.management.iterations}
    @Before fun setup()=runBlocking {
        context.deleteDatabase("arachn0de.db");open()
        listOf("p","q","r").forEach {db.projectDao().insert(ProjectEntity(it,it,"Description",0,1,2))}
        contextId=nodes.management.configure("p",null,true,ManagementMethodology.AGILE)!!.id
        sprint=nodes.createNode("p",null,"Sprint",purpose=NodePurpose.LAYER).id;nodes.setSprintMode(sprint,true)
        val origin=nodes.createNode("p",null,"Origin",purpose=NodePurpose.LAYER)
        task=nodes.createNode("p",origin.id,"Task",description="Original").id
        extra=nodes.createNode("p",origin.id,"Extra").id
        nodes.management.backlog.addTask(contextId,task);nodes.management.backlog.addTask(contextId,extra)
        plan=repo.configure(contextId,sprint).id
    }
    @After fun close() {db.close()}
    private fun backup()=BackupRepository(db,context,BackupAvatarFiles(context,BackupFixture::syncDirectory))
    private suspend fun snapshot()=backup().snapshot().copy(createdAt=0)
    private suspend fun current()=members(db.backupDao().nodes(),db.sprintLinkDao().all(),sprint)
    private suspend fun activated() {repo.add(plan,setOf(task));repo.start(plan)}
    @Test fun optionalPlanningUsesOriginalSprintTitleAndKeepsTreeBacklogAndDeadlines()=runBlocking {
        val tree=db.backupDao().nodes();val backlog=nodes.management.backlog.records()
        val updated=repo.configure(contextId,sprint,"Meta",100,200,"Notes")
        assertEquals(plan,updated.id);assertEquals("PREPARED",updated.state);assertEquals(tree,db.backupDao().nodes());assertEquals(backlog,nodes.management.backlog.records())
        assertTrue(runCatching {repo.configure(contextId,sprint,startAt=300,endAt=200)}.isFailure)
        assertEquals(updated,repo.records().plans.single())
    }
    @Test fun selectionIsDeduplicatedWithoutMovingOrReorderingAndConflictsAreAtomic()=runBlocking {
        val before=db.nodeDao().getById(task)!!;val backlog=nodes.management.backlog.records()
        repo.add(plan,setOf(task));repo.add(plan,setOf(task));assertEquals(1,db.sprintLinkDao().all().size)
        val after=db.nodeDao().getById(task)!!;assertEquals(before.copy(workState=after.workState,updatedAt=after.updatedAt),after);assertEquals(backlog,nodes.management.backlog.records())
        val other=nodes.createNode("p",null,"Other Sprint",purpose=NodePurpose.LAYER);nodes.setSprintMode(other.id,true);nodes.sprintLinks.link(other.id,setOf(extra))
        val saved=snapshot();assertTrue(runCatching {repo.add(plan,setOf(extra))}.isFailure);assertEquals(saved,snapshot())
        val additional=nodes.createNode("p",null,"Contextual task").id;repo.add(plan,setOf(additional));assertTrue(db.sprintLinkDao().all().any {it.taskId==additional})
        val unavailable=nodes.createNode("q",null,"Other context").id;assertTrue(runCatching {repo.add(plan,setOf(unavailable))}.isFailure)
    }
    @Test fun startRecordsInitialCommitmentAndLaterScopeSurvivesRemovalAndReaddition()=runBlocking {
        activated();val cycle=repo.records().cycles.single();val initial=repo.records().commitments.single();assertTrue(initial.initial)
        repo.add(plan,setOf(extra));repo.remove(plan,extra,WorkState.UNPLANNED)
        val refs=repo.records().commitments;assertEquals(2,refs.size);assertEquals(initial,refs.first {it.taskId==task});assertFalse(refs.first {it.taskId==extra}.initial)
        repo.remove(plan,task,WorkState.UNPLANNED);assertTrue(current().isEmpty());assertEquals(refs,repo.records().commitments)
        repo.add(plan,setOf(task));assertEquals(refs,repo.records().commitments);assertEquals(cycle,repo.records().cycles.single())
    }
    @Test fun progressUsesExistingMotorAndAcceptanceCompletionAndResponsibilitiesAreIndependent()=runBlocking {
        val goal=nodes.management.planning.createObjective(contextId,"Goal");val criterion=nodes.management.planning.createCriterion(goal.id,"Explicit")
        db.personDao().insert(PersonEntity("person","Original",null));nodes.management.participation.add(contextId,"person");nodes.management.participation.assign(contextId,"person",task,false,ResponsibilityRole.VALIDATOR)
        val responsibilities=nodes.management.participation.records();activated();nodes.setWorkState(task,WorkState.VALIDATED)
        val data=backup().snapshot();val state=IterationState(repo.records(),BacklogState(data.nodes,data.conversionRoots,data.managementContexts,records=nodes.management.backlog.records()),links=data.sprintLinks)
        assertEquals(100.0,state.progress(sprint)!!,0.0);assertFalse(nodes.getNode(task)!!.isCompleted);assertFalse(db.planningDao().criterion(criterion.id)!!.validated)
        repo.close(plan,current());assertEquals(responsibilities,nodes.management.participation.records());assertFalse(db.planningDao().criterion(criterion.id)!!.validated)
    }
    @Test fun closeWithPendingWorkFreezesResultAndReopenCreatesIndependentCycle()=runBlocking {
        activated();repo.add(plan,setOf(extra));nodes.setWorkState(task,WorkState.DOING)
        repo.close(plan,current());val before=repo.records();val links=db.sprintLinkDao().all();val tree=db.backupDao().nodes()
        assertEquals("CLOSED",before.plans.single().state);assertEquals("DOING",before.closures.first {it.taskId==task}.phaseAtClose)
        assertTrue(runCatching {repo.start(plan)}.isFailure);assertEquals(before,repo.records());assertEquals(links,db.sprintLinkDao().all());assertEquals(tree,db.backupDao().nodes())
        nodes.setWorkState(task,WorkState.VALIDATED);assertEquals(before.closures,repo.records().closures)
        repo.start(plan,true);val reopened=repo.records();assertEquals(2,reopened.cycles.size);assertEquals(before.cycles.single(),reopened.cycles.first());assertEquals(before.closures,reopened.closures)
        val initial=reopened.commitments.filter {it.cycleId==reopened.cycles.last().id};assertEquals(2,initial.size);assertTrue(initial.all {it.initial})
    }
    @Test fun closeRejectsStaleSummaryAndEmptySprintNeedsNoDates()=runBlocking {
        activated();val before=current();nodes.setWorkState(task,WorkState.PLANNED)
        assertTrue(runCatching {repo.close(plan,before)}.isFailure);assertEquals("ACTIVE",repo.records().plans.single().state);assertTrue(repo.records().closures.isEmpty())
        val empty=nodes.createNode("p",null,"Empty",purpose=NodePurpose.LAYER);nodes.setSprintMode(empty.id,true)
        val id=repo.configure(contextId,empty.id).id;repo.start(id);repo.close(id,emptyList());assertEquals("CLOSED",repo.records().plans.first {it.id==id}.state)
    }
    @Test fun traditionalDirectTaskChangesAreRecordedButDoNotMoveOrResetOtherTasks()=runBlocking {
        activated();val direct=nodes.createNode("p",sprint,"Direct").id
        assertFalse(repo.records().commitments.first {it.taskId==direct}.initial)
        assertTrue(runCatching {repo.remove(plan,direct,WorkState.UNPLANNED)}.isFailure);assertEquals(sprint,nodes.getNode(direct)!!.parentId)
        val old=nodes.createNode("p",null,"Moved existing").id;nodes.moveNode(old,sprint)
        assertFalse(repo.records().commitments.first {it.taskId==old}.initial);nodes.moveNode(old,null);assertTrue(repo.records().commitments.any {it.taskId==old})
    }
    @Test fun disabledContextAndRestartPreserveEveryCycleAndReference()=runBlocking {
        activated();repo.close(plan,current());val before=repo.records();nodes.management.configure("p",null,false,ManagementMethodology.HYBRID)
        assertTrue(runCatching {repo.start(plan,true)}.isFailure);db.close();open();assertEquals(before,repo.records())
        nodes.management.configure("p",null,true,ManagementMethodology.TRADITIONAL);assertEquals(before,repo.records())
    }
    @Test fun conversionsKeepActiveAndClosedHistoryAndPreservePhasesWithRollback()=runBlocking {
        activated();nodes.setWorkState(task,WorkState.VALIDATED);repo.close(plan,current());repo.start(plan,true)
        val before=repo.records();val links=db.sprintLinkDao().all();val layer=ProjectNestingRepository(db).move("p","q",null)
        assertEquals(before,repo.records());assertEquals(links,db.sprintLinkDao().all());assertEquals(WorkState.VALIDATED,nodes.getNode(task)!!.workState)
        val saved=snapshot();assertTrue(runCatching {ProjectNestingRepository(db) {error("Rollback")}.promote(layer)}.isFailure);assertEquals(saved,snapshot())
        ProjectNestingRepository(db).promote(layer);assertEquals(before,repo.records());assertEquals(links,db.sprintLinkDao().all())
    }
    @Test fun historyPreventsOutOfContextMovesDeletionPurposeChangeAndSprintDisable()=runBlocking {
        activated();repo.remove(plan,task,WorkState.UNPLANNED);nodes.management.backlog.removeEntry(contextId,task)
        val layer=ProjectNestingRepository(db).move("p","q",null);val outside=nodes.createNode("q",null,"Outside",purpose=NodePurpose.LAYER).id
        val saved=snapshot()
        for(action in listOf<suspend ()->Unit>({nodes.moveNode(task,outside)},{nodes.deleteNode(task)},{nodes.convertPurpose(task,NodePurpose.NOTE)},{nodes.setSprintMode(sprint,false)},{ProjectNestingRepository(db).promote(sprint)})) {assertTrue(runCatching {action()}.isFailure);assertEquals(saved,snapshot())}
        assertNotNull(db.conversionDao().node(layer));repo.validate()
    }
    @Test fun nestedContextCannotSupplyForeignScopeAndBoundaryCreationRollsBack()=runBlocking {
        val inner=ProjectNestingRepository(db).move("r","p",null)
        val inside=nodes.createNode("p",inner,"Inside").id;nodes.management.backlog.addTask(contextId,inside)
        repo.add(plan,setOf(inside));repo.start(plan)
        val saved=snapshot();assertTrue(runCatching {nodes.management.configure(null,inner,true,ManagementMethodology.AGILE)}.isFailure);assertEquals(saved,snapshot())
        val foreign=nodes.createNode("q",null,"Foreign").id;val before=snapshot();assertTrue(runCatching {nodes.sprintLinks.link(sprint,setOf(foreign))}.isFailure);assertEquals(before,snapshot())
    }
    @Test fun json25RoundTripRestoresAllCyclesScopeAndClosedResults()=runBlocking {
        activated();repo.add(plan,setOf(extra));repo.remove(plan,extra,WorkState.UNPLANNED);repo.close(plan,current());repo.start(plan,true)
        val before=snapshot();val bytes=BackupJson.encode(before);assertEquals(29,JSONObject(String(bytes)).getInt("dataVersion"));assertEquals(before,BackupJson.decode(bytes))
        repo.add(plan,setOf(extra));backup().restore(BackupJson.decode(bytes));assertEquals(before,snapshot())
    }
    @Test fun historical24RestoresWithoutInventingPlanningOrCommitments()=runBlocking {
        db.personDao().insert(PersonEntity("person","Person",null));nodes.management.participation.add(contextId,"person")
        val old=snapshot().copy(managementLayers=emptyList(),iterationPlans=emptyList(),iterationCycles=emptyList(),iterationCommitments=emptyList(),iterationClosures=emptyList())
        val bytes=BackupJson.encode(old);assertEquals(24,JSONObject(String(bytes)).getInt("dataVersion"));val decoded=BackupJson.decode(bytes);assertEquals(IterationRecords(),IterationRecords(decoded.iterationPlans,decoded.iterationCycles,decoded.iterationCommitments,decoded.iterationClosures))
        activated();backup().restore(decoded);assertEquals(IterationRecords(),repo.records())
    }
    @Test fun invalidBackupsAndUnknownFieldsFailBeforeAnyReplacement()=runBlocking {
        activated();repo.close(plan,current());val before=snapshot();val cycle=before.iterationCycles.single();val ref=before.iterationCommitments.single()
        val bad=listOf(before.copy(iterationPlans=before.iterationPlans+before.iterationPlans),before.copy(iterationCommitments=listOf(ref.copy(taskId="missing"))),before.copy(iterationCycles=listOf(cycle.copy(closedAt=cycle.startedAt-1))),before.copy(iterationClosures=emptyList()),before.copy(iterationCommitments=listOf(ref,ref)))
        bad.forEach {assertTrue(runCatching {backup().restore(it)}.isFailure);assertEquals(before,snapshot())}
        val json=JSONObject(String(BackupJson.encode(before)));json.getJSONArray("iterationPlans").getJSONObject(0).put("extra",true);assertTrue(runCatching {BackupJson.decode(json.toString().toByteArray())}.isFailure)
    }
    @Test fun nestedContextsRejectLinksAcrossScopeEvenInsideTheSamePhysicalProject()=runBlocking {
        activated();nodes.management.configure("q",null,true,ManagementMethodology.HYBRID)
        ProjectNestingRepository(db).move("p","q",null)
        val outside=nodes.createNode("q",null,"Outer context task").id
        val before=snapshot();assertTrue(runCatching {nodes.sprintLinks.link(sprint,setOf(outside))}.isFailure)
        assertEquals(before,snapshot());assertNull(nodes.getNode(outside)!!.workState)
    }

}
