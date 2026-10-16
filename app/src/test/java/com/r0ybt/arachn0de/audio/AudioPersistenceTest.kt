package com.r0ybt.arachn0de.audio

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.r0ybt.arachn0de.data.local.*
import com.r0ybt.arachn0de.data.repository.*
import com.r0ybt.arachn0de.backup.*
import com.r0ybt.arachn0de.domain.model.*
import com.r0ybt.arachn0de.security.SecureFiles
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class) @Config(application=Application::class,sdk=[28])
class AudioPersistenceTest {
    private val context get()=ApplicationProvider.getApplicationContext<Application>()
    private lateinit var db:Arachn0deDatabase
    private lateinit var recordings:AudioDraftStore
    private lateinit var attachments:AttachmentRepository
    private fun tone()=javaClass.classLoader!!.getResourceAsStream("audio/tone.aac")!!.use {it.readBytes()}
    @Before fun open() {context.deleteDatabase("arachn0de.db");for(name in listOf("audio-drafts","attachments","backups")) File(context.filesDir,name).deleteRecursively();db=Arachn0deDatabase.create(context);recordings=AudioDraftStore(context,db,BackupFixture::syncDirectory);attachments=AttachmentRepository(db,AttachmentStore(context,BackupFixture::syncDirectory))}
    @After fun close() {db.close()}
    private suspend fun task(title:String="Título escrito",description:String="Texto anterior") :Node {
        val project=ProjectRepository(db.projectDao(),db).createProject("Contexto")
        return NodeRepository(db).createNode(project.id,null,title,description)
    }
    private suspend fun audio(owner:String):AttachmentFileEntity {val id=recordings.begin(owner);recordings.append(id,0,tone());return recordings.finish(id)!!}
    @Test fun finalizedMultipleAudiosKeepTextTitleAndSurviveReopen()=runBlocking<Unit> {
        val node=task();val first=audio(node.id);val second=audio(node.id)
        val saved=db.nodeDao().getById(node.id)!!
        assertEquals(node.title,saved.title);assertTrue(saved.description.startsWith("Texto anterior"))
        assertEquals(setOf(first.id,second.id),AttachmentReferences.ids(saved.description))
        assertArrayEquals(tone(),SecureFiles.read(attachments.localFile(first)))
        db.close();db=Arachn0deDatabase.create(context)
        assertEquals(saved,db.nodeDao().getById(node.id));assertEquals(2,db.attachmentDao().forNode(node.id).size)
    }
    @Test fun interruptedSegmentsRecoverOnceWithoutPauseSilence()=runBlocking<Unit> {
        val node=task();val id=recordings.begin(node.id);val bytes=tone()
        recordings.append(id,0,bytes);recordings.append(id,1,bytes)
        val expected=AacFrames.duration(bytes.inputStream())*2
        db.close();db=Arachn0deDatabase.create(context);recordings=AudioDraftStore(context,db,BackupFixture::syncDirectory)
        assertTrue(recordings.recover().isEmpty());assertTrue(recordings.recover().isEmpty())
        val row=db.attachmentDao().files().single();assertEquals(id,row.id);assertEquals(expected,row.durationMs)
        assertEquals(1,db.attachmentDao().forNode(node.id).size)
        assertArrayEquals(bytes+bytes,SecureFiles.read(AttachmentStore(context,BackupFixture::syncDirectory).file(row.storageName)))
    }
    @Test fun activeJournalBlocksIncompleteBackupAndCancelReleasesOnlyItsSegments()=runBlocking<Unit> {
        val node=task();val confirmed=audio(node.id);val id=recordings.begin(node.id);recordings.append(id,0,tone())
        val backup=BackupRepository(db,context,BackupAvatarFiles(context,BackupFixture::syncDirectory))
        assertTrue(runCatching {backup.snapshot()}.isFailure)
        recordings.discard(id);assertTrue(recordings.pending().isEmpty())
        assertEquals(listOf(confirmed),backup.snapshot().attachmentFiles)
        assertTrue(attachments.localFile(confirmed).isFile)
    }
    @Test fun concurrentEditorSaveKeepsServiceAudioAndExplicitRemovalDetachesOnlySelected()=runBlocking<Unit> {
        val node=task();val first=audio(node.id);val second=audio(node.id)
        attachments.saveNodeDraft("editor",listOf(node.id),"Texto editado",emptySet()) {text->db.nodeDao().updateContent(node.id,node.title,text,22)}
        val saved=db.nodeDao().getById(node.id)!!
        assertTrue(saved.description.startsWith("Texto editado"));assertEquals(setOf(first.id,second.id),AttachmentReferences.ids(saved.description))
        attachments.saveNodeDraft("editor",listOf(node.id),AttachmentReferences.remove(saved.description,first.id),setOf(first.id)) {text->db.nodeDao().updateContent(node.id,node.title,text,23)}
        assertNull(db.attachmentDao().get(first.id));assertFalse(attachments.localFile(first).exists())
        assertTrue(attachments.localFile(second).isFile);assertEquals(setOf(second.id),AttachmentReferences.ids(db.nodeDao().getById(node.id)!!.description))
    }
    @Test fun sharedAudioLastReferenceAndEditorReservationPreventEarlyDeletion()=runBlocking<Unit> {
        val node=task();val row=audio(node.id);val other=NodeRepository(db).createNode(node.projectId,null,"Otra tarea")
        attachments.associateNode(other.id,row.id,AttachmentReferences.audioToken(row.id))
        attachments.detachNode(node.id,row.id);assertTrue(attachments.localFile(row).isFile)
        val staging=File(context.filesDir,"attachments/staging/${row.id}.lease")
        SecureFiles.write(staging,"editor".toByteArray())
        attachments.detachNode(other.id,row.id);assertTrue(attachments.localFile(row).isFile)
        attachments.discardDraft("editor");assertFalse(attachments.localFile(row).exists());assertNull(db.attachmentDao().get(row.id))
    }
    @Test fun archiveRestoresBytesPlaybackBookmarkAndRejectsCorruptionWithoutReplacingState()=runBlocking<Unit> {
        val node=task();val row=audio(node.id)
        db.attachmentDao().playback(row.id,128);db.attachmentDao().bookmark(row.id,64)
        val backup=BackupRepository(db,context,BackupAvatarFiles(context,BackupFixture::syncDirectory))
        val before=backup.snapshot();assertEquals(34,JSONObject(String(BackupJson.encode(before))).getInt("dataVersion"))
        val archive=backup.create();val inspected=backup.inspect(archive.readBytes().inputStream())
        attachments.detachNode(node.id,row.id);backup.restore(inspected);backup.discard(inspected)
        val restored=db.attachmentDao().get(row.id)!!;assertEquals(128L,restored.playbackMs);assertEquals(64L,restored.bookmarkMs)
        assertArrayEquals(tone(),SecureFiles.read(attachments.localFile(restored)))
        val current=backup.snapshot()
        val bad=current.copy(attachmentFiles=current.attachmentFiles.map {it.copy(bookmarkMs=it.durationMs+1)})
        assertTrue(runCatching {backup.restore(bad)}.isFailure)
        assertEquals(current.nodes,db.backupDao().nodes())
        val corrupt=File(context.cacheDir,"corrupt.aac").apply {writeBytes(tone().dropLast(1).toByteArray())}
        assertTrue(runCatching {verifyBackupAttachment(restored,corrupt)}.isFailure)
        assertEquals(restored,db.attachmentDao().get(row.id))
    }
    @Test fun imageOnlyLegacyBackupDefaultsAudioMetadataAndPreservesOriginalBytes()=runBlocking<Unit> {
        val node=task();val image=File(context.cacheDir,"original.png").apply {writeBytes(BackupFixture.png())}
        val imported=attachments.import(android.net.Uri.fromFile(image),"images")
        attachments.associateNode(node.id,imported.id,AttachmentReferences.token(imported.id))
        val backup=BackupRepository(db,context,BackupAvatarFiles(context,BackupFixture::syncDirectory))
        val current=backup.snapshot();val historical=JSONObject(String(BackupJson.encode(current)))
        assertTrue(historical.getInt("dataVersion")<34)
        val decoded=BackupJson.decode(historical.toString().toByteArray())
        assertEquals(0L,decoded.attachmentFiles.single().playbackMs);assertNull(decoded.attachmentFiles.single().bookmarkMs)
        val inspected=backup.inspect(backup.create().readBytes().inputStream());backup.restore(inspected);backup.discard(inspected)
        assertArrayEquals(image.readBytes(),SecureFiles.read(attachments.localFile(db.attachmentDao().files().single())))
    }
    @Test fun committedCleanupFailureNeverResurrectsAnExplicitlyDeletedAudio()=runBlocking<Unit> {
        val node=task();var id=""
        val failing=AudioDraftStore(context,db) {dir->
            if(dir.name=="staging" && id.isNotBlank() && runBlocking {db.attachmentDao().get(id)!=null}) error("Cleanup unavailable")
            BackupFixture.syncDirectory(dir)
        }
        id=failing.begin(node.id);failing.append(id,0,tone())
        val row=failing.finish(id)!!
        assertEquals("COMMITTED",db.attachmentDao().recording(id)!!.state)
        attachments.detachNode(node.id,id);assertNull(db.attachmentDao().get(id))
        assertTrue(recordings.recover().isEmpty())
        assertTrue(db.attachmentDao().files().isEmpty());assertTrue(recordings.pending().isEmpty())
        assertFalse(attachments.localFile(row).exists());assertFalse(id in AttachmentReferences.ids(db.nodeDao().getById(node.id)!!.description))
    }
    @Test fun exportedCopiesAreOriginalAndRejectModifiedBytesWithoutRemovingSource()=runBlocking<Unit> {
        val node=task();val row=audio(node.id)
        assertArrayEquals(tone(),originalAttachmentBytes(attachments,row))
        val image=File(context.cacheDir,"download.png").apply {writeBytes(BackupFixture.png())}
        val imported=attachments.import(android.net.Uri.fromFile(image),"image-export")
        attachments.associateNode(node.id,imported.id)
        assertArrayEquals(image.readBytes(),originalAttachmentBytes(attachments,imported))
        val file=attachments.localFile(row);file.appendBytes(byteArrayOf(1))
        assertTrue(runCatching {originalAttachmentBytes(attachments,row)}.isFailure)
        assertTrue(file.isFile);assertNotNull(db.attachmentDao().get(row.id))
    }
    @Test fun authenticatedPartIsRecoveredButIncompleteTailWaitsForExplicitDecision()=runBlocking<Unit> {
        val node=task();val id=recordings.begin(node.id)
        val part=File(context.filesDir,"audio-drafts/$id/0.part");SecureFiles.write(part,tone())
        assertTrue(recordings.recover().isEmpty())
        assertArrayEquals(tone(),SecureFiles.read(AttachmentStore(context,BackupFixture::syncDirectory).file(db.attachmentDao().get(id)!!.storageName)))
        val another=recordings.begin(node.id);recordings.append(another,0,tone())
        val broken=File(context.filesDir,"audio-drafts/$another/1.part");SecureFiles.write(broken,tone().dropLast(1).toByteArray())
        assertEquals(listOf(another),recordings.recover());assertTrue(broken.isFile)
        assertNull(db.attachmentDao().get(another))
        val saved=recordings.finish(another,allowIncomplete=true)!!
        assertTrue(recordings.incompleteRecovered);assertArrayEquals(tone(),SecureFiles.read(attachments.localFile(saved)))
        assertEquals(setOf(id,another),AttachmentReferences.ids(db.nodeDao().getById(node.id)!!.description))
    }
    @Test fun discardedCleanupFailureIsDurableAndDoesNotPublishAudioOnRecovery()=runBlocking<Unit> {
        val node=task();val id=recordings.begin(node.id);recordings.append(id,0,tone())
        val marker=File(context.filesDir,"audio-drafts/$id/blocker").apply {writeText("owned by another operation")}
        assertTrue(runCatching {recordings.discard(id)}.isFailure)
        assertEquals("DISCARDED",db.attachmentDao().recording(id)!!.state)
        assertNull(db.attachmentDao().get(id));assertTrue(marker.exists())
        marker.delete();assertTrue(recordings.recover().isEmpty());assertTrue(recordings.pending().isEmpty())
        assertTrue(AttachmentReferences.ids(db.nodeDao().getById(node.id)!!.description).isEmpty())
    }
    @Test fun processLossBeforeMicrophoneRemovesOnlyEmptyProvisionalTaskAndKeepsUserTitle()=runBlocking<Unit> {
        val empty=task("Audio — provisional","");val typed=task("Título del usuario","")
        NodeRepository(db).reserveAudioCreation(empty.id,empty.title)
        NodeRepository(db).reserveAudioCreation(typed.id,null)
        val backup=BackupRepository(db,context,BackupAvatarFiles(context,BackupFixture::syncDirectory))
        assertTrue(runCatching {backup.snapshot()}.isFailure)
        assertTrue(recordings.recover().isEmpty())
        assertNull(db.nodeDao().getById(empty.id));assertEquals(typed.title,db.nodeDao().getById(typed.id)!!.title)
        assertTrue(recordings.pending().isEmpty());assertEquals(2,db.projectDao().getAll().size)
    }
    @Test fun emptyCaptureAfterReservationRetainsConfiguredTaskDataAndCannotLosePartialTail()=runBlocking<Unit> {
        val node=task("Audio — provisional","")
        NodeRepository(db).reserveAudioCreation(node.id,node.title)
        val id=recordings.begin(node.id)
        val partial=File(context.filesDir,"audio-drafts/$id/0.part").apply {writeBytes(tone().dropLast(1).toByteArray())}
        assertTrue(runCatching {recordings.finish(id,allowIncomplete=true)}.isFailure)
        assertNotNull(db.nodeDao().getById(node.id));assertTrue(partial.exists())
        recordings.discard(id);assertNull(db.nodeDao().getById(node.id))
        val configured=task("Audio — configurado","")
        db.nodeDao().updatePriority(configured.id,"HIGH",configured.updatedAt)
        NodeRepository(db).reserveAudioCreation(configured.id,configured.title)
        val another=recordings.begin(configured.id);assertNull(recordings.finish(another))
        assertNotNull(db.nodeDao().getById(configured.id));assertEquals("HIGH",db.nodeDao().getById(configured.id)!!.priority)
    }
    @Test fun undoFinalizesActiveAudioBeforeRejectingChangedCreationAndOrdinaryUndoStillWorks()=runBlocking<Unit> {
        val node=task();val repository=NodeRepository(db)
        val undo=repository.captureCreation(listOf(node.id))
        val id=recordings.begin(node.id);recordings.append(id,0,tone())
        repository.beforeDelete={ids->if(node.id in ids) recordings.finish(id)}
        assertFalse(repository.undoCreation(undo))
        assertNotNull(db.nodeDao().getById(node.id));assertEquals(listOf(id),db.attachmentDao().forNode(node.id).map {it.attachmentId})
        val another=repository.createNode(node.projectId,null,"Normal")
        assertTrue(repository.undoCreation(repository.captureCreation(listOf(another.id))))
        assertNull(db.nodeDao().getById(another.id));assertNotNull(db.nodeDao().getById(node.id))
    }
    @Test fun conversionFinalizesAudioAndPromotionKeepsBytesAndOwnerReferences()=runBlocking<Unit> {
        val node=task();val repository=NodeRepository(db)
        val id=recordings.begin(node.id);recordings.append(id,0,tone())
        repository.beforeDelete={ids->if(node.id in ids) recordings.finish(id)}
        assertTrue(repository.convertPurpose(node.id,NodePurpose.LAYER))
        val promoted=ProjectRepository(db.projectDao(),db).convertLayerToProject(node.id)
        assertNull(db.nodeDao().getById(node.id))
        assertEquals(listOf(id),db.attachmentDao().forProject(promoted).map {it.attachmentId})
        assertEquals(setOf(id),AttachmentReferences.ids(db.projectDao().getById(promoted)!!.description))
        assertArrayEquals(tone(),SecureFiles.read(attachments.localFile(db.attachmentDao().get(id)!!)))
    }
    @Test fun missingOwnerOrBrokenSegmentRetainsRecoveryMaterialAndRejectsInvalidMarkers()=runBlocking<Unit> {
        val node=task();val id=recordings.begin(node.id);recordings.append(id,0,tone())
        NodeRepository(db).deleteNode(node.id)
        assertEquals(listOf(id),recordings.recover());assertTrue(recordings.pending().contains(id))
        assertTrue(File(context.filesDir,"audio-drafts/$id/0.aac").isFile)
        assertEquals(setOf(id),AttachmentReferences.ids("texto "+AttachmentReferences.audioToken(id)))
        assertTrue(AttachmentReferences.ids("\\"+AttachmentReferences.audioToken(id)).isEmpty())
        assertTrue(runCatching {AacFrames.duration(tone().dropLast(1).toByteArray().inputStream())}.isFailure)
        recordings.discard(id)
    }
}
