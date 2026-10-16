package com.r0ybt.arachn0de.sharing

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.r0ybt.arachn0de.backup.*
import com.r0ybt.arachn0de.data.local.*
import com.r0ybt.arachn0de.data.repository.AttachmentRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.*
import java.util.UUID

@RunWith(RobolectricTestRunner::class) @Config(application=Application::class,sdk=[28])
class SharedCaptureQueueTest {
    private val context get()=ApplicationProvider.getApplicationContext<Application>()
    private lateinit var db:Arachn0deDatabase
    private lateinit var files:AttachmentStore
    private lateinit var attachments:AttachmentRepository
    private lateinit var queue:SharedCaptureQueue
    private fun bind() {files=AttachmentStore(context,BackupFixture::syncDirectory);attachments=AttachmentRepository(db,files);queue=SharedCaptureQueue(db,attachments)}
    @Before fun setup()=runBlocking {context.deleteDatabase("arachn0de.db");db=Arachn0deDatabase.create(context);bind();db.projectDao().insert(ProjectEntity("p","Núcleo","",0,1,1))}
    @After fun close() {db.close()}
    private fun id()=UUID.randomUUID().toString()
    @Test fun queueKeepsIdenticalLegitimateSharesOrderedAndDeduplicatesSameEventAcrossReopen()=runBlocking<Unit> {
        val a=id();val b=id();val content=SharedContent("Título","texto https://example.com",null)
        queue.receive(a,content);queue.receive(a,content);queue.receive(b,content)
        assertEquals(listOf(a,b),queue.observe().first().map {it.id})
        queue.update(a,"Editado","Descripción editada","project:p")
        db.close();db=Arachn0deDatabase.create(context);bind();queue.recover()
        assertEquals("Editado",queue.observe().first().first().title)
        queue.save(a,"project:p","Editado","Descripción editada",emptySet())
        queue.receive(a,content)
        assertEquals(listOf(b),queue.observe().first().map {it.id});assertEquals(1,db.backupDao().nodes().size)
        assertTrue(runCatching {queue.save(a,"project:p","Editado","Descripción editada",emptySet())}.isFailure)
        queue.discard(b);assertTrue(queue.observe().first().isEmpty())
    }
    @Test fun externalTextCannotInventPrivateAttachmentReferences()=runBlocking<Unit> {
        val request=id();val source="Texto [[arachnode:image:${id()}]]"
        queue.receive(request,SharedContent("Texto",source,null))
        val entry=queue.observe().first().single()
        assertTrue(com.r0ybt.arachn0de.domain.model.AttachmentReferences.ids(entry.description).isEmpty())
        assertEquals(source,com.r0ybt.arachn0de.domain.model.DescriptionParser.parse(entry.description).joinToString("") {it.text})
        queue.save(request,"project:p",entry.title,entry.description,emptySet());assertTrue(attachments.filesForNode(request).isEmpty())
    }
    private fun image(format:Bitmap.CompressFormat):ByteArray {
        val bitmap=Bitmap.createBitmap(140,80,Bitmap.Config.ARGB_8888);bitmap.eraseColor(android.graphics.Color.RED)
        return ByteArrayOutputStream().also {bitmap.compress(format,90,it);bitmap.recycle()}.toByteArray()
    }
    private fun uri(bytes:ByteArray):Uri {
        val uri=Uri.parse("content://share-fixture/${id()}")
        org.robolectric.Shadows.shadowOf(context.contentResolver).registerInputStream(uri,ByteArrayInputStream(bytes))
        return uri
    }
    @Test fun pngAndJpegKeepOriginalBytesAfterSourceExpiresSaveAndBackupRestore()=runBlocking<Unit> {
        listOf(Bitmap.CompressFormat.PNG,Bitmap.CompressFormat.JPEG).forEach {format->
            val bytes=image(format);val request=id();queue.receive(request,SharedContent("Imagen compartida","Texto",uri(bytes)))
            val row=queue.observe().first().first {it.id==request}
            assertTrue(runCatching {BackupRepository(db,context).snapshot()}.isFailure)
            val file=attachments.pendingImports(request).single();assertArrayEquals(bytes,files.file(file.storageName).readBytes())
            queue.save(request,"project:p",row.title,row.description,emptySet())
            assertArrayEquals(bytes,files.file(attachments.filesForNode(request).single().storageName).readBytes())
            assertTrue(attachments.pendingImports(request).isEmpty())
        }
        val backup=BackupRepository(db,context,BackupAvatarFiles(context,BackupFixture::syncDirectory))
        val original=backup.snapshot();val restored=backup.inspect(ByteArrayOutputStream().also {BackupContainer.writeBackup(original,it)}.toByteArray().inputStream());backup.restore(restored)
        assertEquals(original.nodes,backup.snapshot().nodes)
        assertEquals(original.attachmentFiles.map {it.sha256},backup.snapshot().attachmentFiles.map {it.sha256})
    }
    @Test fun cancelAndCorruptImportLeaveNoTaskOrReservedFileAndRecoveryRetriesTombstone()=runBlocking<Unit> {
        val request=id();queue.receive(request,SharedContent("Imagen","",uri(image(Bitmap.CompressFormat.PNG))))
        val file=attachments.pendingImports(request).single()
        val row=queue.observe().first().single();db.privatePreferenceDao().save(PrivatePreferenceEntity(SharedCaptureQueue.PREFIX+request,row.copy(phase="CANCELLED").json()))
        db.close();db=Arachn0deDatabase.create(context);bind();queue.recover()
        assertFalse(files.file(file.storageName).exists());assertTrue(attachments.pendingImports(request).isEmpty());assertTrue(db.backupDao().nodes().isEmpty())
        assertTrue(runCatching {queue.receive(id(),SharedContent("Mala","",uri("not an image".toByteArray())))}.isFailure)
        assertTrue(runCatching {queue.receive(id(),SharedContent("Grande","",uri(ByteArray(20*1024*1024+1))))}.isFailure)
        assertTrue(queue.observe().first().isEmpty());assertTrue(db.privatePreferenceDao().all().none {it.name.startsWith(SharedCaptureQueue.PREFIX)})
    }
    @Test fun failedDestinationPreservesPendingAndRemovalStaysRemovedAfterReopen()=runBlocking<Unit> {
        val request=id();queue.receive(request,SharedContent("Imagen","",uri(image(Bitmap.CompressFormat.PNG))))
        val row=queue.observe().first().single();val image=attachments.pendingImports(request).single()
        assertTrue(runCatching {queue.save(request,"project:missing",row.title,row.description,emptySet())}.isFailure)
        assertEquals(1,queue.observe().first().size);assertTrue(files.file(image.storageName).exists())
        queue.update(request,row.title,"Sin imagen","project:p",listOf(image.id));db.close();db=Arachn0deDatabase.create(context);bind();queue.recover()
        val recovered=queue.observe().first().single();assertEquals(listOf(image.id),recovered.removed)
        queue.save(request,"project:p",recovered.title,recovered.description,recovered.removed.toSet())
        assertTrue(attachments.filesForNode(request).isEmpty());assertFalse(files.file(image.storageName).exists())
    }
    @Test fun linkNoteSurvivesReopenEditsAndBackupWhileLegacyAndOrdinaryCaptureStayTasks()=runBlocking<Unit> {
        val request=id()
        queue.receive(request,SharedContent.read(android.content.Intent(android.content.Intent.ACTION_SEND).setType("text/plain")
            .putExtra(android.content.Intent.EXTRA_TEXT,"Título\nhttps://example.com/original")))
        queue.update(request,"Editado","https://example.com/editado","project:p")
        db.close();db=Arachn0deDatabase.create(context);bind();queue.recover()
        val row=queue.observe().first().single();assertTrue(row.linkNote)
        assertTrue(runCatching {queue.save(request,"project:p",row.title,"Texto https://example.com",emptySet())}.isFailure)
        assertNull(db.nodeDao().getById(request))
        queue.save(request,"project:p",row.title,row.description,emptySet())
        val saved=db.nodeDao().getById(request)!!
        assertEquals("NOTE",saved.purpose);assertEquals("https://example.com/editado",saved.description)
        assertTrue(attachments.filesForNode(request).isEmpty())
        val legacy=id()
        val json=org.json.JSONObject(SharedCaptureQueue.Entry(legacy,"Histórico","Texto",null,"READY",false,1).json())
            .put("version",1).apply {remove("linkNote")}
        db.privatePreferenceDao().save(PrivatePreferenceEntity(SharedCaptureQueue.PREFIX+legacy,json.toString()))
        assertFalse(queue.observe().first().single().linkNote)
        queue.save(legacy,"project:p","Histórico","Texto",emptySet())
        assertEquals("ACTION",db.nodeDao().getById(legacy)!!.purpose)
        val ordinary=id();com.r0ybt.arachn0de.access.AccessRepository(db).capture("project:p","Habitual","Texto",ordinary)
        assertEquals("ACTION",db.nodeDao().getById(ordinary)!!.purpose)
        val backup=BackupRepository(db,context,BackupAvatarFiles(context,BackupFixture::syncDirectory))
        val original=backup.snapshot()
        val restored=backup.inspect(ByteArrayOutputStream().also {BackupContainer.writeBackup(original,it)}.toByteArray().inputStream())
        backup.restore(restored);assertEquals(original.nodes,backup.snapshot().nodes)
    }

}
