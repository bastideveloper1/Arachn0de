package com.r0ybt.arachn0de.backup

import android.app.Application
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.r0ybt.arachn0de.data.local.*
import com.r0ybt.arachn0de.data.repository.AttachmentRepository
import com.r0ybt.arachn0de.domain.model.*
import com.r0ybt.arachn0de.security.*
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.*
import java.io.File
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(application=Application::class,sdk=[28])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ContentGifBackupTest {
    private lateinit var app:Application
    private lateinit var db:Arachn0deDatabase
    private lateinit var attachments:AttachmentRepository
    private lateinit var store:AttachmentStore
    private lateinit var backup:BackupRepository
    private lateinit var session:SecureFiles.Session
    private var refuseDelete=false
    private val bytes get()=javaClass.getResourceAsStream("/content/two-frames.gif")!!.use {it.readBytes()}
    @Before fun setup() {
        app=ApplicationProvider.getApplicationContext();app.deleteDatabase("arachn0de.db")
        File(app.filesDir,"attachments").deleteRecursively()
        val root=File(app.filesDir,"attachments").apply {mkdirs()}
        session=SecureFiles.Session(UUID.randomUUID(),root,VaultCrypto.randomKey());SecureFiles.register(session)
        db=Arachn0deDatabase.create(app)
        store=object:AttachmentStore(app,BackupFixture::syncDirectory) {override fun delete(name:String)=if(refuseDelete)false else super.delete(name)}
        attachments=AttachmentRepository(db,store)
        backup=BackupRepository(db,app,BackupAvatarFiles(app,BackupFixture::syncDirectory))
    }
    @After fun close() {db.close();SecureFiles.revoke(session);app.deleteDatabase("arachn0de.db")}
    private suspend fun imported(draft:String="editor"):AttachmentFileEntity {
        val source=File(app.cacheDir,"original-animation.gif").apply {writeBytes(bytes)}
        return attachments.import(Uri.fromFile(source),draft).also {assertArrayEquals(bytes,source.readBytes())}
    }
    private fun description(id:String):String {
        val h=DescriptionParser.Format.Highlight.marker;val t=DescriptionParser.Format.Title.marker;val c=DescriptionParser.Format.Code.marker
        return "${t}Título$t\n${h}árbol$h __***anterior***__\n$c  x = 1\n\treturn x\n$c\n${AttachmentReferences.token(id,"Animación")}" 
    }
    @Test fun encryptedOriginalAndAllFormatsSurvivePortableBackupRestoreAndStaticExport()=runBlocking<Unit> {
        val row=imported();val text=description(row.id)
        attachments.saveProjectDraft("editor","p",text,emptySet()) {db.projectDao().insert(ProjectEntity("p","Núcleo",it,0,1,1))}
        for((id,purpose,parent) in listOf(Triple("layer","LAYER",null),Triple("task","ACTION","layer"),Triple("note","NOTE","layer"))) {
            db.nodeDao().insert(NodeEntity(id,"p",parent,id,"",false,0,1,1,purpose=purpose))
            attachments.associateNode(id,row.id,text)
        }
        assertFalse(store.file(row.storageName).readBytes().take(6).toByteArray().contentEquals(bytes.take(6).toByteArray()))
        assertArrayEquals(bytes,SecureFiles.read(store.file(row.storageName)))
        val archive=backup.create("test-password-123".toCharArray())
        val candidate=backup.inspect(archive.inputStream(),"test-password-123".toCharArray())
        assertEquals(text,candidate.projects.single().description)
        assertEquals(55,org.json.JSONObject(String(BackupJson.encode(candidate))).getInt("dataVersion"))
        backup.restore(candidate)
        val current=db.attachmentDao().get(row.id)!!
        assertNotEquals(row.storageName,current.storageName)
        assertArrayEquals(bytes,SecureFiles.read(store.file(current.storageName)))
        assertEquals(text,db.projectDao().getById("p")!!.description)
        assertTrue(db.nodeDao().getProjectNodes("p").all {it.description==text})
        val static=com.r0ybt.arachn0de.document.reportImage(bytes)
        assertEquals(2,static.width);assertNotNull(android.graphics.BitmapFactory.decodeByteArray(static.bytes,0,static.bytes.size))
        assertArrayEquals(bytes,com.r0ybt.arachn0de.audio.publicAttachmentBytes(attachments,current))
        assertEquals("files/attachments/${row.id}.gif",com.r0ybt.arachn0de.export.ElementExport.attachmentPath(current))
        backup.discard(candidate)
    }
    @Test fun gifReservationsSharedReferencesReplacementCancellationAndDeferredDelete()=runBlocking<Unit> {
        val row=imported()
        attachments.saveProjectDraft("editor","p",AttachmentReferences.token(row.id),emptySet()) {db.projectDao().insert(ProjectEntity("p","Núcleo",it,0,1,1))}
        db.projectDao().insert(ProjectEntity("q","Otro","",1,1,1));attachments.associateProject("q",row.id,AttachmentReferences.token(row.id))
        val cancelled=imported("cancel")
        attachments.discardDraft("cancel");assertFalse(store.file(cancelled.storageName).exists());assertTrue(store.file(row.storageName).exists())
        val replacement=imported("replace")
        attachments.saveProjectDraft("replace","p",AttachmentReferences.token(replacement.id),setOf(row.id)) {
            db.projectDao().update("p","Núcleo",it,2)
        }
        assertTrue(store.file(row.storageName).exists());assertTrue(store.file(replacement.storageName).exists())
        refuseDelete=true;attachments.detachProject("q",row.id)
        assertTrue(store.file(row.storageName).exists());assertEquals("DELETE_PENDING",db.attachmentDao().get(row.id)!!.lifecycleState)
        refuseDelete=false;attachments.cleanup()
        assertFalse(store.file(row.storageName).exists());assertNull(db.attachmentDao().get(row.id));assertTrue(store.file(replacement.storageName).exists())
    }
    @Test fun corruptedGifBackupIsRejectedBeforeReplacingExistingContent()=runBlocking<Unit> {
        val row=imported();val text=description(row.id)
        attachments.saveProjectDraft("editor","p",text,emptySet()) {db.projectDao().insert(ProjectEntity("p","Conservar",it,0,1,1))}
        val archive=backup.create();val candidate=backup.inspect(archive.inputStream())
        val file=candidate.attachmentContents.getValue(row.storageName)
        file.writeBytes(byteArrayOf(1,2,3))
        assertTrue(runCatching {backup.restore(candidate)}.isFailure)
        assertEquals(text,db.projectDao().getById("p")!!.description)
        assertArrayEquals(bytes,SecureFiles.read(store.file(row.storageName)))
        backup.discard(candidate)
    }
}
