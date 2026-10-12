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

@RunWith(RobolectricTestRunner::class) @Config(sdk=[28],application=Application::class)
class BacklogTest {
    private val context get()=ApplicationProvider.getApplicationContext<Application>()
    private lateinit var db:Arachn0deDatabase
    private lateinit var nodes:NodeRepository
    private lateinit var repo:BacklogRepository
    private lateinit var management:ManagementRepository
    private lateinit var id:String
    private lateinit var epic:String
    private lateinit var story:String
    private lateinit var task:String
    private lateinit var goal:String
    @Before fun setup()=runBlocking {
        context.deleteDatabase("arachn0de.db");open()
        for(p in listOf("p","q","r")) db.projectDao().insert(ProjectEntity(p,p,"Description $p",0,1,2))
        id=management.configure("p",null,true,ManagementMethodology.AGILE)!!.id
        epic=nodes.createNode("p",null,"Epic",purpose=NodePurpose.LAYER).id
        story=nodes.createNode("p",null,"Story",purpose=NodePurpose.LAYER).id
        val origin=nodes.createNode("p",epic,"Origin",purpose=NodePurpose.LAYER)
        task=nodes.createNode("p",origin.id,"Task",description="Full original content").id
        goal=management.planning.createObjective(id,"Goal").id
    }
    private fun open() {db=Arachn0deDatabase.create(context);nodes=NodeRepository(db);management=nodes.management;repo=management.backlog}
    @After fun close() {db.close()}
    private fun backup()=BackupRepository(db,context,BackupAvatarFiles(context,BackupFixture::syncDirectory))
    private suspend fun snapshot()=backup().snapshot().copy(createdAt=0)
    private suspend fun link() {
        repo.setRole(id,epic,LayerRole.EPIC);repo.setRole(id,story,LayerRole.STORY)
        repo.linkObjective(id,goal,epic);repo.linkEpic(id,epic,story);repo.linkTask(id,story,task)
    }
    @Test fun rolesAreOptionalAndReversibleAndNeverChangeNodesOrDescendants()=runBlocking {
        val before=db.backupDao().nodes();repo.setRole(id,epic,LayerRole.EPIC)
        repo.setRole(id,epic,LayerRole.STORY)
        assertTrue(runCatching {repo.setRole(id,epic,null)}.isFailure)
        repo.setRole(id,epic,null,confirmRelationLoss=true)
        assertTrue(db.backlogDao().roles().isEmpty());assertTrue(db.backlogDao().entries().isEmpty())
        assertEquals(before,db.backupDao().nodes())
        assertTrue(runCatching {repo.setRole(id,task,LayerRole.STORY)}.isFailure)
        assertEquals(before,db.backupDao().nodes())
    }
    @Test fun manyToManyTypedRelationsAreDeduplicatedAndBacklogDoesNotDuplicateTasks()=runBlocking {
        link();val otherStory=nodes.createNode("p",null,"Other story",purpose=NodePurpose.LAYER).id
        val otherEpic=nodes.createNode("p",null,"Other epic",purpose=NodePurpose.LAYER).id
        val otherGoal=management.planning.createObjective(id,"Other goal").id
        repo.setRole(id,otherStory,LayerRole.STORY);repo.setRole(id,otherEpic,LayerRole.EPIC)
        repo.linkObjective(id,otherGoal,epic);repo.linkEpic(id,otherEpic,story);repo.linkTask(id,otherStory,task)
        repeat(2) {repo.linkObjective(id,goal,epic);repo.linkEpic(id,epic,story);repo.linkTask(id,story,task);repo.addTask(id,task)}
        assertEquals(2,db.backlogDao().objectiveEpics().size);assertEquals(2,db.backlogDao().epicStories().size);assertEquals(2,db.backlogDao().storyTasks().size)
        assertEquals(1,db.backlogDao().entries().count {it.nodeId==task})
        val before=snapshot()
        for(action in listOf<suspend ()->Unit>({repo.linkEpic(id,story,epic)},{repo.linkEpic(id,epic,epic)},{repo.linkTask(id,story,story)},{repo.linkObjective(id,goal,story)})) {
            assertTrue(runCatching {action()}.isFailure);assertEquals(before,snapshot())
        }
    }
    @Test fun roleChangeRequiresAcknowledgementAndRemovesOnlyAffectedManagementEdges()=runBlocking {
        link();val before=db.backupDao().nodes();val records=repo.records()
        assertTrue(runCatching {repo.setRole(id,story,LayerRole.EPIC)}.isFailure);assertEquals(records,repo.records())
        repo.setRole(id,story,LayerRole.EPIC,confirmRelationLoss=true)
        assertEquals(listOf(ObjectiveEpicEntity(goal,epic)),db.backlogDao().objectiveEpics())
        assertTrue(db.backlogDao().epicStories().isEmpty());assertTrue(db.backlogDao().storyTasks().isEmpty())
        assertEquals(listOf(task),db.backlogDao().entries().map {it.nodeId});assertEquals(before,db.backupDao().nodes())
    }
    @Test fun linkAndUnlinkKeepOriginalLocationSprintWorkStateCompletionAndAcceptanceIndependent()=runBlocking {
        val sprint=nodes.createNode("p",null,"Sprint",purpose=NodePurpose.LAYER);nodes.setSprintMode(sprint.id,true)
        nodes.sprintLinks.link(sprint.id,setOf(task));nodes.setWorkState(task,WorkState.VALIDATED)
        val criterion=management.planning.createCriterion(goal,"Explicit acceptance")
        val before=db.backupDao().nodes();val sprintLinks=db.sprintLinkDao().all()
        link();repo.unlinkTask(id,story,task)
        assertEquals(before,db.backupDao().nodes());assertEquals(sprintLinks,db.sprintLinkDao().all())
        assertFalse(db.planningDao().criterion(criterion.id)!!.validated)
        assertTrue(db.backlogDao().entries().any {it.nodeId==task})
        repo.linkTask(id,story,task);assertEquals(1,db.backlogDao().storyTasks().size)
        repo.reorder(id,listOf(task,story));assertEquals(listOf(task,story),db.backlogDao().entries().map {it.nodeId})
        assertEquals(before,db.backupDao().nodes());assertEquals(sprintLinks,db.sprintLinkDao().all())
    }
    @Test fun scopeBoundariesIncludeDisabledContextsAndRejectCrossContextLinksOrMovesAtomically()=runBlocking {
        link();val q=management.configure("q",null,true,ManagementMethodology.HYBRID)!!.id
        val foreign=nodes.createNode("q",null,"Foreign task").id
        val before=snapshot();assertTrue(runCatching {repo.linkTask(id,story,foreign)}.isFailure);assertEquals(before,snapshot())
        val nesting=ProjectNestingRepository(db);val layer=nesting.move("q","p",null)
        management.configure(null,layer,false,ManagementMethodology.HYBRID)
        val after=snapshot()
        assertEquals(q,contextOwners(db.backupDao().nodes(),db.conversionDao().roots(),db.managementContextDao().all())[foreign])
        assertTrue(runCatching {nodes.moveNode(task,layer)}.isFailure);assertEquals(after,snapshot())
        assertTrue(runCatching {nodes.moveSelected("p",setOf(epic,story),layer)}.isFailure);assertEquals(after,snapshot())
        assertTrue(runCatching {repo.addTask(id,foreign)}.isFailure);assertEquals(after,snapshot())
        val neutral=nodes.createNode("p",null,"Normal layer",purpose=NodePurpose.LAYER)
        assertTrue(nodes.moveNode(task,neutral.id));assertEquals(listOf(StoryTaskEntity(story,task)),db.backlogDao().storyTasks())
        assertTrue(nodes.moveNode(layer,neutral.id));assertEquals(q,contextOwners(db.backupDao().nodes(),db.conversionDao().roots(),db.managementContextDao().all())[foreign])
    }
    @Test fun conversionAndNestedIndependentContextsPreserveRecordsAndOrderIncludingDisabledData()=runBlocking {
        link();management.configure("p",null,false,ManagementMethodology.TRADITIONAL)
        val q=management.configure("q",null,true,ManagementMethodology.HYBRID)!!.id
        val qs=nodes.createNode("q",null,"Q story",purpose=NodePurpose.LAYER).id;repo.setRole(q,qs,LayerRole.STORY)
        management.configure("r",null,true,ManagementMethodology.AGILE)
        val before=repo.records();val nesting=ProjectNestingRepository(db)
        val lp=nesting.move("p","q",null);val lq=nesting.move("q","r",null)
        backup().restore(BackupJson.decode(BackupJson.encode(snapshot())))
        assertEquals(before,repo.records());assertEquals("q",nesting.promote(lq));assertEquals("p",nesting.promote(lp));assertEquals(before,repo.records())
        assertEquals(id,management.configure("p",null,true,ManagementMethodology.HYBRID)!!.id)
        assertEquals(before,repo.records())
    }
    @Test fun promotionThatWouldLoseRolesOrSplitContextIsRejectedAndFailureOrCancellationRollsBack()=runBlocking {
        link();val before=snapshot()
        assertTrue(runCatching {ProjectNestingRepository(db).promote(story)}.isFailure);assertEquals(before,snapshot())
        assertTrue(runCatching {ProjectNestingRepository(db) {error("injected")}.move("p","q",null)}.isFailure);assertEquals(before,snapshot())
        assertTrue(runCatching {ProjectNestingRepository(db) {throw kotlinx.coroutines.CancellationException("injected")}.move("p","q",null)}.isFailure);assertEquals(before,snapshot())
        val layer=ProjectNestingRepository(db).move("p","q",null)
        repo.setRole(id,layer,LayerRole.EPIC);val withRoot=snapshot()
        assertTrue(runCatching {ProjectNestingRepository(db).promote(layer)}.isFailure);assertEquals(withRoot,snapshot())
        repo.setRole(id,layer,null);assertEquals("p",ProjectNestingRepository(db).promote(layer))
    }
    @Test fun deletingOrChangingReferencedNodesIsBlockedUntilExplicitUnlinkingAndRoleRemoval()=runBlocking {
        link();val before=snapshot()
        for(action in listOf<suspend ()->Unit>({nodes.deleteNode(task)},{nodes.deleteSelected("p",setOf(epic))},{nodes.convertPurpose(story,NodePurpose.NOTE)},{nodes.convertPurpose(task,NodePurpose.LAYER)})) {
            assertTrue(runCatching {action()}.isFailure);assertEquals(before,snapshot())
        }
        repo.unlinkTask(id,story,task);repo.removeEntry(id,task);assertTrue(nodes.deleteNode(task))
        repo.setRole(id,story,null,confirmRelationLoss=true);assertTrue(nodes.deleteNode(story))
        management.planning.deleteObjective(goal);assertTrue(db.backlogDao().objectiveEpics().isEmpty())
        repo.setRole(id,epic,null);assertTrue(ProjectRepository(db.projectDao(),db).deleteProject("p"))
        assertEquals(BacklogRecords(),repo.records())
    }
    @Test fun disablingAndReopeningPreserveEverythingAndStaleOrderCannotMutateRecords()=runBlocking {
        link();repo.reorder(id,listOf(task,story));val before=snapshot();db.close();open();assertEquals(before,snapshot())
        assertTrue(runCatching {repo.reorder(id,listOf(task,task))}.isFailure);assertEquals(before,snapshot())
        management.configure("p",null,false,ManagementMethodology.AGILE);val disabled=repo.records()
        assertTrue(runCatching {repo.unlinkTask(id,story,task)}.isFailure);assertEquals(disabled,repo.records())
        management.configure("p",null,true,ManagementMethodology.TRADITIONAL);assertEquals(disabled,repo.records())
        val other=androidx.room.Room.inMemoryDatabaseBuilder(context,Arachn0deDatabase::class.java).build()
        try {assertEquals(BacklogRecords(),BacklogRepository(other).records())} finally {other.close()}
    }
    @Test fun json23RestoresEverythingAndInvalidRecordsOrRestoreFailurePreservePriorState()=runBlocking {
        link();val data=snapshot();val bytes=BackupJson.encode(data)
        assertEquals(29,org.json.JSONObject(String(bytes)).getInt("dataVersion"));assertEquals(data,BackupJson.decode(bytes))
        backup().restore(BackupJson.decode(bytes));assertEquals(data,snapshot())
        val role=data.managementRoles.first();val entry=data.backlogEntries.first()
        val bad=listOf(data.copy(managementRoles=data.managementRoles+role),data.copy(managementRoles=listOf(role.copy(contextId="absent"))),data.copy(epicStories=listOf(EpicStoryEntity(story,epic))),data.copy(storyTasks=listOf(StoryTaskEntity(story,epic))),data.copy(backlogEntries=listOf(entry,entry.copy(nodeId=task,position=entry.position))),data.copy(objectiveEpics=listOf(ObjectiveEpicEntity("missing",epic))))
        bad.forEach {assertTrue(runCatching {backup().restore(it)}.isFailure);assertEquals(data,snapshot())}
        db.openHelper.writableDatabase.execSQL("CREATE TRIGGER fail_backlog BEFORE INSERT ON story_tasks BEGIN SELECT RAISE(ABORT,'injected'); END")
        try {assertTrue(runCatching {backup().restore(data)}.isFailure);assertEquals(data,snapshot())} finally {db.openHelper.writableDatabase.execSQL("DROP TRIGGER fail_backlog")}
        val old=data.copy(managementLayers=emptyList(),managementRoles=emptyList(),objectiveEpics=emptyList(),epicStories=emptyList(),storyTasks=emptyList(),backlogEntries=emptyList())
        assertEquals(22,org.json.JSONObject(String(BackupJson.encode(old))).getInt("dataVersion"));backup().restore(BackupJson.decode(BackupJson.encode(old)))
        assertEquals(BacklogRecords(),repo.records());assertEquals(old.managementObjectives,db.planningDao().objectives())
    }
    @Test fun physicalIdentityCollisionsDoNotAssociateOrMergeIndependentBacklogs()=runBlocking {
        link();val q=management.configure("q",null,true,ManagementMethodology.HYBRID)!!.id
        val existing=nodes.createNode("q",null,"Unrelated layer",purpose=NodePurpose.LAYER)
        db.nodeDao().insert(db.nodeDao().getById(existing.id)!!.copy(id="p"));repo.setRole(q,"p",LayerRole.EPIC)
        val before=repo.records();val nesting=ProjectNestingRepository(db);val layer=nesting.move("p","q",null)
        assertNotEquals("p",layer);assertEquals(id,db.conversionDao().node(layer)!!.id)
        db.projectDao().insert(ProjectEntity("p","Unrelated project","",0,1,2));val unrelated=management.configure("p",null,true,ManagementMethodology.AGILE)!!.id
        assertNotEquals(id,unrelated)
        val promoted=nesting.promote(layer);assertNotEquals("p",promoted);assertEquals(id,db.conversionDao().project(promoted)!!.id)
        assertEquals(before,repo.records());assertEquals(q,db.backlogDao().roles().first {it.nodeId=="p"}.contextId)
    }
    @Test fun largeDeepTreeResolvesNearestContextIterativelyWithoutStackOverflow()=runBlocking {
        val template=db.nodeDao().getById(task)!!
        val tree=List(30000) {i->template.copy(id="deep-$i",parentId=if(i==0) null else "deep-${i-1}",purpose=if(i==29999) "ACTION" else "LAYER")}
        val parent=db.conversionDao().project("p")!!
        val inner=parent.copy(id="independent",projectId=null,nodeId="deep-15000",projectIdentity="q",nodeIdentity="deep-15000")
        val contexts=db.managementContextDao().all()+ManagementContextEntity("independent",false,"HYBRID")
        val owners=contextOwners(tree,listOf(parent,inner),contexts)
        assertEquals(30000,owners.size);assertEquals(id,owners["deep-14999"])
        assertEquals("independent",owners["deep-15000"]);assertEquals("independent",owners["deep-29999"])
        BacklogRecords(roles=listOf(ManagementRoleEntity("deep-15000","independent","STORY")),storyTasks=listOf(StoryTaskEntity("deep-15000","deep-29999")),entries=listOf(BacklogEntryEntity("deep-29999","independent",0))).validate(tree,listOf(parent,inner),contexts,emptyList())
    }
    @Test fun databaseConstraintsRejectDuplicateEdgesWrongRolesAndOutOfScopeRecords()=runBlocking {
        link();assertTrue(runCatching {db.backlogDao().insert(EpicStoryEntity(story,epic))}.isFailure)
        assertTrue(runCatching {db.backlogDao().save(ManagementRoleEntity(task,id,"STORY"))}.isFailure)
        assertTrue(runCatching {db.backlogDao().save(BacklogEntryEntity(task,"foreign",0))}.isFailure)
        val json=org.json.JSONObject(String(BackupJson.encode(snapshot())));json.getJSONArray("backlogEntries").getJSONObject(0).put("position",2147483648L)
        assertTrue(runCatching {BackupJson.decode(json.toString().toByteArray())}.isFailure)
    }
}
