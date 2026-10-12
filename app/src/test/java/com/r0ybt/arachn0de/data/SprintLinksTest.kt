package com.r0ybt.arachn0de.data

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.r0ybt.arachn0de.data.local.*
import com.r0ybt.arachn0de.data.repository.*
import com.r0ybt.arachn0de.domain.model.*
import com.r0ybt.arachn0de.backup.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.cancel
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28],application=Application::class)
class SprintLinksTest {
    private val context get()=ApplicationProvider.getApplicationContext<Application>()
    private lateinit var db:Arachn0deDatabase
    private lateinit var nodes:NodeRepository
    @Before fun setup()=runBlocking {context.deleteDatabase("arachn0de.db");open();db.projectDao().insert(ProjectEntity("p","P","",0,1,1));db.projectDao().insert(ProjectEntity("q","Q","",1,1,1))}
    private fun open() {db=Arachn0deDatabase.create(context);nodes=NodeRepository(db)}
    @After fun close() {db.close()}
    private suspend fun sprint(name:String="Sprint"):Node {val n=nodes.createNode("p",null,name,purpose=NodePurpose.LAYER);nodes.setSprintMode(n.id,true);return nodes.getNode(n.id)!!}
    @Test fun linkPreservesTreeAndAssociationsAndUnlinkRequiresAcknowledgingCurrentPhase()=runBlocking {
        val s=sprint();val origin=nodes.createNode("p",null,"Origin",purpose=NodePurpose.LAYER)
        val t=nodes.createNode("p",origin.id,"Task",description="Full\ntext")
        db.personDao().insert(PersonEntity("person","Roy",null));db.personDao().assign(listOf(NodePersonEntity(t.id,"person")))
        val before=db.nodeDao().getById(t.id)!!
        nodes.sprintLinks.link(s.id,setOf(t.id));val linked=db.nodeDao().getById(t.id)!!
        assertEquals(before.copy(workState=linked.workState,updatedAt=linked.updatedAt),linked)
        assertEquals(3,db.nodeDao().getProjectNodes("p").size)
        assertEquals(listOf(NodePersonEntity(t.id,"person")),db.backupDao().assignments())
        assertTrue(nodes.setWorkState(t.id,WorkState.DOING));assertFalse(nodes.getNode(t.id)!!.isCompleted)
        assertTrue(runCatching {nodes.sprintLinks.unlink(s.id,t.id,WorkState.UNPLANNED)}.isFailure)
        assertEquals(WorkState.DOING,nodes.getNode(t.id)!!.workState)
        nodes.sprintLinks.unlink(s.id,t.id,WorkState.DOING)
        assertEquals(origin.id,nodes.getNode(t.id)!!.parentId);assertNull(nodes.getNode(t.id)!!.workState)
        assertFalse(nodes.getNode(t.id)!!.isCompleted);assertEquals("Full\ntext",nodes.getNode(t.id)!!.description)
    }
    @Test fun exclusivityAtomicSelectionAndDirectMembershipNeverDuplicate()=runBlocking {
        val s=sprint();val other=sprint("Other")
        val t=nodes.createNode("p",null,"External");val direct=nodes.createNode("p",s.id,"Direct");val foreign=nodes.createNode("q",null,"Foreign")
        nodes.sprintLinks.link(s.id,setOf(direct.id));assertTrue(db.sprintLinkDao().all().isEmpty())
        assertTrue(runCatching {nodes.sprintLinks.link(other.id,setOf(direct.id))}.isFailure)
        assertTrue(runCatching {nodes.sprintLinks.link(s.id,linkedSetOf(t.id,foreign.id))}.isFailure)
        assertNull(nodes.getNode(t.id)!!.workState);assertTrue(db.sprintLinkDao().all().isEmpty())
        nodes.sprintLinks.link(s.id,setOf(t.id));nodes.sprintLinks.link(s.id,setOf(t.id))
        assertEquals(1,db.sprintLinkDao().all().size)
        assertTrue(runCatching {nodes.sprintLinks.link(other.id,setOf(t.id))}.isFailure)
        assertTrue(runCatching {nodes.sprintLinks.link(s.id,setOf(s.id))}.isFailure)
        assertTrue(runCatching {nodes.sprintLinks.link(s.id,setOf("missing"))}.isFailure)
        assertTrue(runCatching {db.sprintLinkDao().insert(SprintLinkEntity(foreign.id,s.id))}.isFailure)
    }
    @Test fun uniqueProgressPhasesAndCompletionStayIndependentAcrossReopen()=runBlocking {
        val s=sprint();val a=nodes.createNode("p",s.id,"A");val b=nodes.createNode("p",null,"B");val c=nodes.createNode("p",null,"C")
        nodes.sprintLinks.link(s.id,setOf(b.id,c.id))
        nodes.setWorkState(a.id,WorkState.PLANNED);nodes.setWorkState(b.id,WorkState.DOING);nodes.setWorkState(c.id,WorkState.VALIDATED)
        val rows=nodes.getProjectNodes("p");assertEquals(58,com.r0ybt.arachn0de.ui.state.sprintProgressPercentage(rows+rows,s.id,setOf(a.id,b.id,c.id))!!.let {kotlin.math.round(it).toInt()})
        assertFalse(nodes.getNode(c.id)!!.isCompleted)
        nodes.setCompleted(c.id,true);nodes.setCompleted(c.id,false);assertEquals(WorkState.VALIDATED,nodes.getNode(c.id)!!.workState)
        nodes.confirmWorkState(b.id);assertEquals(WorkState.DONE,nodes.getNode(b.id)!!.workState);assertTrue(nodes.getNode(b.id)!!.isCompleted)
        val saved=db.sprintLinkDao().all();db.close();open();assertEquals(saved,db.sprintLinkDao().all());assertEquals(WorkState.DONE,nodes.getNode(b.id)!!.workState)
    }
    @Test fun movesConversionsDisablingAndDeletionAreSafe()=runBlocking {
        val s=sprint();val other=sprint("Other");val t=nodes.createNode("p",null,"T")
        nodes.sprintLinks.link(s.id,setOf(t.id));nodes.setWorkState(t.id,WorkState.DOING)
        val origin=nodes.createNode("p",null,"Origin",purpose=NodePurpose.LAYER)
        assertTrue(nodes.moveNode(t.id,origin.id));assertEquals(WorkState.DOING,nodes.getNode(t.id)!!.workState)
        val before=db.backupDao().nodes()
        assertTrue(runCatching {nodes.moveNode(t.id,other.id)}.isFailure)
        assertTrue(runCatching {nodes.setSprintMode(s.id,false)}.isFailure)
        assertTrue(runCatching {nodes.deleteNode(s.id)}.isFailure)
        assertTrue(runCatching {nodes.convertPurpose(t.id,NodePurpose.NOTE)}.isFailure)
        assertTrue(runCatching {ProjectRepository(db.projectDao(),db).convertLayerToProject(origin.id)}.isFailure)
        assertTrue(runCatching {ProjectRepository(db.projectDao(),db).moveInside("p","q",null)}.isFailure)
        assertEquals(before,db.backupDao().nodes());assertEquals(1,db.sprintLinkDao().all().size)
        nodes.moveNode(t.id,s.id);assertTrue(db.sprintLinkDao().all().isEmpty());assertEquals(WorkState.DOING,nodes.getNode(t.id)!!.workState)
        val external=nodes.createNode("p",null,"E");nodes.sprintLinks.link(s.id,setOf(external.id));nodes.deleteNode(external.id)
        assertTrue(db.sprintLinkDao().all().isEmpty());assertNotNull(nodes.getNode(t.id))
        assertTrue(nodes.deleteNode(s.id));assertNull(nodes.getNode(t.id))
        val remaining=nodes.createNode("p",origin.id,"Remaining")
        nodes.sprintLinks.link(other.id,setOf(remaining.id))
        assertTrue(runCatching {nodes.setSprintMode(origin.id,true)}.isFailure)
        assertEquals(1,db.sprintLinkDao().all().size)
        ProjectRepository(db.projectDao(),db).deleteProject("p")
        assertTrue(db.sprintLinkDao().all().isEmpty());assertTrue(nodes.getProjectNodes("p").isEmpty())
    }
    @Test fun separateVaultSessionsDoNotShareMembershipOrPhases()=runBlocking {
        val otherDb=androidx.room.Room.inMemoryDatabaseBuilder(context,Arachn0deDatabase::class.java).build()
        val root=java.io.File(context.filesDir,"sprint-isolation-${java.util.UUID.randomUUID()}")
        val primaryAccess=com.r0ybt.arachn0de.security.SecureFiles.Session(java.util.UUID.randomUUID(),java.io.File(root,"main"),ByteArray(32))
        val secondaryAccess=com.r0ybt.arachn0de.security.SecureFiles.Session(java.util.UUID.randomUUID(),java.io.File(root,"decoy"),ByteArray(32) {1})
        val primaryContext=com.r0ybt.arachn0de.security.VaultContext(context,primaryAccess).apply {database=db}
        val secondaryContext=com.r0ybt.arachn0de.security.VaultContext(context,secondaryAccess).apply {database=otherDb;primary=false}
        val primary=com.r0ybt.arachn0de.security.VaultSession(primaryAccess,primaryContext,db,true)
        val secondary=com.r0ybt.arachn0de.security.VaultSession(secondaryAccess,secondaryContext,otherDb,false)
        try {
            val s=sprint();val t=nodes.createNode("p",null,"T")
            otherDb.projectDao().insert(db.projectDao().getById("p")!!)
            otherDb.nodeDao().insert(db.nodeDao().getById(s.id)!!);otherDb.nodeDao().insert(db.nodeDao().getById(t.id)!!)
            primary.nodes.sprintLinks.link(s.id,setOf(t.id));primary.nodes.setWorkState(t.id,WorkState.DOING)
            assertTrue(otherDb.sprintLinkDao().all().isEmpty());assertNull(secondary.nodes.getNode(t.id)!!.workState)
            secondary.nodes.sprintLinks.link(s.id,setOf(t.id));secondary.nodes.setWorkState(t.id,WorkState.VALIDATED)
            assertEquals(WorkState.DOING,primary.nodes.getNode(t.id)!!.workState)
        } finally {primary.scope.cancel();secondary.close();primaryContext.clearMemory();primaryAccess.close();root.deleteRecursively()}
    }
    @Test fun json20RestoresAndInvalidReferencesRollbackWhileHistoricalBackupsStayReadable()=runBlocking {
        val s=sprint();val t=nodes.createNode("p",null,"T");nodes.sprintLinks.link(s.id,setOf(t.id));nodes.setWorkState(t.id,WorkState.PLANNED)
        val data=BackupFixture.empty().copy(projects=db.backupDao().projects(),nodes=db.backupDao().nodes(),sprintLinks=db.sprintLinkDao().all())
        val bytes=BackupJson.encode(data);assertEquals(20,org.json.JSONObject(String(bytes)).getInt("dataVersion"))
        assertEquals(data,BackupJson.decode(bytes))
        val repo=BackupRepository(db,context,BackupAvatarFiles(context,BackupFixture::syncDirectory))
        nodes.sprintLinks.unlink(s.id,t.id,WorkState.PLANNED);repo.restore(BackupJson.decode(bytes))
        assertEquals(data.sprintLinks,repo.snapshot().sprintLinks);assertEquals(WorkState.PLANNED,nodes.getNode(t.id)!!.workState)
        for(invalid in listOf(data.copy(sprintLinks=listOf(SprintLinkEntity("missing",s.id))),data.copy(sprintLinks=data.sprintLinks+data.sprintLinks),data.copy(sprintLinks=listOf(SprintLinkEntity(t.id,t.id))))) {
            assertTrue(runCatching {repo.restore(invalid)}.isFailure);assertEquals(data.sprintLinks,db.sprintLinkDao().all())
        }
        db.openHelper.writableDatabase.execSQL("CREATE TRIGGER fail_link_restore BEFORE INSERT ON sprint_links BEGIN SELECT RAISE(ABORT,'injected restore failure'); END")
        try {
            assertTrue(runCatching {repo.restore(data)}.isFailure)
            assertEquals(data.sprintLinks,db.sprintLinkDao().all())
            assertEquals(WorkState.PLANNED,nodes.getNode(t.id)!!.workState)
        } finally {db.openHelper.writableDatabase.execSQL("DROP TRIGGER fail_link_restore")}
        val old=BackupFixture.empty();assertTrue(BackupJson.decode(BackupJson.encode(old)).sprintLinks.isEmpty())
        repo.restore(old);assertTrue(db.sprintLinkDao().all().isEmpty())
    }
}
