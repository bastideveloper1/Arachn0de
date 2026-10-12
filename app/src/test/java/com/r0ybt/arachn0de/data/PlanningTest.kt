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
class PlanningTest {
    private val context get()=ApplicationProvider.getApplicationContext<Application>()
    private lateinit var db:Arachn0deDatabase
    private lateinit var management:ManagementRepository
    private lateinit var planning:PlanningRepository
    private var time=123456789L
    @Before fun setup()=runBlocking {
        context.deleteDatabase("arachn0de.db");open()
        for(id in listOf("p","q","r")) db.projectDao().insert(ProjectEntity(id,id,"Description $id",0,1,2))
    }
    private fun open() {db=Arachn0deDatabase.create(context);management=ManagementRepository(db);planning=PlanningRepository(db) {time}}
    @After fun close() {db.close()}
    private suspend fun activate(id:String)=management.configure(id,null,true,ManagementMethodology.AGILE)!!.id
    private fun backup()=BackupRepository(db,context,BackupAvatarFiles(context,BackupFixture::syncDirectory))
    private suspend fun snapshot()=backup().snapshot().copy(createdAt=0)
    private suspend fun populate(id:String):ManagementObjectiveEntity {
        planning.saveCharter(ProjectCharterEntity(id,purpose="Purpose $id",scope="Scope"))
        val goal=planning.createObjective(id,"Goal $id","Full description")
        val a=planning.createCriterion(goal.id,"First");val b=planning.createCriterion(goal.id,"Second")
        planning.validateCriterion(a.id,true);planning.reorderCriteria(goal.id,listOf(b.id,a.id))
        return goal
    }
    @Test fun partialCharterAndStableOrderedObjectivesAndCriteriaPersistAcrossRestart()=runBlocking {
        val id=activate("p");val first=populate(id);val second=planning.createObjective(id,"Second")
        planning.editObjective(first.id,"Edited","Changed description")
        planning.reorderObjectives(id,listOf(second.id,first.id))
        val criterion=db.planningDao().criteria().first {it.validated}
        planning.editCriterion(criterion.id,"Edited criterion")
        val before=snapshot();db.close();open()
        assertEquals(before,snapshot())
        assertEquals(ProjectCharterEntity(id,purpose="Purpose $id",scope="Scope"),db.planningDao().charters().single())
        assertEquals(listOf(second.id,first.id),db.planningDao().objectives().map {it.id})
        assertEquals(criterion.copy(description="Edited criterion"),db.planningDao().criterion(criterion.id))
        assertEquals(1,db.planningDao().objective(first.id)!!.position)
    }
    @Test fun acceptanceIsExplicitEmptyNeverAcceptedAndRevocationClearsDate()=runBlocking {
        val id=activate("p");val goal=planning.createObjective(id,"Goal")
        assertEquals(ObjectiveAcceptance.NO_CRITERIA,objectiveAcceptance(emptyList()))
        val a=planning.createCriterion(goal.id,"A");val b=planning.createCriterion(goal.id,"B")
        fun status()=runBlocking {objectiveAcceptance(db.planningDao().criteria())}
        assertEquals(ObjectiveAcceptance.PENDING,status())
        planning.validateCriterion(a.id,true);time++
        planning.validateCriterion(a.id,true);assertEquals(123456789L,db.planningDao().criterion(a.id)!!.validatedAt)
        assertEquals(listOf(b.id),db.planningDao().criteria().filterNot {it.validated}.map {it.id})
        planning.validateCriterion(b.id,true);assertEquals(ObjectiveAcceptance.ACCEPTED,status())
        planning.validateCriterion(a.id,false);assertEquals(a,db.planningDao().criterion(a.id))
        assertEquals(ObjectiveAcceptance.PENDING,status())
        planning.validateCriterion(a.id,true);assertEquals(time,db.planningDao().criterion(a.id)!!.validatedAt)
    }
    @Test fun disabledContextsRejectWritesButReactivationAndMethodChangeKeepEverything()=runBlocking {
        val id=activate("p");val goal=populate(id);val original=snapshot()
        management.configure("p",null,false,ManagementMethodology.TRADITIONAL)
        for(action in listOf<suspend ()->Unit>(
            {planning.saveCharter(ProjectCharterEntity(id,purpose="lost"))},
            {planning.createObjective(id,"Other")}, {planning.editObjective(goal.id,"Other","")},
            {planning.createCriterion(goal.id,"Other")}, {planning.deleteObjective(goal.id)},
            {planning.validateCriterion(db.planningDao().criteria().first().id,true)})) assertTrue(runCatching {action()}.isFailure)
        assertEquals(original.projectCharters,snapshot().projectCharters)
        assertEquals(original.managementObjectives,snapshot().managementObjectives)
        assertEquals(original.acceptanceCriteria,snapshot().acceptanceCriteria)
        assertEquals(id,management.configure("p",null,true,ManagementMethodology.HYBRID)!!.id)
        assertEquals(original.acceptanceCriteria,snapshot().acceptanceCriteria)
    }
    @Test fun invalidEditsAndStaleOrDuplicateOrdersDoNotMutateData()=runBlocking {
        val id=activate("p");val goal=populate(id);val before=snapshot();val rows=db.planningDao().criteria()
        val actions=listOf<suspend ()->Unit>(
            {planning.createObjective(id," ")}, {planning.editObjective(goal.id,"","")},
            {planning.createCriterion(goal.id,"\n ")}, {planning.editCriterion(rows.first().id," ")},
            {planning.reorderObjectives(id,listOf("missing"))},
            {planning.reorderCriteria(goal.id,listOf(rows.first().id,rows.first().id))},
            {planning.reorderCriteria(goal.id,listOf(rows.first().id))},
            {db.planningDao().save(rows.first().copy(validated=true,validatedAt=null))},
            {db.planningDao().save(rows.first().copy(position=-1))})
        actions.forEach {assertTrue(runCatching {it()}.isFailure);assertEquals(before,snapshot())}
    }
    @Test fun nestedConversionsAndRestorePreserveAllIdentitiesDatesAndOrdersIncludingDisabledContext()=runBlocking {
        val p=activate("p");val q=activate("q");val r=activate("r")
        populate(p);populate(q);populate(r);management.configure("p",null,false,ManagementMethodology.HYBRID)
        val before=snapshot();val nesting=ProjectNestingRepository(db)
        val layerP=nesting.move("p","q",null);val layerQ=nesting.move("q","r",null)
        backup().restore(BackupJson.decode(BackupJson.encode(snapshot())))
        assertEquals("q",nesting.promote(layerQ));assertEquals("p",nesting.promote(layerP))
        val after=snapshot()
        assertEquals(before.managementContexts,after.managementContexts)
        assertEquals(before.projectCharters,after.projectCharters)
        assertEquals(before.managementObjectives,after.managementObjectives)
        assertEquals(before.acceptanceCriteria,after.acceptanceCriteria)
    }
    @Test fun conversionFailureRollsBackPlanningAndPhysicalCollisionsDoNotMergeContexts()=runBlocking {
        val p=activate("p");activate("q");populate(p);val before=snapshot()
        assertTrue(runCatching {ProjectNestingRepository(db) {error("injected")}.move("p","q",null)}.isFailure)
        assertEquals(before,snapshot())
        assertTrue(runCatching {ProjectNestingRepository(db) {throw kotlinx.coroutines.CancellationException("injected")}.move("p","q",null)}.isFailure)
        assertEquals(before,snapshot())
        val nodes=NodeRepository(db);val existing=nodes.createNode("q",null,"Existing",purpose=NodePurpose.LAYER)
        db.nodeDao().insert(db.nodeDao().getById(existing.id)!!.copy(id="p"))
        val nesting=ProjectNestingRepository(db);val layer=nesting.move("p","q",null)
        assertNotEquals("p",layer)
        db.projectDao().insert(ProjectEntity("p","Other project","",0,1,2));val other=activate("p");populate(other)
        val promoted=nesting.promote(layer);assertNotEquals("p",promoted)
        assertEquals(p,db.conversionDao().project(promoted)!!.id)
        assertEquals(setOf(p,other),db.planningDao().charters().map {it.contextId}.toSet())
        assertEquals(before.acceptanceCriteria,db.planningDao().criteria().filter {row->row.objectiveId in before.managementObjectives.map {it.id}})
    }
    @Test fun backup22RoundTripAndHistorical21AndFailedRestoreAreSafe()=runBlocking {
        val id=activate("p");val old=snapshot().copy(managementLayers=emptyList());val oldBytes=BackupJson.encode(old)
        assertEquals(21,org.json.JSONObject(String(oldBytes)).getInt("dataVersion"))
        populate(id);val data=snapshot();val bytes=BackupJson.encode(data)
        assertEquals(29,org.json.JSONObject(String(bytes)).getInt("dataVersion"));assertEquals(data,BackupJson.decode(bytes))
        backup().restore(BackupJson.decode(bytes));assertEquals(data,snapshot())
        val criterion=data.acceptanceCriteria.first();val goal=data.managementObjectives.first()
        val bad=listOf(
            data.copy(projectCharters=listOf(data.projectCharters.first().copy(contextId="absent"))),
            data.copy(managementObjectives=listOf(goal.copy(contextId="absent"))),
            data.copy(managementObjectives=listOf(goal,goal.copy(id="duplicatePosition"))),
            data.copy(acceptanceCriteria=listOf(criterion.copy(objectiveId="absent"))),
            data.copy(acceptanceCriteria=listOf(criterion,criterion)),
            data.copy(acceptanceCriteria=listOf(criterion.copy(validated=true,validatedAt=null))),
            data.copy(acceptanceCriteria=listOf(criterion.copy(validated=true,validatedAt=-1))))
        bad.forEach {assertTrue(runCatching {backup().restore(it)}.isFailure);assertEquals(data,snapshot())}
        db.openHelper.writableDatabase.execSQL("CREATE TRIGGER fail_planning BEFORE INSERT ON acceptance_criteria BEGIN SELECT RAISE(ABORT,'injected'); END")
        try {assertTrue(runCatching {backup().restore(data)}.isFailure);assertEquals(data,snapshot())}
        finally {db.openHelper.writableDatabase.execSQL("DROP TRIGGER fail_planning")}
        backup().restore(BackupJson.decode(oldBytes));assertEquals(old,snapshot())
        assertTrue(db.planningDao().objectives().isEmpty());assertTrue(db.planningDao().criteria().isEmpty())
    }
    @Test fun allHistoricalHeadersRestoreWithoutFabricatingPlanning()=runBlocking {
        val legacy=snapshot();val source=org.json.JSONObject(String(BackupJson.encode(legacy)))
        source.put("privatePreferences",org.json.JSONArray()).put("storeId",org.json.JSONObject.NULL).put("storeKind","primary")
        source.put("sprintLinks",org.json.JSONArray()).put("managementContexts",org.json.JSONArray())
        val introduced=mapOf(
            "recurrenceRules" to 2,"recurrenceOccurrences" to 2,"recurrenceAssignments" to 2,
            "tags" to 3,"nodeTags" to 3,"recurrenceTags" to 3,"nodeEvents" to 4,
            "creationDefaults" to 7,"defaultsTags" to 7,"defaultsPeople" to 7,
            "attachmentFiles" to 10,"nodeAttachments" to 10,"projectAttachments" to 10,
            "technologies" to 11,"nodeTechnologies" to 11,"projectTechnologies" to 11,"technologyIcons" to 11,
            "projectPhotos" to 13,"projectPhotoImages" to 13,
            "conversionRoots" to 14,"conversionPeople" to 14,"conversionTags" to 14,"conversionEvents" to 14,
            "conversionWorkStates" to 14,"nodeSortPreferences" to 14,"imageFiles" to 14,
            "gameSession" to 15,"metroPreferences" to 16,"metroJourneys" to 16,"savedTemplates" to 17,
            "privatePreferences" to 18,"storeId" to 18,"storeKind" to 18,"sprintLinks" to 20,"managementContexts" to 21)
        for(version in 1..21) {
            populate(activate("p"))
            val json=org.json.JSONObject(source.toString()).put("dataVersion",version)
            introduced.filterValues {it>version}.keys.forEach {json.remove(it)}
            val decoded=BackupJson.decode(json.toString().toByteArray());assertEquals(legacy,decoded)
            backup().restore(decoded)
            assertTrue(db.planningDao().charters().isEmpty());assertTrue(db.planningDao().objectives().isEmpty());assertTrue(db.planningDao().criteria().isEmpty())
            assertEquals(legacy.projects,db.backupDao().projects())
        }
    }
    @Test fun json22RejectsWrongTypesUnknownFieldsAndOverflowBeforeMutation()=runBlocking {
        populate(activate("p"));val data=snapshot();val bytes=BackupJson.encode(data)
        val changes=listOf<(org.json.JSONObject)->Unit>(
            {it.put("dataVersion",30)}, {it.remove("acceptanceCriteria")},
            {it.getJSONArray("projectCharters").getJSONObject(0).put("extra",true)},
            {it.getJSONArray("managementObjectives").getJSONObject(0).put("position",2147483648L)},
            {it.getJSONArray("acceptanceCriteria").getJSONObject(0).put("validated",1)},
            {it.getJSONArray("acceptanceCriteria").getJSONObject(0).put("validatedAt",1.5)})
        changes.forEach {change->
            val json=org.json.JSONObject(String(bytes));change(json)
            assertTrue(runCatching {BackupJson.decode(json.toString().toByteArray())}.isFailure)
            assertEquals(data,snapshot())
        }
    }
    @Test fun objectiveAndOwnerDeletionCascadeOnlyTheirOwnData()=runBlocking {
        val p=activate("p");val q=activate("q");val a=populate(p);val b=populate(q)
        val retained=db.planningDao().criteria().filter {it.objectiveId==b.id}
        val criterion=db.planningDao().criteria().first {it.objectiveId==a.id};planning.deleteCriterion(criterion.id)
        assertEquals(1,db.planningDao().criteria().count {it.objectiveId==a.id})
        planning.deleteObjective(a.id);assertFalse(db.planningDao().criteria().any {it.objectiveId==a.id})
        assertEquals(retained,db.planningDao().criteria());assertNotNull(db.planningDao().charters().first {it.contextId==p})
        ProjectRepository(db.projectDao(),db).deleteProject("p")
        assertEquals(listOf(q),db.planningDao().charters().map {it.contextId});assertEquals(retained,db.planningDao().criteria())
    }
    @Test fun sprintWorkAndCompletionAreIndependentAndDatabasesAreIsolated()=runBlocking {
        val nodes=NodeRepository(db);val sprint=nodes.createNode("p",null,"Sprint",purpose=NodePurpose.LAYER);nodes.setSprintMode(sprint.id,true)
        val task=nodes.createNode("p",null,"Task");nodes.sprintLinks.link(sprint.id,setOf(task.id));nodes.setWorkState(task.id,WorkState.VALIDATED)
        val before=db.backupDao().nodes();val links=db.sprintLinkDao().all();val p=activate("p");val goal=populate(p)
        assertEquals(ObjectiveAcceptance.PENDING,objectiveAcceptance(db.planningDao().criteria()))
        db.planningDao().criteria().forEach {planning.validateCriterion(it.id,true)}
        for(old in before) {val current=db.nodeDao().getById(old.id)!!;assertEquals(old,if(old.sprintMode) current.copy(parentId=old.parentId,position=old.position,updatedAt=old.updatedAt) else current)};assertEquals(links,db.sprintLinkDao().all())
        val other=androidx.room.Room.inMemoryDatabaseBuilder(context,Arachn0deDatabase::class.java).build()
        try {
            assertTrue(other.planningDao().objectives().isEmpty());other.projectDao().insert(db.projectDao().getById("p")!!)
            val otherId=ManagementRepository(other).configure("p",null,true,ManagementMethodology.AGILE)!!.id
            PlanningRepository(other).createObjective(otherId,"Independent")
            assertEquals(goal.id,db.planningDao().objectives().single().id)
            assertNotEquals(goal.id,other.planningDao().objectives().single().id)
            BackupRepository(other,context,BackupAvatarFiles(context,BackupFixture::syncDirectory)).restore(BackupJson.decode(BackupJson.encode(snapshot())))
            assertEquals(db.planningDao().charters(),other.planningDao().charters())
            assertEquals(db.planningDao().objectives(),other.planningDao().objectives())
            assertEquals(db.planningDao().criteria(),other.planningDao().criteria())
        } finally {other.close()}
    }
}
