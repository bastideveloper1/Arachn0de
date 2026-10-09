package com.r0ybt.arachn0de.metro

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.r0ybt.arachn0de.backup.*
import com.r0ybt.arachn0de.data.local.*
import com.r0ybt.arachn0de.data.repository.*
import com.r0ybt.arachn0de.domain.model.NodePurpose
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28],application=Application::class)
class MetroNativePersistenceTest {
    private val context get()=ApplicationProvider.getApplicationContext<Application>()
    private lateinit var db:Arachn0deDatabase
    private lateinit var repo:MetroRepository
    private lateinit var nodes:NodeRepository
    private lateinit var backup:BackupRepository
    @Before fun open() {context.deleteDatabase("arachn0de.db");db=Arachn0deDatabase.create(context);repo=MetroRepository(db,context);nodes=NodeRepository(db);backup=BackupRepository(db,context,BackupAvatarFiles(context,BackupFixture::syncDirectory))}
    @After fun close() {db.close()}
    private suspend fun route(destination:String="republica")=MetroPlanner.plan(repo.snapshot().preferences.planningNetwork,listOf("los-heroes",destination),false,MetroRestrictions())!!
    private fun plan(route:MetroRoute)=MetroCodec.journey(MetroJourneyData(route))
    @Test fun attachesIndependentActiveJourneyInsideSubLayerWithoutDuplicatingAndRoundTripsBackup()=runBlocking<Unit> {
        val p=ProjectRepository(db.projectDao()).createProject("P")
        val layer=nodes.createNode(p.id,null,"Capa",purpose=NodePurpose.LAYER)
        val child=nodes.createNode(p.id,layer.id,"Subcapa",purpose=NodePurpose.LAYER)
        val id=repo.savePlan(route());repo.begin(id,repo.snapshot().journeys.single().row.revision,MetroTime(1_000_000,100_000,1))
        val before=repo.snapshot();val journey=before.journeys.single();val active=journey.data.active!!
        nodes.withEditorAssignments("task",null,before.preferences.catalog,plan(journey.data.plan),null,id,journey.row.revision) {nodes.createNode(p.id,child.id,"Viaje",creationId="task")}
        val after=repo.snapshot().journeys.single();assertEquals(id,after.row.id);assertEquals("task",after.row.nodeId);assertEquals(child.id,db.nodeDao().getById("task")!!.parentId)
        assertEquals(active,after.data.active);assertEquals(active.events,after.data.active!!.events)
        val archive=backup.create().readBytes();val candidate=backup.inspect(archive.inputStream());backup.restore(candidate);backup.discard(candidate)
        val restored=repo.snapshot().journeys.single();assertEquals(id,restored.row.id);assertEquals("task",restored.row.nodeId)
        assertEquals(child.id,db.nodeDao().getById("task")!!.parentId);assertEquals(active.events,restored.data.active!!.events);assertTrue(restored.data.active!!.uncertain)
    }
    @Test fun editingAttachedPlanKeepsSessionHistoryEnabledStateAndTaskMetadata()=runBlocking<Unit> {
        val p=ProjectRepository(db.projectDao()).createProject("P");val task=nodes.createNode(p.id,null,"Original","Texto",startAt=100,dueAt=200)
        val id=repo.savePlan(route(),task.id);repo.enabled(id,false)
        var state=repo.snapshot();var journey=state.journeys.single()
        nodes.withEditorAssignments(task.id,null,state.preferences.catalog,plan(route("tobalaba")),null,id,journey.row.revision) {nodes.updateEditor(task.id,"Editado","Texto",100,200,null,false,true,emptySet())}
        assertFalse(repo.snapshot().journeys.single().row.enabled);assertEquals("Editado",db.nodeDao().getById(task.id)!!.title);assertEquals(200L,db.nodeDao().getById(task.id)!!.dueAt)
        repo.enabled(id,true);journey=repo.snapshot().journeys.single();repo.begin(id,journey.row.revision,MetroTime(1_000_000,100_000,1))
        db.personDao().insert(PersonEntity("person","Viajera",null));state=repo.snapshot();journey=state.journeys.single();val active=journey.data.active!!
        nodes.withEditorAssignments(task.id,null,state.preferences.catalog,plan(route("cerrillos")),"person",id,journey.row.revision) {true}
        val edited=repo.snapshot().journeys.single();assertEquals(id,edited.row.id);assertEquals("cerrillos",edited.data.plan.stops.last())
        assertEquals(active.copy(personId="person"),edited.data.active);assertEquals("person",edited.row.personId)
    }
    @Test fun staleMissingOrAlreadyAttachedJourneyRollsBackCreationAndMetadata()=runBlocking<Unit> {
        val p=ProjectRepository(db.projectDao()).createProject("P");val id=repo.savePlan(route());val state=repo.snapshot();val j=state.journeys.single()
        repo.enabled(id,false)
        assertThrows(IllegalArgumentException::class.java) {runBlocking {nodes.withEditorAssignments("stale",null,state.preferences.catalog,plan(j.data.plan),null,id,j.row.revision) {nodes.createNode(p.id,null,"Stale",creationId="stale")}}}
        assertNull(db.nodeDao().getById("stale"))
        var row=repo.snapshot().journeys.single()
        nodes.withEditorAssignments("a",null,state.preferences.catalog,plan(j.data.plan),null,id,row.row.revision) {nodes.createNode(p.id,null,"A",creationId="a")}
        row=repo.snapshot().journeys.single();val before=repo.snapshot()
        assertThrows(IllegalArgumentException::class.java) {runBlocking {nodes.withEditorAssignments("b",null,state.preferences.catalog,plan(j.data.plan),null,id,row.row.revision) {nodes.createNode(p.id,null,"B",creationId="b")}}}
        assertNull(db.nodeDao().getById("b"));assertEquals(before,repo.snapshot());assertEquals("a",repo.snapshot().journeys.single().row.nodeId)
        assertThrows(IllegalArgumentException::class.java) {runBlocking {nodes.withEditorAssignments("missing",null,state.preferences.catalog,plan(j.data.plan),null,"missing",0) {nodes.createNode(p.id,null,"X",creationId="missing")}}}
        assertNull(db.nodeDao().getById("missing"))
        assertThrows(IllegalArgumentException::class.java) {runBlocking {nodes.withEditorAssignments("incomplete",null,state.preferences.catalog,null,null,id,row.row.revision) {nodes.createNode(p.id,null,"X",creationId="incomplete")}}}
        assertNull(db.nodeDao().getById("incomplete"));assertEquals(before,repo.snapshot())
    }
    @Test fun metadataOnlySaveDoesNotReinterpretHistoricPlanUnderNewRestrictions()=runBlocking<Unit> {
        val p=ProjectRepository(db.projectDao()).createProject("P");val task=nodes.createNode(p.id,null,"Original")
        val id=repo.savePlan(route(),task.id)
        repo.settings {it.copy(restrictions=MetroRestrictions(closed=setOf("republica")))}
        val state=repo.snapshot();val j=state.journeys.single()
        nodes.withEditorAssignments(task.id,null,state.preferences.catalog,plan(j.data.plan),null,id,j.row.revision) {nodes.updateEditor(task.id,"Otro título","",null,null,null,false,true,emptySet())}
        assertEquals(j.data,repo.snapshot().journeys.single().data);assertEquals("Otro título",db.nodeDao().getById(task.id)!!.title)
    }
}
