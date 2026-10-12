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
class CascadeRelationsTest {
    private val app get()=ApplicationProvider.getApplicationContext<Application>()
    private lateinit var db:Arachn0deDatabase
    private lateinit var repo:ManagementRepository
    private lateinit var nodes:NodeRepository
    private lateinit var context:String
    private lateinit var story:String
    private lateinit var sprint:String
    @Before fun setup()=runBlocking {
        app.deleteDatabase("arachn0de.db");db=Arachn0deDatabase.create(app);repo=ManagementRepository(db);nodes=NodeRepository(db)
        db.projectDao().insert(ProjectEntity("p","Project","",0,1,2))
        context=repo.configure("p",null,true,ManagementMethodology.AGILE)!!.id
        val layers=db.managementLayerDao().all().associateBy {it.kind}
        val epic=repo.cascade.create(context,layers.getValue("BACKLOG").nodeId,"Epic",LayerRole.EPIC)
        story=repo.cascade.create(context,epic,"Story",LayerRole.STORY)
        sprint=repo.cascade.create(context,layers.getValue("SPRINTS").nodeId,"Sprint",sprint=true)
    }
    @After fun close() {db.close()}
    @Test fun emptyStoryAndFutureDeepTasksJoinSprintWithoutCopiesOrNotesAsWork()=runBlocking {
        val location=db.nodeDao().getById(story)!!.parentId
        repo.relations.addStory(sprint,story)
        val plan=db.iterationDao().plans().single();repo.iterations.start(plan.id)
        val layer=nodes.createNode("p",story,"Inner",purpose=NodePurpose.LAYER)
        val note=nodes.createNode("p",layer.id,"Note",purpose=NodePurpose.NOTE)
        val task=nodes.createNode("p",layer.id,"Task")
        assertEquals(listOf(SprintLinkEntity(task.id,sprint)),db.sprintLinkDao().all())
        assertEquals("UNPLANNED",db.nodeDao().getById(task.id)!!.workState)
        assertEquals(location,db.nodeDao().getById(story)!!.parentId)
        assertEquals(layer.id,db.nodeDao().getById(note.id)!!.parentId)
        assertEquals(listOf(task.id),db.iterationDao().commitments().map {it.taskId})
        nodes.updateNode(note.id,"Changed in Backlog","Shared")
        assertEquals("Shared",db.nodeDao().getById(note.id)!!.description)
    }
    @Test fun conflictingSprintAndStaleUnlinkAreAtomicAndRequirePhaseAcknowledgement()=runBlocking {
        val task=nodes.createNode("p",story,"Task")
        repo.relations.addStory(sprint,story)
        nodes.setWorkState(task.id,WorkState.DOING)
        val another=repo.cascade.create(context,db.managementLayerDao().all().first {it.kind=="SPRINTS"}.nodeId,"Other",sprint=true)
        assertTrue(runCatching {repo.relations.addStory(another,story)}.isFailure)
        assertEquals(listOf(SprintStoryEntity(sprint,story)),db.cascadeRelationDao().stories())
        assertTrue(runCatching {repo.relations.removeStory(sprint,story,mapOf(task.id to "UNPLANNED"))}.isFailure)
        assertEquals("DOING",db.nodeDao().getById(task.id)!!.workState)
        repo.relations.removeStory(sprint,story,mapOf(task.id to "DOING"))
        assertTrue(db.cascadeRelationDao().stories().isEmpty());assertNull(db.nodeDao().getById(task.id)!!.workState)
        assertNotNull(db.nodeDao().getById(story))
    }
    @Test fun objectiveWorkDeduplicatesOverlappingStoriesAndTasksAndExcludesNotes()=runBlocking {
        val goal=repo.planning.createObjective(context,"Objective").id
        val empty=objectiveWorkProgress(goal,db.backupDao().nodes(),emptyList(),repo.backlog.records())
        assertEquals(WorkProgress(0,0),empty);assertFalse(empty.complete)
        val task=nodes.createNode("p",story,"Task")
        nodes.createNode("p",story,"Note",purpose=NodePurpose.NOTE)
        repo.relations.objective(goal,story,true);repo.relations.objective(goal,task.id,true)
        val second=repo.planning.createObjective(context,"Another").id;repo.relations.objective(second,story,true)
        assertEquals(WorkProgress(0,1),objectiveWorkProgress(goal,db.backupDao().nodes(),db.cascadeRelationDao().objectives(),repo.backlog.records()))
        nodes.setCompleted(task.id,true)
        assertEquals(WorkProgress(1,1),objectiveWorkProgress(goal,db.backupDao().nodes(),db.cascadeRelationDao().objectives(),repo.backlog.records()))
        assertEquals(WorkProgress(1,1),objectiveWorkProgress(second,db.backupDao().nodes(),db.cascadeRelationDao().objectives(),repo.backlog.records()))
    }
    @Test fun selfIdentityAndAllNewRelationsSurviveBackupReopenAndConversion()=runBlocking {
        db.personDao().insert(PersonEntity("me","Me",null));db.personDao().insert(PersonEntity("other","Other",null))
        db.cascadeRelationDao().save(LocalIdentityEntity(personId="me"));db.cascadeRelationDao().save(LocalIdentityEntity(personId="other"))
        assertEquals(listOf(LocalIdentityEntity(personId="other")),db.cascadeRelationDao().identities())
        repo.relations.addStory(sprint,story)
        val task=nodes.createNode("p",story,"Task");nodes.setWorkState(task.id,WorkState.DOING)
        val goal=repo.planning.createObjective(context,"Goal").id;repo.relations.objective(goal,story,true)
        val backup=BackupRepository(db,app,BackupAvatarFiles(app,BackupFixture::syncDirectory))
        val snapshot=backup.snapshot();backup.restore(BackupJson.decode(BackupJson.encode(snapshot)))
        assertEquals(snapshot.sprintStories,db.cascadeRelationDao().stories());assertEquals(snapshot.objectiveWork,db.cascadeRelationDao().objectives())
        assertEquals(snapshot.localIdentities,db.cascadeRelationDao().identities())
        db.projectDao().insert(ProjectEntity("q","Target","",0,1,2))
        val layer=ProjectNestingRepository(db).move("p","q",null)
        assertEquals(snapshot.sprintStories,db.cascadeRelationDao().stories())
        assertEquals("DOING",db.nodeDao().getById(task.id)!!.workState)
        ProjectNestingRepository(db).promote(layer)
        assertEquals(snapshot.managementLayers,db.managementLayerDao().all())
        db.close();db=Arachn0deDatabase.create(app)
        assertEquals(snapshot.sprintStories,db.cascadeRelationDao().stories())
        db.personDao().delete("other");assertTrue(db.cascadeRelationDao().identities().isEmpty())
    }
    @Test fun historicExternalTasksRemainSharedAndJoinStorySprint()=runBlocking {
        val task=nodes.createNode("p",null,"External task")
        repo.relations.addStory(sprint,story)
        repo.backlog.linkTask(context,story,task.id)
        assertNull(db.nodeDao().getById(task.id)!!.parentId)
        assertEquals(listOf(SprintLinkEntity(task.id,sprint)),db.sprintLinkDao().all())
        repo.relations.removeStory(sprint,story,mapOf(task.id to "UNPLANNED"))
        assertNotNull(db.nodeDao().getById(task.id))
        assertEquals(listOf(StoryTaskEntity(story,task.id)),db.backlogDao().storyTasks())
    }
    @Test fun closedSprintRetainsSnapshotAndNewDescendantsJoinOnlyWhenReopened()=runBlocking {
        repo.relations.addStory(sprint,story)
        val first=nodes.createNode("p",story,"Initial")
        val plan=db.iterationDao().plans().single()
        repo.iterations.start(plan.id)
        repo.iterations.close(plan.id,listOf(db.nodeDao().getById(first.id)!!))
        val history=db.iterationDao().closures()
        val second=nodes.createNode("p",story,"After close")
        assertFalse(db.sprintLinkDao().all().any {it.taskId==second.id})
        repo.iterations.start(plan.id,true)
        assertTrue(db.sprintLinkDao().all().any {it.taskId==second.id && it.sprintId==sprint})
        assertEquals(history,db.iterationDao().closures())
        assertFalse(db.nodeDao().getById(first.id)!!.isCompleted)
    }

}
