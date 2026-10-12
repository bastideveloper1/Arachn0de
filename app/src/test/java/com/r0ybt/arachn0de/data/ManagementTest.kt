package com.r0ybt.arachn0de.data

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.r0ybt.arachn0de.data.local.*
import com.r0ybt.arachn0de.data.repository.*
import com.r0ybt.arachn0de.domain.model.*
import com.r0ybt.arachn0de.backup.*
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class) @Config(sdk=[28],application=Application::class)
class ManagementTest {
    private val context get()=ApplicationProvider.getApplicationContext<Application>()
    private lateinit var db:Arachn0deDatabase
    private lateinit var nodes:NodeRepository
    private lateinit var management:ManagementRepository
    @Before fun setup()=runBlocking {
        context.deleteDatabase("arachn0de.db");open()
        for(id in listOf("p","q","r")) db.projectDao().insert(ProjectEntity(id,id,"Description $id",0,1,2))
    }
    private fun open() {db=Arachn0deDatabase.create(context);nodes=NodeRepository(db);management=ManagementRepository(db)}
    @After fun close() {db.close()}
    private suspend fun configure(id:String,enabled:Boolean=true,method:ManagementMethodology=ManagementMethodology.AGILE)=management.configure(id,null,enabled,method)!!
    private fun assertOriginalWork(before:List<NodeEntity>,after:List<NodeEntity>) {
        for(old in before) {
            val current=after.single {it.id==old.id}
            assertEquals(old,if(old.sprintMode) current.copy(parentId=old.parentId,position=old.position,updatedAt=old.updatedAt) else current)
        }
        assertEquals(6,db.managementLayerDao().let {runBlocking {it.all()}}.size)
    }
    @Test fun normalIsDefaultAndActivationConfigurationAndReactivationKeepIdentityAndTree()=runBlocking {
        val task=nodes.createNode("p",null,"Task")
        val before=db.backupDao().nodes();val projects=db.backupDao().projects()
        assertTrue(db.managementContextDao().all().isEmpty())
        assertNull(management.configure("p",null,false,ManagementMethodology.AGILE));assertTrue(db.conversionDao().roots().isEmpty())
        val initial=configure("p")
        for(method in ManagementMethodology.entries) assertEquals(initial.id,configure("p",true,method).id)
        val disabled=configure("p",false,ManagementMethodology.HYBRID)
        assertFalse(disabled.enabled);assertEquals(initial.id,disabled.id)
        db.close();open();assertEquals(listOf(disabled),db.managementContextDao().all())
        assertEquals(initial.id,configure("p",true,ManagementMethodology.HYBRID).id)
        assertOriginalWork(before,db.backupDao().nodes());assertEquals(projects,db.backupDao().projects());assertEquals(task.id,nodes.getNode(task.id)!!.id)
        assertTrue(runCatching {db.managementContextDao().save(initial.copy(methodology="UNKNOWN"))}.isFailure)
    }
    @Test fun nestedConversionsKeepIndependentContextsAndDisabledConfiguration()=runBlocking {
        configure("p");val p=configure("p",false);val q=configure("q",true,ManagementMethodology.TRADITIONAL);val r=configure("r",true,ManagementMethodology.HYBRID)
        nodes.createNode("p",null,"Child")
        val nesting=ProjectNestingRepository(db)
        val layerP=nesting.move("p","q",null)
        assertEquals(p,db.managementContextDao().all().first {it.id==p.id})
        assertEquals(p.id,db.conversionDao().node(layerP)!!.id)
        val layerQ=nesting.move("q","r",null)
        assertEquals(setOf(p,q,r),db.managementContextDao().all().toSet())
        val repo=BackupRepository(db,context,BackupAvatarFiles(context,BackupFixture::syncDirectory))
        repo.restore(BackupJson.decode(BackupJson.encode(repo.snapshot())))
        assertEquals(setOf(p,q,r),db.managementContextDao().all().toSet())
        assertEquals("q",nesting.promote(layerQ));assertEquals("p",nesting.promote(layerP))
        assertEquals(setOf(p,q,r),db.managementContextDao().all().toSet())
        assertEquals(p.id,db.conversionDao().project("p")!!.id)
        assertEquals("Description p",db.projectDao().getById("p")!!.description)
    }
    @Test fun physicalIdentityCollisionsDoNotChangeOrMergeManagementIdentity()=runBlocking {
        val p=configure("p");val q=configure("q")
        val existing=nodes.createNode("q",null,"Existing",purpose=NodePurpose.LAYER)
        db.nodeDao().insert(db.nodeDao().getById(existing.id)!!.copy(id="p"))
        val nesting=ProjectNestingRepository(db);val layer=nesting.move("p","q",null)
        assertNotEquals("p",layer);assertEquals(p.id,db.conversionDao().node(layer)!!.id)
        db.projectDao().insert(ProjectEntity("p","Unrelated","",0,1,2))
        val promoted=nesting.promote(layer)
        assertNotEquals("p",promoted);assertEquals(p.id,db.conversionDao().project(promoted)!!.id)
        assertEquals(setOf(p,q),db.managementContextDao().all().toSet())
        assertEquals("Unrelated",db.projectDao().getById("p")!!.name)
    }
    @Test fun failedConversionRollsBackContextsAndExplicitOwnerDeletionCleansOnlyItsContext()=runBlocking {
        val p=configure("p");val q=configure("q")
        assertTrue(runCatching {ProjectNestingRepository(db) {error("injected")}.move("p","q",null)}.isFailure)
        assertEquals(setOf(p,q),db.managementContextDao().all().toSet());assertNotNull(db.projectDao().getById("p"))
        val layer=ProjectNestingRepository(db).move("p","q",null)
        management.configure(null,layer,false,ManagementMethodology.HYBRID)
        assertEquals(p.id,db.managementContextDao().all().first {it.id==p.id}.id)
        nodes.deleteNode(layer);assertEquals(listOf(q),db.managementContextDao().all())
        ProjectRepository(db.projectDao(),db).deleteProject("q");assertTrue(db.managementContextDao().all().isEmpty())
    }
    @Test fun backup21RestoresDisabledContextsAndRejectsOrphansAndFailedRestoreWithoutLoss()=runBlocking {
        configure("p");val p=configure("p",false,ManagementMethodology.HYBRID)
        val repo=BackupRepository(db,context,BackupAvatarFiles(context,BackupFixture::syncDirectory))
        val data=repo.snapshot();val bytes=BackupJson.encode(data)
        assertEquals(29,org.json.JSONObject(String(bytes)).getInt("dataVersion"))
        assertEquals(data,BackupJson.decode(bytes))
        configure("p",true);repo.restore(BackupJson.decode(bytes));assertEquals(listOf(p),db.managementContextDao().all())
        for(bad in listOf(data.copy(managementContexts=listOf(p.copy(id="absent"))),data.copy(managementContexts=listOf(p,p)),data.copy(managementContexts=listOf(p.copy(methodology="INVALID"))))) {
            assertTrue(runCatching {repo.restore(bad)}.isFailure);assertEquals(listOf(p),db.managementContextDao().all())
        }
        db.openHelper.writableDatabase.execSQL("CREATE TRIGGER fail_management BEFORE INSERT ON management_contexts BEGIN SELECT RAISE(ABORT,'injected'); END")
        try {assertTrue(runCatching {repo.restore(data)}.isFailure);assertEquals(listOf(p),db.managementContextDao().all())}
        finally {db.openHelper.writableDatabase.execSQL("DROP TRIGGER fail_management")}
        val old=BackupFixture.empty();assertTrue(BackupJson.decode(BackupJson.encode(old)).managementContexts.isEmpty())
        repo.restore(old);assertTrue(db.managementContextDao().all().isEmpty())
    }
    @Test fun sprintLinksAndPhasesStayIntactAndSessionsStayIsolated()=runBlocking {
        val sprint=nodes.createNode("p",null,"Sprint",purpose=NodePurpose.LAYER);nodes.setSprintMode(sprint.id,true)
        val task=nodes.createNode("p",null,"Task");nodes.sprintLinks.link(sprint.id,setOf(task.id));nodes.setWorkState(task.id,WorkState.VALIDATED)
        val before=db.backupDao().nodes();val links=db.sprintLinkDao().all()
        configure("p");configure("p",false,ManagementMethodology.TRADITIONAL)
        assertOriginalWork(before,db.backupDao().nodes());assertEquals(links,db.sprintLinkDao().all())
        val nesting=ProjectNestingRepository(db);val layer=nesting.move("p","q",null)
        assertEquals(WorkState.VALIDATED,nodes.getNode(task.id)!!.workState)
        assertEquals(links,db.sprintLinkDao().all())
        assertEquals("p",nesting.promote(layer));assertOriginalWork(before,db.backupDao().nodes())
        assertEquals(links,db.sprintLinkDao().all())
        val other=androidx.room.Room.inMemoryDatabaseBuilder(context,Arachn0deDatabase::class.java).build()
        try {
            other.projectDao().insert(db.projectDao().getById("p")!!)
            val secondary=ManagementRepository(other);assertTrue(other.managementContextDao().all().isEmpty())
            secondary.configure("p",null,true,ManagementMethodology.HYBRID)
            assertFalse(db.managementContextDao().all().single().enabled)
            assertTrue(other.managementContextDao().all().single().enabled)
        } finally {other.close()}
    }
}
