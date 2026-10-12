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
class ManagementCascadeTest {
    private val app get()=ApplicationProvider.getApplicationContext<Application>()
    private lateinit var db:Arachn0deDatabase
    @Before fun setup()=runBlocking {app.deleteDatabase("arachn0de.db");db=Arachn0deDatabase.create(app);db.projectDao().insert(ProjectEntity("p","Project","Original",0,1,2))}
    @After fun close() {db.close()}
    private suspend fun configure()=ManagementRepository(db).configure("p",null,true,ManagementMethodology.AGILE)!!.id
    private fun backup()=BackupRepository(db,app,BackupAvatarFiles(app,BackupFixture::syncDirectory))
    private suspend fun legacySections(context:String) {
        val acta=db.managementLayerDao().all().first {it.contextId==context && it.kind=="ACTA"}.nodeId
        ManagementLayerKind.entries.filter {it.purpose=="NOTE"}.forEach {kind->
            val note=NodeRepository(db).createNode("p",acta,kind.title,"Historical "+kind.title,purpose=NodePurpose.NOTE)
            db.managementLayerDao().insert(ManagementLayerEntity(note.id,context,kind.name))
        }
    }
    @Test fun realLayersIdempotentRenamingAndMethodologyPreserveContent()=runBlocking {
        val personal=NodeRepository(db).createNode("p",null,"Personal",purpose=NodePurpose.LAYER)
        val id=configure();legacySections(id);val before=db.managementLayerDao().all();assertEquals(12,before.size)
        val acta=before.first {it.kind=="ACTA"};val purpose=before.first {it.kind=="PURPOSE"}
        assertEquals(acta.nodeId,db.nodeDao().getById(purpose.nodeId)!!.parentId)
        db.nodeDao().updateContent(purpose.nodeId,"Renamed","Edited content",3)
        repeat(3) {ManagementCascadeRepository(db).ensure(id)}
        assertEquals(before,db.managementLayerDao().all());assertEquals("Edited content",db.nodeDao().getById(purpose.nodeId)!!.description)
        assertEquals(0,db.nodeDao().getById(personal.id)!!.position)
        val repo=ManagementRepository(db);repo.configure("p",null,false,ManagementMethodology.TRADITIONAL)
        assertEquals(before,db.managementLayerDao().all());repo.configure("p",null,true,ManagementMethodology.HYBRID);assertEquals(before,db.managementLayerDao().all())
    }
    @Test fun backlogAndKanbanOpenSameStoryWithDeepNotesAndTasks()=runBlocking {
        val id=configure();val repo=ManagementRepository(db);val backlog=db.managementLayerDao().all().first {it.kind=="BACKLOG"}.nodeId
        val epic=repo.cascade.create(id,backlog,"Epic",LayerRole.EPIC)
        val story=repo.cascade.create(id,epic,"Story",LayerRole.STORY)
        val nodes=NodeRepository(db);val layer=nodes.createNode("p",story,"Tests",purpose=NodePurpose.LAYER)
        val task=nodes.createNode("p",layer.id,"Task");val note=nodes.createNode("p",story,"Note from Kanban",purpose=NodePurpose.NOTE)
        assertEquals(story,db.nodeDao().getById(note.id)!!.parentId)
        nodes.updateNode(note.id,"Edited from Backlog","New content")
        assertEquals("New content",nodes.getNode(note.id)!!.description)
        assertEquals(layer.id,nodes.getNode(task.id)!!.parentId)
        assertEquals(listOf(EpicStoryEntity(epic,story)),db.backlogDao().epicStories())
        assertEquals(1,db.backupDao().nodes().count {it.id==story})
    }
    @Test fun backup29RestoresIdentityHierarchyAndRejectsInvalidMappingBeforeReplacement()=runBlocking {
        configure();val data=backup().snapshot();val bytes=BackupJson.encode(data)
        assertEquals(29,org.json.JSONObject(String(bytes)).getInt("dataVersion"))
        val restored=BackupJson.decode(bytes);assertEquals(data.managementLayers,restored.managementLayers)
        backup().restore(restored);assertEquals(data.nodes,db.backupDao().nodes())
        val invalid=restored.copy(managementLayers=restored.managementLayers.map {it.copy(contextId="absent")})
        assertTrue(runCatching {backup().restore(invalid)}.isFailure);assertEquals(data.nodes,db.backupDao().nodes())
    }
    @Test fun oldBackupCanBeInitializedWithoutLosingLegacyContent()=runBlocking {
        val id=configure();val charter=ProjectCharterEntity(id,purpose="Historical content")
        val original=backup().snapshot().copy(managementLayers=emptyList(),projectCharters=listOf(charter),nodes=emptyList())
        backup().restore(BackupJson.decode(BackupJson.encode(original)))
        ManagementCascadeRepository(db).ensure(id)
        val purpose=db.managementLayerDao().all().first {it.kind=="PURPOSE"}
        assertEquals("Historical content",db.nodeDao().getById(purpose.nodeId)!!.description)
        assertEquals(listOf(charter),db.planningDao().charters())
    }
    @Test fun explicitNodeEditsKeepHistoricalTextAdaptersAndCriteriaCoherent()=runBlocking {
        val nodes=NodeRepository(db)
        val id=nodes.management.configure("p",null,true,ManagementMethodology.AGILE)!!.id
        val goal=nodes.management.planning.createObjective(id,"Old goal")
        nodes.management.planning.createCriterion(goal.id,"Criterion")
        nodes.updateNode(goal.id,"Edited objective","Edited description")
        assertEquals("Edited objective",db.planningDao().objective(goal.id)!!.title)
        assertEquals(1,db.planningDao().criteria().size)
        legacySections(id)
        val scope=db.managementLayerDao().all().first {it.contextId==id && it.kind=="SCOPE"}
        nodes.updateNode(scope.nodeId,"Renamed scope","Canonical scope")
        assertEquals("Canonical scope",db.planningDao().charters().single().scope)
    }

    @Test fun optionalSectionsPersistReopenAndRoundTripWithoutNewFormat()=runBlocking {
        val context=configure();val cascade=ManagementCascadeRepository(db)
        assertEquals(essentialManagementKinds,db.managementLayerDao().all().map {it.kind}.toSet())
        val note=cascade.createSection(context,"Custom section","Original content")
        assertEquals("NOTE",db.nodeDao().getById(note)!!.purpose)
        NodeRepository(db).updateNode(note,"Renamed section","Edited content")
        val second=cascade.createSection(context,"Second section","")
        val parent=db.nodeDao().getById(note)!!.parentId
        assertTrue(NodeRepository(db).reorderNode(second,parent,true))
        assertTrue(db.nodeDao().getById(second)!!.position<db.nodeDao().getById(note)!!.position)

        db.close();db=Arachn0deDatabase.create(app)
        assertEquals("Edited content",db.nodeDao().getById(note)!!.description)
        val snapshot=backup().snapshot();val restored=BackupJson.decode(BackupJson.encode(snapshot))
        backup().restore(restored)
        assertEquals(snapshot.nodes,db.backupDao().nodes())
        ManagementCascadeRepository(db).ensure(context)
        assertEquals(1,db.backupDao().nodes().count {it.id==note})
        assertEquals(6,db.managementLayerDao().all().size)
        val before=db.backupDao().nodes()
        val essential=restored.managementLayers.first {it.kind=="TEAM"}
        assertTrue(runCatching {backup().restore(restored.copy(managementLayers=restored.managementLayers-essential))}.isFailure)
        assertEquals(before,db.backupDao().nodes())
    }
    @Test fun legacySectionsArePreservedAndExplicitDeletionIsNotUndoneByEnsureOrRestore()=runBlocking {
        val context=configure();legacySections(context)
        val before=backup().snapshot()
        repeat(2) {ManagementCascadeRepository(db).ensure(context)}
        assertEquals(before.nodes,db.backupDao().nodes())
        backup().restore(BackupJson.decode(BackupJson.encode(before)))
        assertEquals(before.nodes,db.backupDao().nodes())
        val scope=db.managementLayerDao().all().first {it.kind=="SCOPE"}
        assertTrue(NodeRepository(db).deleteNode(scope.nodeId))
        ManagementCascadeRepository(db).ensure(context)
        assertNull(db.nodeDao().getById(scope.nodeId))
        assertFalse(db.managementLayerDao().all().any {it.kind=="SCOPE"})
        val after=backup().snapshot();backup().restore(BackupJson.decode(BackupJson.encode(after)))
        ManagementCascadeRepository(db).ensure(context)
        assertEquals(after.nodes,db.backupDao().nodes())
        assertTrue(runCatching {NodeRepository(db).deleteNode(db.managementLayerDao().all().first {it.kind=="TEAM"}.nodeId)}.isFailure)
    }

}
