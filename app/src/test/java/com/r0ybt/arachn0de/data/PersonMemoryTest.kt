package com.r0ybt.arachn0de.data

import android.app.Application
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.r0ybt.arachn0de.backup.*
import com.r0ybt.arachn0de.data.local.*
import com.r0ybt.arachn0de.data.repository.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class) @Config(application=Application::class,sdk=[28])
class PersonMemoryTest {
    private val context get()=ApplicationProvider.getApplicationContext<Application>()
    private lateinit var db:Arachn0deDatabase
    private lateinit var store:AttachmentStore
    private lateinit var attachments:AttachmentRepository
    private lateinit var people:PersonRepository
    private lateinit var gallery:PersonMemoryRepository
    @Before fun open() {
        context.deleteDatabase("arachn0de.db")
        db=Arachn0deDatabase.create(context)
        store=AttachmentStore(context,BackupFixture::syncDirectory)
        attachments=AttachmentRepository(db,store)
        people=PersonRepository(db,AvatarStore(context,syncDirectory=BackupFixture::syncDirectory))
        gallery=PersonMemoryRepository(db,context,attachments,people)
    }
    @After fun close() {db.close()}
    private fun source()=File(context.cacheDir,"original.png").apply {writeBytes(BackupFixture.png())}
    @Test fun originalsMetadataHistoricalImagesAndSharedReferencesSurviveRealBackup()=runBlocking<Unit> {
        people.save("person","Ana",null)
        val legacy=people.importAvatar(Uri.fromFile(source()))
        people.savePhoto(PersonPhotoEntity("legacy","person",legacy,0),"legacy-draft")
        people.setPhotoAsAvatar("person","legacy")
        val file=attachments.import(Uri.fromFile(source()),"draft")
        assertArrayEquals(source().readBytes(),store.file(file.storageName).readBytes())
        val entry=gallery.create("person",listOf(file.id),"draft")
        val image=db.personMemoryDao().images().first {it.memoryId==entry}
        val post=db.personMemoryDao().memories().first {it.id==entry}.copy(date="1980-04-12",description="Recuerdo")
        gallery.save(post,listOf(image.copy(date="1981-01-02",description="Individual")),"edit")
        val second=post.copy(id="shared",position=2)
        gallery.save(second,listOf(image.copy(id="shared-image",memoryId="shared")),"edit")
        assertEquals(2,db.attachmentDao().references(file.id))
        val backup=BackupRepository(db,context,BackupAvatarFiles(context,BackupFixture::syncDirectory))
        val before=backup.snapshot()
        val candidate=backup.inspect(backup.create().readBytes().inputStream())
        gallery.deleteEntry(entry)
        assertTrue(store.file(file.storageName).exists())
        backup.restore(candidate);backup.discard(candidate)
        val restored=backup.snapshot()
        assertEquals(before.personMemories,restored.personMemories)
        assertEquals(before.personMemoryImages,restored.personMemoryImages)
        assertNotNull(db.personDao().get("person")!!.avatarFile)
        val restoredFile=restored.attachmentFiles.single()
        assertArrayEquals(source().readBytes(),store.file(restoredFile.storageName).readBytes())
        val damaged=restored.copy(personMemoryImages=restored.personMemoryImages.map {if(it.id==image.id) it.copy(attachmentId="missing") else it})
        assertTrue(runCatching {backup.restore(damaged)}.isFailure)
        assertEquals(restored.personMemoryImages,backup.snapshot().personMemoryImages)
        gallery.deleteEntry(entry);gallery.deleteEntry("shared")
        assertFalse(store.file(restoredFile.storageName).exists())
        gallery.deleteEntry("legacy")
        assertNotNull(db.personDao().get("person")!!.avatarFile)
        assertTrue(File(context.filesDir,"avatars/${db.personDao().get("person")!!.avatarFile}").exists())
        val old=BackupFixture.empty().copy(persons=listOf(PersonEntity("old","Anterior",legacy)),personPhotos=listOf(PersonPhotoEntity("photo","old",legacy,0)),avatars=mapOf(legacy to source().readBytes()))
        val adapted=BackupJson.decode(BackupJson.encode(old))
        assertNull(adapted.personMemories.single().date)
        assertEquals("",adapted.personMemories.single().description)
        assertEquals("photo",adapted.personMemoryImages.single().legacyPhotoId)
        backup.restore(adapted)
        assertEquals(1,db.personMemoryDao().memories().size)
        assertEquals("photo",db.personMemoryDao().images().single().legacyPhotoId)
    }
    @Test fun editorLeasesProtectSharedOriginalsAcrossReopenAndAvatarChangeDoesNotRewriteOriginal()=runBlocking<Unit> {
        people.save("person","Ana",null)
        val file=attachments.import(Uri.fromFile(source()),"draft")
        val id=gallery.create("person",listOf(file.id),"draft")
        val original=store.file(file.storageName).readBytes()
        attachments.retainGallery("editor-one",setOf(file.id));attachments.retainGallery("editor-two",setOf(file.id))
        val state=gallery.observe().first()
        gallery.setAvatar("person",state.photos(id).single(),state)
        assertArrayEquals(original,store.file(file.storageName).readBytes())
        assertNotNull(db.personDao().get("person")!!.avatarFile)
        gallery.deleteEntry(id)
        assertTrue(store.file(file.storageName).exists())
        attachments.discardDraft("editor-one")
        assertTrue(store.file(file.storageName).exists())
        val reopened=AttachmentRepository(db,AttachmentStore(context,BackupFixture::syncDirectory))
        reopened.discardDraft("editor-two")
        assertFalse(store.file(file.storageName).exists())
        assertNotNull(db.personDao().get("person")!!.avatarFile)
    }
    @Test fun removingCarouselImagesKeepsOtherMetadataAndAvatarThenRemovesEmptyEntry()=runBlocking<Unit> {
        people.save("person","Ana",null)
        val legacy=people.importAvatar(Uri.fromFile(source()))
        people.savePhoto(PersonPhotoEntity("legacy","person",legacy,0),"seed")
        people.setPhotoAsAvatar("person","legacy")
        val file=attachments.importGallery(Uri.fromFile(source()),"draft")
        val entry=PersonMemoryEntity("group","person","1980-04-12","Grupo",0)
        val historical=PersonMemoryImageEntity("historical","group",null,"legacy",0,null,"Histórica")
        val original=PersonMemoryImageEntity("original","group",file.id,null,1,"1981-01-02","Original")
        // Move the migrated photo into the carousel without duplicating its file.
        db.personMemoryDao().remove("legacy")
        gallery.save(entry,listOf(historical,original),"draft")
        gallery.removeImage("original")
        assertEquals(listOf(entry),db.personMemoryDao().memories())
        assertEquals(listOf(historical),db.personMemoryDao().images())
        assertFalse(store.file(file.storageName).exists())
        gallery.removeImage("historical")
        assertTrue(db.personMemoryDao().memories().isEmpty())
        assertTrue(db.personMemoryDao().images().isEmpty())
        assertTrue(db.personGalleryDao().photos().isEmpty())
        assertEquals(legacy,db.personDao().get("person")!!.avatarFile)
        assertTrue(File(context.filesDir,"avatars/$legacy").exists())
    }
    @Test fun restoreKeepsEditorReservationsAndDownloadCopiesOriginalBytes()=runBlocking<Unit> {
        people.save("person","Ana",null)
        val file=attachments.importGallery(Uri.fromFile(source()),"draft")
        val id=gallery.create("person",listOf(file.id),"draft")
        val state=gallery.observe().first()
        val destination=File(context.cacheDir,"download-original.png")
        gallery.export(state.photos(id).single(),state,Uri.fromFile(destination))
        assertArrayEquals(source().readBytes(),destination.readBytes())
        val legacy=people.importAvatar(Uri.fromFile(source()))
        people.savePhoto(PersonPhotoEntity("legacy","person",legacy,1),"legacy-draft")
        attachments.retainGallery("active-editor",setOf(file.id))
        people.retainAvatar(legacy,"active-editor")
        val backup=BackupRepository(db,context,BackupAvatarFiles(context,BackupFixture::syncDirectory))
        val inspected=backup.inspect(backup.create().readBytes().inputStream())
        try {backup.restore(inspected)} finally {backup.discard(inspected)}
        assertTrue(store.file(file.storageName).exists())
        assertTrue(File(context.filesDir,"avatars/$legacy").exists())
        attachments.discardDraft("active-editor");people.discardAvatar(legacy,"active-editor")
        attachments.cleanup();people.cleanup()
        assertFalse(store.file(file.storageName).exists())
        assertFalse(File(context.filesDir,"avatars/$legacy").exists())
        val restored=gallery.observe().first()
        assertEquals(2,restored.entries.size)
        restored.images.forEach {assertTrue(gallery.file(it,restored).exists())}
    }
    @Test fun galleryImportReusesSafeReferencesAndDeletionOfPersonReleasesOnlyUnsharedFiles()=runBlocking<Unit> {
        people.save("person","Ana",null)
        val file=attachments.importGallery(Uri.fromFile(source()),"draft")
        assertEquals(file.id,attachments.importGallery(Uri.fromFile(source()),"draft").id)
        val first=gallery.create("person",listOf(file.id),"draft")
        val reused=attachments.importGallery(Uri.fromFile(source()),"other")
        assertEquals(file.id,reused.id)
        gallery.create("person",listOf(reused.id),"other")
        assertEquals(1,db.attachmentDao().files().count {it.lifecycleState=="READY"})
        assertEquals(2,db.attachmentDao().references(file.id))
        people.delete("person");attachments.cleanup()
        assertTrue(db.personMemoryDao().memories().isEmpty())
        assertFalse(store.file(file.storageName).exists())
    }
    @Test fun cancelInvalidReplacementAndInterruptedDeletionKeepFilesRecoverable()=runBlocking<Unit> {
        people.save("person","Ana",null)
        val abandoned=attachments.import(Uri.fromFile(source()),"cancel")
        attachments.cleanup()
        assertTrue(store.file(abandoned.storageName).exists())
        AttachmentRepository(db,AttachmentStore(context,BackupFixture::syncDirectory)).discardDraft("cancel")
        assertFalse(store.file(abandoned.storageName).exists())
        val file=attachments.import(Uri.fromFile(source()),"draft")
        val id=gallery.create("person",listOf(file.id),"draft")
        val entry=db.personMemoryDao().memories().single()
        val photo=db.personMemoryDao().images().single()
        assertTrue(runCatching {gallery.save(entry.copy(description="Must roll back"),emptyList(),"edit")}.isFailure)
        assertEquals(entry,db.personMemoryDao().memories().single())
        assertEquals(photo,db.personMemoryDao().images().single())
        assertTrue(store.file(file.storageName).exists())
        val failingStore=object:AttachmentStore(context,BackupFixture::syncDirectory) {override fun delete(name:String)=false}
        val failingAttachments=AttachmentRepository(db,failingStore)
        PersonMemoryRepository(db,context,failingAttachments,people).deleteEntry(id)
        assertEquals("DELETE_PENDING",db.attachmentDao().get(file.id)!!.lifecycleState)
        assertTrue(store.file(file.storageName).exists())
        attachments.cleanup()
        assertNull(db.attachmentDao().get(file.id));assertFalse(store.file(file.storageName).exists())
    }
}
