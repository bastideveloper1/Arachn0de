package com.r0ybt.arachn0de.drafts

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import androidx.room.withTransaction
import com.r0ybt.arachn0de.access.*
import com.r0ybt.arachn0de.backup.*
import com.r0ybt.arachn0de.audio.AudioDraftStore
import com.r0ybt.arachn0de.cleanup.CompletedCleanup
import com.r0ybt.arachn0de.data.local.*
import com.r0ybt.arachn0de.data.repository.*
import com.r0ybt.arachn0de.domain.model.*
import com.r0ybt.arachn0de.security.SecureFiles
import com.r0ybt.arachn0de.sharing.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
@RunWith(RobolectricTestRunner::class) @Config(application=Application::class,sdk=[28])
class DraftsTest {
    private val context get()=ApplicationProvider.getApplicationContext<Application>()
    private lateinit var db:Arachn0deDatabase
    private lateinit var nodes:NodeRepository
    private lateinit var attachments:AttachmentRepository
    @Before fun setup() {
        context.deleteDatabase("arachn0de.db")
        listOf("audio-drafts","attachments").forEach {File(context.filesDir,it).deleteRecursively()}
        open()
    }
    private fun open() {
        db=Arachn0deDatabase.create(context);nodes=NodeRepository(db)
        attachments=AttachmentRepository(db,AttachmentStore(context,BackupFixture::syncDirectory))
    }
    @After fun close() {db.close()}
    private suspend fun project()=ProjectRepository(db.projectDao(),db).createProject("Destino")
    private fun backup()=BackupRepository(db,context,BackupAvatarFiles(context,BackupFixture::syncDirectory))
    private fun tone()=javaClass.classLoader!!.getResourceAsStream("audio/tone.aac")!!.use {it.readBytes()}
    private suspend fun audio(owner:String):AttachmentFileEntity {
        val store=AudioDraftStore(context,db,BackupFixture::syncDirectory);val id=store.begin(owner)
        store.append(id,0,tone());return checkNotNull(store.finish(id))
    }
    @Test fun noteTaskCaptureEditReopenAndStableRetriesWithoutPhantomProjects()=runBlocking<Unit> {
        val access=AccessRepository(db)
        val task=access.capture(null,"Captura","Texto íntegro","capture")
        val note=nodes.createNode(null,null,"Nota",purpose=NodePurpose.NOTE)
        assertEquals(task,access.capture(null,"Captura","Texto íntegro","capture"))
        assertTrue(runCatching {nodes.createNode(null,null," ")}.isFailure)
        assertTrue(runCatching {nodes.createNode(null,null,"Capa",purpose=NodePurpose.LAYER)}.isFailure)
        assertEquals(2,nodes.observeDrafts().first().size)
        assertTrue(db.backupDao().projects().isEmpty());assertTrue(nodes.observeAllState().first().nodes.isEmpty())
        assertTrue(nodes.updateNode(note.id,"Nota editada","Descripción"))
        db.close();open()
        assertNull(nodes.getNode(task.id)!!.projectId)
        assertEquals("Nota editada",nodes.getNode(note.id)!!.title)
        assertEquals(setOf(task.id,note.id),nodes.observeDrafts().first().map {it.id}.toSet())
        assertTrue(db.backupDao().projects().isEmpty())
    }
    @Test fun movesKeepIdentityMetadataFavoritesAndFileBytesAndProjectDeletionCannotDeleteDraft()=runBlocking<Unit> {
        val p=project();val layer=nodes.createNode(p.id,null,"Capa",purpose=NodePurpose.LAYER)
        val task=nodes.createNode(p.id,layer.id,"Audio y texto","Contenido",priority=Priority.HIGH)
        db.personDao().insert(PersonEntity("person","Ana",null))
        db.personDao().assign(listOf(NodePersonEntity(task.id,"person")))
        db.tagDao().insert(listOf(TagEntity("tag","Etiqueta","etiqueta")))
        db.tagDao().assignNodes(listOf(NodeTagEntity(task.id,"tag")))
        val file=audio(task.id);val saved=db.nodeDao().getById(task.id)!!
        val access=AccessRepository(db);access.toggle("node:${task.id}")
        val events=db.nodeEventDao().forNode(task.id)
        assertTrue(nodes.relocateNode(task.id,null))
        assertTrue(nodes.getProjectNodes(p.id).none {it.id==task.id})
        assertEquals(saved.copy(projectId=null,parentId=null,position=0,updatedAt=db.nodeDao().getById(task.id)!!.updatedAt),db.nodeDao().getById(task.id))
        assertEquals(listOf("person"),db.personDao().assignmentIds(task.id))
        assertEquals(listOf("tag"),db.tagDao().nodeIds(task.id))
        assertEquals(events,db.nodeEventDao().forNode(task.id));assertArrayEquals(tone(),SecureFiles.read(attachments.localFile(file)))
        assertEquals(setOf("node:${task.id}"),access.snapshot().favorites)
        val index=ElementSearch.index(listOf(p),listOf(nodes.getNode(task.id)!!))
        assertEquals("Borradores",index.single {it.nodeId==task.id}.path)
        assertFalse(index.single {it.nodeId==task.id}.destination)
        assertTrue(nodes.relocateNode(task.id,p.id,layer.id))
        assertTrue(nodes.observeDrafts().first().isEmpty());assertEquals(task.id,nodes.getNode(task.id)!!.id)
        assertTrue(nodes.relocateNode(task.id,null))
        db.projectDao().delete(p.id)
        assertNotNull(nodes.getNode(task.id));assertArrayEquals(tone(),SecureFiles.read(attachments.localFile(file)))
    }
    @Test fun invalidCancelledAndRolledBackMovesLeaveEverythingIntact()=runBlocking<Unit> {
        val p=project();val task=nodes.createNode(null,null,"Conservar")
        val file=audio(task.id);val before=db.nodeDao().getById(task.id)
        assertTrue(runCatching {nodes.relocateNode(task.id,"missing")}.isFailure)
        assertEquals(before,db.nodeDao().getById(task.id))
        val leaf=nodes.createNode(p.id,null,"No es capa")
        assertTrue(runCatching {nodes.relocateNode(task.id,p.id,leaf.id)}.isFailure)
        assertEquals(before,db.nodeDao().getById(task.id))
        assertTrue(runCatching {db.withTransaction {nodes.relocateNode(task.id,p.id);throw CancellationException("cancel")}}.isFailure)
        assertEquals(before,db.nodeDao().getById(task.id));assertTrue(attachments.localFile(file).isFile)
        val layer=nodes.createNode(p.id,null,"Estructura",purpose=NodePurpose.LAYER)
        assertTrue(runCatching {nodes.relocateNode(layer.id,null)}.isFailure)
        assertEquals(p.id,nodes.getNode(layer.id)!!.projectId)
    }
    @Test fun draftsNeverExpireOrEnterProjectCalendarAttentionAndFinancialAggregates()=runBlocking<Unit> {
        val task=nodes.createNode(null,null,"Conservar siempre",dueAt=100,priority=Priority.HIGH)
        db.openHelper.writableDatabase.execSQL("UPDATE nodes SET amountMinor=100,currencyCode='USD' WHERE id=?",arrayOf(task.id))
        nodes.toggleCompleted(task.id)
        val cleanup=CompletedCleanup(db,nodes,attachments);cleanup.setDays(1)
        assertTrue(cleanup.preview(days=1,now=Long.MAX_VALUE).candidates.isEmpty())
        assertTrue(cleanup.preview().candidates.isEmpty())
        val tree=NodeTreeSnapshot(listOf(nodes.getNode(task.id)!!))
        assertTrue(tree.projectProgressById.isEmpty())
        assertTrue(CalendarSnapshot(tree,java.util.TimeZone.getTimeZone("UTC")).tasksByDay.isEmpty())
        assertTrue(AttentionSnapshot(tree,1000).tasks.isEmpty())
        assertTrue(nodes.observeAllState().first().nodes.isEmpty())
    }
    @Test fun encryptedBackupRestoresProjectlessAudioMetadataAndFavoritesAndRejectsInvalidDataBeforeReplacement()=runBlocking<Unit> {
        val task=nodes.createNode(null,null,"Recuperable","Texto");val note=nodes.createNode(null,null,"Nota",purpose=NodePurpose.NOTE)
        val file=audio(task.id);db.attachmentDao().playback(file.id,64)
        val access=AccessRepository(db);access.toggle("node:${task.id}")
        val repo=backup();val snapshot=repo.snapshot()
        assertEquals(42,JSONObject(String(BackupJson.encode(snapshot))).getInt("dataVersion"))
        val archive=repo.create("contraseña".toCharArray())
        val candidate=repo.inspect(archive.inputStream(),"contraseña".toCharArray())
        nodes.deleteNode(task.id);nodes.deleteNode(note.id);attachments.cleanup()
        repo.restore(candidate);repo.discard(candidate)
        assertEquals(snapshot.nodes,db.backupDao().nodes())
        val restored=db.attachmentDao().get(file.id)!!
        assertArrayEquals(tone(),SecureFiles.read(attachments.localFile(restored)));assertEquals(64L,restored.playbackMs)
        assertEquals(setOf("node:${task.id}"),access.snapshot().favorites)
        val current=repo.snapshot()
        assertTrue(runCatching {repo.restore(current.copy(nodes=current.nodes.map {if(it.id==task.id) it.copy(parentId=note.id) else it}))}.isFailure)
        assertEquals(current.nodes,db.backupDao().nodes())
        val corrupt=archive.readBytes().apply {this[lastIndex]=(this[lastIndex].toInt() xor 1).toByte()}
        assertTrue(runCatching {repo.inspect(corrupt.inputStream(),"contraseña".toCharArray())}.isFailure)
        assertEquals(current.nodes,db.backupDao().nodes())
    }
    @Test fun sharedCaptureCanBeConfirmedWithoutDestinationAndHistoricalBackupHasNoDrafts()=runBlocking<Unit> {
        val queue=SharedCaptureQueue(db,attachments);val id=java.util.UUID.randomUUID().toString()
        queue.receive(id,SharedContent("Compartido","https://example.test/texto",null,linkNote=true))
        db.close();open()
        val recovered=SharedCaptureQueue(db,attachments);recovered.recover()
        val entry=recovered.observe().first().single()
        recovered.save(entry.id,null,entry.title,entry.description,emptySet())
        assertTrue(recovered.observe().first().isEmpty())
        assertEquals(NodePurpose.NOTE,nodes.getNode(id)!!.purpose);assertNull(nodes.getNode(id)!!.projectId)
        val historical=BackupJson.decode(BackupJson.encode(BackupFixture.empty()))
        assertTrue(historical.nodes.isEmpty())
        backup().restore(historical)
        assertTrue(nodes.observeDrafts().first().isEmpty())
    }

    @Test fun modifiedOrderIncludesAudioAndSortingMetadataRoundTripsWithoutOrphans()=runBlocking<Unit> {
        var clock=100L;val timed=NodeRepository(db) {clock}
        val first=timed.createNode(null,null,"Primero");clock=200
        val second=timed.createNode(null,null,"Segundo")
        assertEquals(listOf(second.id,first.id),timed.observeDrafts().first().map {it.id})
        audio(first.id)
        assertEquals(first.id,timed.observeDrafts().first().first().id)
        val p=project();timed.relocateNode(first.id,p.id)
        db.nodeSortPreferenceDao().save(NodeSortPreferenceEntity("${p.id}:${first.id}","CREATED_NEWEST"))
        timed.relocateNode(first.id,null)
        assertEquals("draft:${first.id}",db.nodeSortPreferenceDao().all().single().context)
        val repo=backup();val archive=repo.create();val data=repo.inspect(archive.inputStream())
        repo.restore(data);repo.discard(data)
        timed.relocateNode(first.id,p.id)
        assertEquals("${p.id}:${first.id}",db.nodeSortPreferenceDao().all().single().context)
        timed.relocateNode(first.id,null);timed.deleteNode(first.id)
        assertTrue(db.nodeSortPreferenceDao().all().isEmpty());repo.snapshot()
    }
    @Test fun recurringReceiptsCannotBeDetachedFromTheirHistory()=runBlocking<Unit> {
        val p=project();val now=RecurrenceSchedule.timestamp(RecurrenceSchedule.parse("2026-10-10"),"UTC",720)
        val recurring=NodeRepository(db) {now}
        val rule=RecurrenceRuleEntity("rule",p.id,null,"Recurrente","",null,null,RecurrenceSchedule.parse("2026-10-10"),"DAILY",1,null,0,"ACTIVE","UTC",0,null)
        recurring.recurrence.create(rule);recurring.recurrence.materializeDue()
        val occurrence=db.recurrenceDao().occurrences().single();val before=db.nodeDao().getById(occurrence.nodeId)
        assertTrue(runCatching {nodes.relocateNode(occurrence.nodeId,null)}.isFailure)
        assertEquals(before,db.nodeDao().getById(occurrence.nodeId))
        assertEquals(listOf(occurrence),db.recurrenceDao().occurrences())
    }

    @Test fun noteImageBytesSurviveMoveAndRestoreAndMissingFileAbortsExport()=runBlocking<Unit> {
        val note=nodes.createNode(null,null,"Nota con imagen",purpose=NodePurpose.NOTE)
        val bytes=BackupFixture.png();val id=java.util.UUID.randomUUID().toString()
        val hash=java.security.MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") {"%02x".format(it)}
        val row=AttachmentFileEntity(id,"$id.png","Original.png","image/png",bytes.size.toLong(),32,32,hash,1)
        val local=AttachmentStore(context,BackupFixture::syncDirectory).file(row.storageName)
        local.parentFile!!.mkdirs();SecureFiles.write(local,bytes)
        db.attachmentDao().insert(row);attachments.associateNode(note.id,id,AttachmentReferences.token(id))
        val p=project();nodes.relocateNode(note.id,p.id);nodes.relocateNode(note.id,null)
        val repo=backup();val archive=repo.create();val candidate=repo.inspect(archive.inputStream())
        assertTrue(local.delete())
        assertTrue(runCatching {repo.create()}.isFailure)
        repo.restore(candidate);repo.discard(candidate)
        val restored=db.attachmentDao().get(id)!!
        assertArrayEquals(bytes,SecureFiles.read(attachments.localFile(restored)))
        assertNull(nodes.getNode(note.id)!!.projectId)
        assertEquals(setOf(id),AttachmentReferences.ids(nodes.getNode(note.id)!!.description))
    }

    @Test fun editorReservationsSurviveRejectedSaveAndRestartAndSuccessfulSaveReleasesOnlyItsOwn()=runBlocking<Unit> {
        val task=nodes.createNode(null,null,"Editor");val file=audio(task.id)
        attachments.retainGallery("editor-one",setOf(file.id));attachments.retainGallery("editor-two",setOf(file.id))
        val description=nodes.getNode(task.id)!!.description
        assertTrue(runCatching {attachments.saveNodeDraft("editor-one",listOf(task.id),description,emptySet()) {false}}.isFailure)
        assertEquals(setOf(file.id),AttachmentStore(context,BackupFixture::syncDirectory).reservations("editor-one"))
        assertTrue(attachments.saveNodeDraft("editor-one",listOf(task.id),description,emptySet()) {text->nodes.updateNode(task.id,"Guardado",text)})
        assertTrue(AttachmentStore(context,BackupFixture::syncDirectory).reservations("editor-one").isEmpty())
        nodes.deleteNode(task.id);attachments.cleanup();assertTrue(attachments.localFile(file).isFile)
        db.close();open();attachments.cleanup();assertTrue(attachments.localFile(file).isFile)
        attachments.discardDraft("editor-two");attachments.cleanup();assertFalse(attachments.localFile(file).exists())
    }

    @Test fun confirmedAudioDeletionRespectsSharedOwnersAndEditorReservations()=runBlocking<Unit> {
        val first=nodes.createNode(null,null,"Uno");val second=nodes.createNode(null,null,"Dos");val file=audio(first.id)
        attachments.associateNode(second.id,file.id,AttachmentReferences.audioToken(file.id))
        nodes.deleteNode(first.id);attachments.cleanup();assertTrue(attachments.localFile(file).isFile)
        val lease=File(context.filesDir,"attachments/staging/${file.id}.lease");SecureFiles.write(lease,"editor".toByteArray())
        nodes.deleteNode(second.id);attachments.cleanup();assertTrue(attachments.localFile(file).isFile)
        attachments.discardDraft("editor");attachments.cleanup()
        assertFalse(attachments.localFile(file).exists());assertNull(db.attachmentDao().get(file.id))
    }
}
