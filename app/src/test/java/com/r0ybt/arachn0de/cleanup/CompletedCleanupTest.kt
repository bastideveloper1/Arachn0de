package com.r0ybt.arachn0de.cleanup

import android.app.Application
import android.app.job.JobScheduler
import android.net.Uri
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
import java.io.File
import java.io.ByteArrayOutputStream

@RunWith(RobolectricTestRunner::class) @Config(application=Application::class,sdk=[28])
class CompletedCleanupTest {
    private val context get()=ApplicationProvider.getApplicationContext<Application>()
    private lateinit var db:Arachn0deDatabase
    private lateinit var nodes:NodeRepository
    private lateinit var attachments:AttachmentRepository
    private lateinit var cleanup:CompletedCleanup
    private var now=1_000_000L
    private fun bind() {
        nodes=NodeRepository(db) {now}
        attachments=AttachmentRepository(db,AttachmentStore(context,BackupFixture::syncDirectory))
        cleanup=CompletedCleanup(db,nodes,attachments)
    }
    @Before fun setup()=runBlocking {
        context.deleteDatabase("arachn0de.db");db=Arachn0deDatabase.create(context);bind()
        db.projectDao().insert(ProjectEntity("p","P","",0,1,1));db.projectDao().insert(ProjectEntity("q","Q","",1,1,1))
    }
    @After fun close() {db.close();CleanupSchedule.update(context,0)}
    private suspend fun task(title:String="Task",parent:String?=null,project:String="p",complete:Boolean=true):Node {
        val n=nodes.createNode(project,parent,title);if(complete) nodes.setCompleted(n.id,true);return n
    }
    @Test fun manualScopePreservesOtherBranchesContainersNotesPendingAndRestricted()=runBlocking {
        val layer=nodes.createNode("p",null,"Layer",purpose=NodePurpose.LAYER)
        val nested=nodes.createNode("p",layer.id,"Nested",purpose=NodePurpose.LAYER)
        val a=task(parent=layer.id);val b=task(parent=nested.id)
        val other=task();val outside=task(project="q")
        val pending=task(parent=layer.id,complete=false)
        val note=nodes.createNode("p",layer.id,"Note",purpose=NodePurpose.NOTE)
        val protected=task(parent=layer.id)
        val management=nodes.management.configure("p",null,true,ManagementMethodology.AGILE)!!
        db.backlogDao().save(BacklogEntryEntity(protected.id,management.id,0))
        val preview=cleanup.preview("p",layer.id)
        assertEquals(setOf(a.id,b.id),preview.candidates.map {it.node.id}.toSet());assertEquals(1,preview.excluded)
        assertNotNull(db.nodeDao().getById(a.id))
        assertEquals(2,cleanup.clean(preview))
        for(id in listOf(layer.id,nested.id,other.id,outside.id,pending.id,note.id,protected.id)) assertNotNull(db.nodeDao().getById(id))
        assertEquals(setOf(other.id),cleanup.preview("p").candidates.map {it.node.id}.toSet())
    }
    @Test fun confirmationCannotDeleteLateArrivalsMovedOrReopenedTasks()=runBlocking {
        val layer=nodes.createNode("p",null,"Layer",purpose=NodePurpose.LAYER)
        val a=task(parent=layer.id);val b=task(parent=layer.id);val c=task(parent=layer.id)
        val preview=cleanup.preview("p",layer.id)
        val late=task(parent=layer.id)
        now++;nodes.setCompleted(a.id,false)
        nodes.moveSelected("p",setOf(b.id),null)
        assertEquals(1,cleanup.clean(preview))
        assertNull(db.nodeDao().getById(c.id));for(id in listOf(a.id,b.id,late.id)) assertNotNull(db.nodeDao().getById(id))
        assertEquals(0,cleanup.clean(preview))
    }
    @Test fun automaticUsesLatestCompletionSkipsUnknownAndHonorsNeverAndChangedSetting()=runBlocking {
        val old=task();val reopened=task();val unknown=task()
        db.openHelper.writableDatabase.execSQL("DELETE FROM node_events WHERE nodeId=?",arrayOf(unknown.id))
        val day=86_400_000L
        now+=2*day
        nodes.setCompleted(reopened.id,false);nodes.setCompleted(reopened.id,true)
        assertEquals(0,cleanup.automatic(now))
        cleanup.setDays(1)
        assertEquals(listOf(old.id),cleanup.preview(days=1,now=now).candidates.map {it.node.id})
        val preview=cleanup.preview(days=1,now=now)
        cleanup.setDays(0);assertEquals(0,cleanup.clean(preview,now))
        cleanup.setDays(1);assertEquals(1,cleanup.automatic(now))
        assertNotNull(db.nodeDao().getById(unknown.id));assertNotNull(db.nodeDao().getById(reopened.id))
        now+=day;assertEquals(1,cleanup.automatic(now))
    }
    @Test fun settingsAllSevenRoundTripRestoreAndRejectInvalidWithoutReplacingData()=runBlocking {
        assertEquals(0,cleanup.days())
        for(value in CleanupPreference.options.keys) {
            cleanup.setDays(value)
            db.close();db=Arachn0deDatabase.create(context);bind();assertEquals(value,cleanup.days())
        }
        cleanup.setDays(365)
        val backup=BackupRepository(db,context,BackupAvatarFiles(context,BackupFixture::syncDirectory))
        val data=backup.snapshot()
        val restored=backup.inspect(ByteArrayOutputStream().also {BackupContainer.writeBackup(data,it)}.toByteArray().inputStream())
        cleanup.setDays(0);backup.restore(restored);assertEquals(365,cleanup.days())
        val invalid=data.copy(privatePreferences=data.privatePreferences.map {if(it.name==CleanupPreference.NAME) it.copy(payload=com.r0ybt.arachn0de.security.EncryptedPreferences.encode(mapOf("version" to 1,"days" to 2))) else it})
        assertTrue(runCatching {backup.restore(invalid)}.isFailure);assertEquals(365,cleanup.days())
        backup.restore(data.copy(privatePreferences=data.privatePreferences.filterNot {it.name==CleanupPreference.NAME}));assertEquals(0,cleanup.days())
    }
    @Test fun deferredScheduleDoesNotDeleteImmediatelyAndNeverCancelsIt()=runBlocking {
        val n=task();cleanup.setDays(1);CleanupSchedule.update(context,1)
        val scheduler=context.getSystemService(JobScheduler::class.java)
        val job=scheduler.getPendingJob(CleanupSchedule.ID)!!
        assertTrue(job.isPeriodic);assertEquals(6*60*60*1000L,job.intervalMillis)
        assertNotNull(db.nodeDao().getById(n.id))
        cleanup.setDays(0);CleanupSchedule.update(context,0)
        assertNull(scheduler.getPendingJob(CleanupSchedule.ID));assertEquals(0,cleanup.automatic(now+10*86_400_000L))
    }
    @Test fun cleanupPreservesRecurrenceReceiptsPeopleAndSharedAttachments()=runBlocking {
        val a=task(complete=false);val b=task(complete=false)
        val source=File(context.cacheDir,"cleanup.png").apply {writeBytes(BackupFixture.png())}
        val file=attachments.import(Uri.fromFile(source),"cleanup")
        attachments.associateNode(a.id,file.id);attachments.associateNode(b.id,file.id)
        db.personDao().insert(PersonEntity("person","Ana",null))
        db.personDao().assign(listOf(NodePersonEntity(a.id,"person")))
        val rule=RecurrenceRuleEntity("rule","p",null,"Rule","",null,null,0,"DAILY",1,null,0,"ACTIVE","UTC",0,null)
        db.recurrenceDao().insert(rule);db.recurrenceDao().record(RecurrenceOccurrenceEntity("rule",0,a.id))
        nodes.setCompleted(a.id,true)
        assertEquals(1,cleanup.clean(cleanup.preview("p")))
        assertNotNull(db.personDao().get("person"));assertEquals(a.id,db.recurrenceDao().occurrences().single().nodeId)
        assertNotNull(db.attachmentDao().get(file.id));assertTrue(File(context.filesDir,"attachments/${file.storageName}").exists())
        nodes.setCompleted(b.id,true);assertEquals(1,cleanup.clean(cleanup.preview("p")))
        assertNull(db.attachmentDao().get(file.id));assertFalse(File(context.filesDir,"attachments/${file.storageName}").exists())
    }
    @Test fun newRestrictionsAndPreDeleteFailureCannotRemoveConfirmedTasks()=runBlocking {
        val a=task();val b=task()
        val preview=cleanup.preview("p")
        val management=nodes.management.configure("p",null,true,ManagementMethodology.AGILE)!!
        db.backlogDao().save(BacklogEntryEntity(a.id,management.id,0))
        nodes.beforeDelete={error("Simulated pre-delete failure")}
        assertTrue(runCatching {cleanup.clean(preview)}.isFailure)
        assertNotNull(db.nodeDao().getById(a.id));assertNotNull(db.nodeDao().getById(b.id))
        nodes.beforeDelete={}
        assertEquals(1,cleanup.clean(preview))
        assertNotNull(db.nodeDao().getById(a.id));assertNull(db.nodeDao().getById(b.id))
    }

    @Test fun sprintLinksUseExistingDeletionAndDoNotExpandPhysicalScope()=runBlocking {
        val sprint=nodes.createNode("p",null,"Sprint",purpose=NodePurpose.LAYER)
        nodes.setSprintMode(sprint.id,true)
        val layer=nodes.createNode("p",null,"Branch",purpose=NodePurpose.LAYER)
        val external=task(parent=layer.id)
        SprintLinkRepository(db).link(sprint.id,setOf(external.id))
        assertTrue(cleanup.preview("p",sprint.id).candidates.isEmpty())
        assertEquals(1,cleanup.clean(cleanup.preview("p",layer.id)))
        assertNull(db.sprintLinkDao().task(external.id))
        assertNotNull(db.nodeDao().getById(sprint.id));assertNotNull(db.nodeDao().getById(layer.id))
    }
    @Test fun deletionFailureRollsBackTheWholeConfirmedBatch()=runBlocking {
        val a=task();val b=task()
        val preview=cleanup.preview("p")
        db.openHelper.writableDatabase.execSQL("CREATE TRIGGER cleanup_failure BEFORE DELETE ON nodes WHEN OLD.id='${b.id}' BEGIN SELECT RAISE(ABORT,'simulated'); END")
        try {
            assertTrue(runCatching {cleanup.clean(preview)}.isFailure)
            assertNotNull(db.nodeDao().getById(a.id));assertNotNull(db.nodeDao().getById(b.id))
        } finally {db.openHelper.writableDatabase.execSQL("DROP TRIGGER cleanup_failure")}
        assertEquals(2,cleanup.clean(preview))
    }

}
