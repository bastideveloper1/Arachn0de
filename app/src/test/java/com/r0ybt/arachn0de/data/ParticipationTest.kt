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
class ParticipationTest {
    private val context get()=ApplicationProvider.getApplicationContext<Application>()
    private lateinit var db:Arachn0deDatabase
    private lateinit var nodes:NodeRepository
    private lateinit var repo:ParticipationRepository
    private lateinit var id:String
    private lateinit var other:String
    private lateinit var task:String
    private lateinit var epic:String
    private lateinit var story:String
    private lateinit var goal:String
    private fun open() {db=Arachn0deDatabase.create(context);nodes=NodeRepository(db);repo=nodes.management.participation}
    @Before fun setup()=runBlocking {
        context.deleteDatabase("arachn0de.db");open()
        listOf("p","q","r").forEach {db.projectDao().insert(ProjectEntity(it,it,"Description",0,1,2))}
        id=nodes.management.configure("p",null,true,ManagementMethodology.AGILE)!!.id
        other=nodes.management.configure("q",null,true,ManagementMethodology.HYBRID)!!.id
        db.personDao().insert(PersonEntity("person","Original",null,3f,.2f,-.5f))
        epic=nodes.createNode("p",null,"Epic",purpose=NodePurpose.LAYER).id
        story=nodes.createNode("p",null,"Story",purpose=NodePurpose.LAYER).id
        task=nodes.createNode("p",epic,"Task").id
        goal=nodes.management.planning.createObjective(id,"Goal").id
        nodes.management.backlog.setRole(id,epic,LayerRole.EPIC)
        nodes.management.backlog.setRole(id,story,LayerRole.STORY)
    }
    @After fun close() {db.close()}
    private fun backup()=BackupRepository(db,context,BackupAvatarFiles(context,BackupFixture::syncDirectory))
    private suspend fun populated() {
        repo.add(id,"person");repo.role(id,"person",ResponsibilityRole.PROJECT_LEAD,true)
        repo.assign(id,"person",goal,true,ResponsibilityRole.VALIDATOR)
        listOf(epic,story,task).forEach {repo.assign(id,"person",it,false,ResponsibilityRole.EXECUTOR)}
    }
    @Test fun sharedIdentityMultipleContextRolesAndNoTraditionalAssignmentChanges()=runBlocking {
        db.personDao().assign(listOf(NodePersonEntity(task,"person")))
        val people=db.backupDao().persons();val traditional=db.backupDao().assignments();val tree=db.backupDao().nodes();val backlog=nodes.management.backlog.records()
        populated();repo.add(other,"person");repo.role(other,"person",ResponsibilityRole.SUPERVISOR,true);repo.role(id,"person",ResponsibilityRole.VALIDATOR,true)
        repeat(2) {repo.add(id,"person");repo.assign(id,"person",task,false,ResponsibilityRole.EXECUTOR)}
        assertEquals(2,repo.records().participants.size);assertEquals(3,repo.records().roles.size);assertEquals(3,repo.records().nodes.size)
        assertEquals(people,db.backupDao().persons());assertEquals(traditional,db.backupDao().assignments());assertEquals(tree,db.backupDao().nodes());assertEquals(backlog,nodes.management.backlog.records())
    }
    @Test fun criterionSprintCompletionAndPhasesStayIndependent()=runBlocking {
        val sprint=nodes.createNode("p",null,"Sprint",purpose=NodePurpose.LAYER);nodes.setSprintMode(sprint.id,true)
        nodes.sprintLinks.link(sprint.id,setOf(task));nodes.setWorkState(task,WorkState.VALIDATED)
        val criterion=nodes.management.planning.createCriterion(goal,"Manual")
        val before=db.backupDao().nodes();val links=db.sprintLinkDao().all();populated()
        assertEquals(before,db.backupDao().nodes());assertEquals(links,db.sprintLinkDao().all());assertFalse(db.planningDao().criterion(criterion.id)!!.validated)
        repo.assign(id,"person",goal,true,ResponsibilityRole.VALIDATOR,false)
        assertFalse(db.planningDao().criterion(criterion.id)!!.validated)
    }
    @Test fun withdrawalRequiresExplicitConfirmationAndKeepsAllOtherData()=runBlocking {
        populated();val before=backup().snapshot();assertTrue(runCatching {repo.remove(id,"person")}.exceptionOrNull()!!.message!!.contains("Confirma"))
        assertEquals(before.copy(createdAt=0),backup().snapshot().copy(createdAt=0))
        repo.remove(id,"person",true);assertEquals(ParticipationRecords(),repo.records())
        assertEquals(before.persons,db.backupDao().persons());assertEquals(before.nodes,db.backupDao().nodes());assertEquals(before.managementObjectives,db.planningDao().objectives())
        repo.add(id,"person");repo.remove(id,"person");assertTrue(repo.records().participants.isEmpty())
    }
    @Test fun deletingPersonCascadesOnlyItsRelationshipsInOneTransaction()=runBlocking {
        populated();val tree=db.backupDao().nodes();val goals=db.planningDao().objectives()
        val people=PersonRepository(db,AvatarStore(context,syncDirectory=BackupFixture::syncDirectory));assertTrue(people.delete("person"));assertEquals(ParticipationRecords(),repo.records());assertEquals(tree,db.backupDao().nodes());assertEquals(goals,db.planningDao().objectives())
        db.openHelper.readableDatabase.query("PRAGMA foreign_key_check").use {assertFalse(it.moveToFirst())}
    }
    @Test fun disablingReactivatingAndRestartPreserveEveryRelationship()=runBlocking {
        populated();val rows=repo.records();nodes.management.configure("p",null,false,ManagementMethodology.TRADITIONAL)
        assertTrue(runCatching {repo.role(id,"person",ResponsibilityRole.SUPERVISOR,true)}.isFailure);assertEquals(rows,repo.records())
        db.close();open();assertEquals(rows,repo.records());nodes.management.configure("p",null,true,ManagementMethodology.HYBRID);assertEquals(rows,repo.records())
    }
    @Test fun projectLayerRoundTripPreservesStableContextAndResponsibilities()=runBlocking {
        populated();repo.add(other,"person");repo.role(other,"person",ResponsibilityRole.SUPERVISOR,true);val rows=repo.records();val nesting=ProjectNestingRepository(db)
        val layer=nesting.move("p","q",null);assertEquals(rows,repo.records());repo.validate()
        nesting.promote(layer);assertEquals(rows,repo.records());repo.validate();assertEquals(2,db.managementContextDao().all().size)
    }
    @Test fun invalidMovesPurposeChangesRoleRemovalAndDeletionRollback()=runBlocking {
        repo.add(id,"person");repo.assign(id,"person",task,false,ResponsibilityRole.EXECUTOR)
        val destination=nodes.createNode("q",null,"Destination",purpose=NodePurpose.LAYER).id
        val before=backup().snapshot().copy(createdAt=0)
        for(action in listOf<suspend ()->Unit>({nodes.moveNode(task,destination)},{nodes.convertPurpose(task,NodePurpose.LAYER)},{nodes.deleteNode(task)})) {assertTrue(runCatching {action()}.isFailure);assertEquals(before,backup().snapshot().copy(createdAt=0))}
        repo.assign(id,"person",epic,false,ResponsibilityRole.SUPERVISOR)
        val rows=repo.records();assertTrue(runCatching {nodes.management.backlog.setRole(id,epic,null,true)}.isFailure);assertEquals(rows,repo.records());assertEquals("EPIC",db.backlogDao().roles().first {it.nodeId==epic}.role)
    }
    @Test fun nestedDisabledContextNeverAcceptsParentResponsibilities()=runBlocking {
        populated();val layer=ProjectNestingRepository(db).move("p","q",null)
        nodes.management.configure(null,layer,false,ManagementMethodology.AGILE)
        repo.add(other,"person");val rows=repo.records()
        assertTrue(runCatching {repo.assign(other,"person",task,false,ResponsibilityRole.VALIDATOR)}.isFailure);assertEquals(rows,repo.records())
    }
    @Test fun newBackupRoundTripAndRestoreRetainAllRelationsAndOriginalFraming()=runBlocking {
        populated();val before=backup().snapshot();val bytes=BackupJson.encode(before)
        assertEquals(29,JSONObject(bytes.toString(Charsets.UTF_8)).getInt("dataVersion"))
        val decoded=BackupJson.decode(bytes);assertEquals(before,decoded)
        repo.remove(id,"person",true);backup().restore(decoded);assertEquals(before.copy(createdAt=0),backup().snapshot().copy(createdAt=0))
    }
    @Test fun historical23HasEmptyParticipantsAndRestoresWithoutInventingThem()=runBlocking {
        val source=backup().snapshot().copy(managementLayers=emptyList());val bytes=BackupJson.encode(source);assertEquals(23,JSONObject(bytes.toString(Charsets.UTF_8)).getInt("dataVersion"))
        populated();val decoded=BackupJson.decode(bytes);assertTrue(decoded.managementParticipants.isEmpty());backup().restore(decoded);assertEquals(ParticipationRecords(),repo.records())
    }
    @Test fun invalidBackupsFailBeforeReplacingConfirmedData()=runBlocking {
        populated();val before=backup().snapshot();val member=before.managementParticipants.single()
        val invalid=listOf(before.copy(managementParticipants=listOf(member,member)),before.copy(participantRoles=listOf(ParticipantRoleEntity(id,"missing","EXECUTOR"))),before.copy(objectiveResponsibilities=listOf(ObjectiveResponsibilityEntity(other,"person",goal,"VALIDATOR"))),before.copy(nodeResponsibilities=listOf(NodeResponsibilityEntity(id,"person",task,"UNKNOWN"))))
        for(data in invalid) {assertTrue(runCatching {backup().restore(data)}.isFailure);assertEquals(before.copy(createdAt=0),backup().snapshot().copy(createdAt=0))}
        val json=JSONObject(BackupJson.encode(before).toString(Charsets.UTF_8));json.getJSONArray("managementParticipants").getJSONObject(0).put("extra",true);assertTrue(runCatching {BackupJson.decode(json.toString().toByteArray())}.isFailure)
    }
    @Test fun directSqlRejectsInvalidRolesScopeAndMembership()=runBlocking {
        repo.add(id,"person");val sql=db.openHelper.writableDatabase
        for(query in listOf("INSERT INTO participant_roles VALUES ('$id','person','UNKNOWN')","INSERT INTO node_responsibilities VALUES ('$other','person','$task','EXECUTOR')","INSERT INTO objective_responsibilities VALUES ('$other','person','$goal','VALIDATOR')")) assertTrue(runCatching {sql.execSQL(query)}.isFailure)
        assertTrue(repo.records().nodes.isEmpty());assertTrue(repo.records().objectives.isEmpty())
    }
    @Test fun scopedMovesAndNewBoundariesRejectLossWithoutChangingTheTree()=runBlocking {
        repo.add(id,"person");repo.assign(id,"person",task,false,ResponsibilityRole.EXECUTOR)
        val parent=ProjectNestingRepository(db).move("p","q",null)
        val outside=nodes.createNode("q",null,"Outside",purpose=NodePurpose.LAYER).id
        val before=backup().snapshot().copy(createdAt=0)
        assertTrue(runCatching {nodes.moveNode(task,outside)}.exceptionOrNull()!!.message!!.contains("responsabilidades"))
        assertEquals(before,backup().snapshot().copy(createdAt=0))
        assertTrue(runCatching {nodes.moveSelected("q",setOf(task),outside)}.isFailure)
        assertEquals(before,backup().snapshot().copy(createdAt=0))
        val inside=nodes.createNode("q",parent,"Inside",purpose=NodePurpose.LAYER).id
        assertTrue(nodes.moveNode(task,inside));repo.validate()
        val rows=repo.records();ProjectNestingRepository(db).promote(parent);assertEquals(rows,repo.records())
        // Only the managed responsibility guards this task; it has no Backlog entry.
        assertTrue(db.backlogDao().entries().none {it.nodeId==task})
    }
    @Test fun existingSharedAvatarBytesAndFramingRestoreWithManagedParticipation()=runBlocking {
        val bytes=BackupFixture.png();val directory=java.io.File(context.filesDir,"avatars").apply {mkdirs()}
        java.io.File(directory,BackupFixture.avatar).writeBytes(bytes)
        db.personDao().update("person","Original",BackupFixture.avatar,4f,.3f,-.2f)
        db.personDao().insert(PersonEntity("shared","Shared",BackupFixture.avatar))
        populated();val before=backup().snapshot();assertEquals(1,before.avatars.size)
        val decoded=BackupJson.decode(BackupJson.encode(before));backup().restore(decoded)
        val after=backup().snapshot();BackupFixture.assertData(before,after)
        assertEquals(before.managementParticipants,after.managementParticipants);assertEquals(before.nodeResponsibilities,after.nodeResponsibilities)
        assertEquals(4f,after.persons.first {it.id=="person"}.avatarZoom)
    }

    @Test fun failedConversionAndNewContextBoundaryKeepAllConfirmedData()=runBlocking {
        populated();val before=backup().snapshot().copy(createdAt=0)
        assertTrue(runCatching {ProjectNestingRepository(db) {error("Before commit fixture")}.move("p","q",null)}.isFailure)
        assertEquals(before,backup().snapshot().copy(createdAt=0))
        val normalLayer=ProjectNestingRepository(db).move("r","p",null)
        val child=nodes.createNode("p",normalLayer,"Scoped task").id
        repo.assign(id,"person",child,false,ResponsibilityRole.EXECUTOR)
        val saved=backup().snapshot().copy(createdAt=0)
        assertTrue(runCatching {nodes.management.configure(null,normalLayer,true,ManagementMethodology.AGILE)}.isFailure)
        assertEquals(saved,backup().snapshot().copy(createdAt=0))
    }

}
