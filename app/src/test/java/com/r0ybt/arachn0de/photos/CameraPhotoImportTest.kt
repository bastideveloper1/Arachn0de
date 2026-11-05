package com.r0ybt.arachn0de.photos

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.r0ybt.arachn0de.backup.*
import com.r0ybt.arachn0de.data.local.*
import com.r0ybt.arachn0de.data.repository.*
import com.r0ybt.arachn0de.security.*
import kotlinx.coroutines.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.*
import java.util.UUID

@RunWith(RobolectricTestRunner::class) @Config(application=Application::class,sdk=[28])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CameraPhotoImportTest {
    @Before fun attachCameraProviderToCurrentSandbox() {
        val app=ApplicationProvider.getApplicationContext<Application>()
        val authority="${app.packageName}.photo-camera"
        val info=requireNotNull(app.packageManager.resolveContentProvider(authority,android.content.pm.PackageManager.GET_META_DATA))
        val provider=CameraCaptureProvider().apply {attachInfo(app,info)}
        org.robolectric.shadows.ShadowContentResolver.registerProviderInternal(authority,provider)
    }
    private val base get()=ApplicationProvider.getApplicationContext<Application>()
    private lateinit var access:SecureFiles.Session
    private lateinit var vault:VaultContext
    private lateinit var db:Arachn0deDatabase
    private lateinit var attachments:AttachmentRepository
    private lateinit var files:CameraCaptureFiles
    private lateinit var importer:CameraPhotoImport
    private val captures=mutableListOf<String>()
    @Before fun before() {
        access=SecureFiles.Session(UUID.randomUUID(),File(base.cacheDir,"camera-vault-${UUID.randomUUID()}").apply {mkdirs()},VaultCrypto.randomKey());SecureFiles.register(access)
        vault=VaultContext(base,access);db=Room.inMemoryDatabaseBuilder(vault,Arachn0deDatabase::class.java).allowMainThreadQueries().build();vault.database=db
        attachments=AttachmentRepository(db,AttachmentStore(vault,BackupFixture::syncDirectory))
        files=CameraCaptureFiles(base,BackupFixture::syncDirectory);importer=CameraPhotoImport(files,attachments,access.id)
    }
    @After fun after()=runBlocking<Unit> {
        captures.forEach {runCatching {importer.discard(it)}}
        db.close();vault.clearMemory();SecureFiles.revoke(access);access.root.deleteRecursively()
    }
    private fun jpeg():ByteArray {val b=Bitmap.createBitmap(80,40,Bitmap.Config.ARGB_8888);b.eraseColor(0xff1462a8.toInt());return try {ByteArrayOutputStream().also {check(b.compress(Bitmap.CompressFormat.JPEG,95,it))}.toByteArray()} finally {b.recycle()}}
    private fun capture(bytes:ByteArray=jpeg())=files.prepare(access.id).also {captures+=it;files.file(it).writeBytes(bytes)}
    @Test fun importsBeforeConfirmationAndEditsOnlyEncryptedSourceWithBackupRoundTrip()=runBlocking<Unit> {
        val raw=jpeg();val id=capture(raw);val expected=PrivatePhoto.clean(raw).bytes
        val photo=importer.import(id)
        assertFalse(files.file(id).exists());assertFalse(photo.file.readBytes().contentEquals(expected));assertArrayEquals(expected,SecureFiles.read(photo.file))
        val backup=BackupRepository(db,vault,BackupAvatarFiles(vault,BackupFixture::syncDirectory))
        assertTrue(backup.snapshot().attachmentFiles.isEmpty()) // Unconfirmed camera reservation is operational.
        val people=PersonRepository(db,AvatarStore(vault,syncDirectory=BackupFixture::syncDirectory))
        val avatar=people.importAvatar(Uri.EMPTY,photo.file);people.save("camera-person","Synthetic",avatar)
        attachments.retainGallery("memory-editor",setOf(photo.attachment.id))
        val memories=PersonMemoryRepository(db,vault,attachments,people)
        val memory=memories.create("camera-person",listOf(photo.attachment.id),"memory-editor")
        importer.discard(id);assertTrue(photo.file.exists()) // Handoff keeps committed and shared references.
        val out=ByteArrayOutputStream();BackupContainer.writeBackup(backup.snapshot(),out)
        val inspect=File(vault.cacheDir,"camera-inspect").apply {mkdirs()}
        val restored=BackupContainer.readBackup(out.toByteArray().inputStream(),inspect)
        assertEquals(listOf(photo.attachment.id),restored.attachmentFiles.map {it.id})
        backup.restore(restored);backup.discard(restored)
        val row=requireNotNull(db.attachmentDao().get(photo.attachment.id));assertArrayEquals(expected,SecureFiles.read(attachments.localFile(row)))
        memories.deleteEntry(memory);assertFalse(attachments.localFile(row).exists())
    }
    @Test fun malformedImportPreservesRawAndRecoveryRetriesWithoutAnotherCamera()=runBlocking<Unit> {
        val id=capture(byteArrayOf(1,2,3));assertTrue(runCatching {importer.import(id)}.isFailure)
        assertTrue(files.file(id).exists());assertEquals("RETURNED",files.pending(access.id).single().second.state)
        files.recover();assertTrue(files.file(id).exists())
        files.file(id).writeBytes(jpeg());CameraPhotoImport(files,attachments,access.id).recover()
        assertFalse(files.file(id).exists());assertEquals(1,attachments.pendingImports("camera-$id").size)
        importer.discard(id);assertTrue(attachments.pendingImports("camera-$id").isEmpty())
    }
    @Test fun interruptedAfterAttachmentCommitAuthenticatesBeforeDeletingOriginal()=runBlocking<Unit> {
        val raw=jpeg();val id=capture(raw);files.returned(id,access.id)
        val clean=PrivatePhoto.clean(raw);val row=attachments.importGuestBytes(clean.bytes,"camera-$id","fotografia.jpg")
        val target=attachments.localFile(row);val encrypted=target.readBytes()
        target.writeBytes(encrypted.copyOf().apply {this[lastIndex]=(last().toInt() xor 1).toByte()})
        assertTrue(runCatching {importer.import(id)}.isFailure);assertArrayEquals(raw,files.file(id).readBytes())
        target.writeBytes(encrypted);CameraPhotoImport(files,attachments,access.id).recover()
        assertFalse(files.file(id).exists());assertEquals(row.id,importer.import(id).attachment.id)
    }
    @Test fun concurrentRecoveryAndUiShareOneReservationAndCancellationKeepsOtherEditor()=runBlocking<Unit> {
        val id=capture();val photos=coroutineScope {listOf(async(Dispatchers.IO) {importer.import(id)},async(Dispatchers.IO) {CameraPhotoImport(files,attachments,access.id).import(id)}).awaitAll()}
        assertEquals(photos[0].attachment.id,photos[1].attachment.id)
        val photo=photos.first();attachments.retainGallery("other-editor",setOf(photo.attachment.id));importer.discard(id)
        assertTrue(photo.file.exists());attachments.discardDraft("other-editor");assertFalse(photo.file.exists())
    }
    @Test fun unresolvedAndUnknownCapturesNeverExpireAndOtherVaultCannotImport()=runBlocking<Unit> {
        val id=capture();val before=files.file(id).readBytes();val root=files.file(id).parentFile!!
        val unknown=File(root,"unknown-camera-file.jpg").apply {writeText("synthetic unknown")}
        repeat(3) {CameraCaptureFiles(base,BackupFixture::syncDirectory).recover()}
        assertArrayEquals(before,files.file(id).readBytes());assertTrue(unknown.exists())
        val wrong=CameraPhotoImport(files,attachments,UUID.randomUUID())
        assertTrue(runCatching {wrong.import(id)}.isFailure);assertTrue(runCatching {wrong.discard(id)}.isFailure)
        assertArrayEquals(before,files.file(id).readBytes());importer.discard(id);assertFalse(files.file(id).exists());unknown.delete()
    }
    @Test fun failedRawDeletionKeepsDurableImportAndRetriesWithoutLosingPhoto()=runBlocking<Unit> {
        val id=capture();val failing=CameraCaptureFiles(base,BackupFixture::syncDirectory) {file->if(file.name=="$id.jpg")false else file.delete()}
        assertTrue(runCatching {CameraPhotoImport(failing,attachments,access.id).import(id)}.isFailure)
        assertTrue(files.file(id).exists());assertEquals("IMPORTED",files.pending(access.id).single().second.state)
        val row=attachments.pendingImports("camera-$id").single();val cipher=attachments.localFile(row).readBytes()
        importer.recover();assertFalse(files.file(id).exists());assertArrayEquals(cipher,attachments.localFile(row).readBytes())
        assertEquals(row.id,importer.import(id).attachment.id)
    }
    @Test fun outstandingRequestsAreBoundedAndExplicitCancellationFreesCapacity()=runBlocking<Unit> {
        val legacy=files.prepare();File(files.file(legacy).parentFile,"$legacy.lease").writeText(UUID.randomUUID().toString())
        try {
        repeat(8) {capture()};assertTrue(runCatching {files.prepare(access.id)}.isFailure)
        files.recover();assertEquals(8,files.pending(access.id).size)
        importer.discard(captures.first());val next=capture();assertTrue(files.file(next).exists())
        } finally {files.finish(legacy)}
    }

    @Test fun repeatedPhotoReusesConfirmedButNotAnotherEditorsUnconfirmedAttachment()=runBlocking<Unit> {
        val first=capture();val photo=importer.import(first)
        attachments.retainGallery("existing-editor",setOf(photo.attachment.id));importer.discard(first)
        val second=capture();val separate=importer.import(second)
        assertNotEquals(photo.attachment.id,separate.attachment.id) // Existing isolation between unconfirmed editors.
        importer.discard(second);assertTrue(photo.file.exists())
        val people=PersonRepository(db,AvatarStore(vault,syncDirectory=BackupFixture::syncDirectory));people.save("repeat-person","Synthetic",null)
        val memories=PersonMemoryRepository(db,vault,attachments,people)
        val memory=memories.create("repeat-person",listOf(photo.attachment.id),"existing-editor")
        val third=capture();val reused=importer.import(third)
        assertEquals(photo.attachment.id,reused.attachment.id);importer.discard(third)
        assertTrue(photo.file.exists());memories.deleteEntry(memory);assertFalse(photo.file.exists())
    }

    @Test fun preparationFailureBeforeUriGrantDoesNotLeakAnUnresolvedRequest() {
        var failed=false
        val interrupted=CameraCaptureFiles(base,sync={directory->
            if(!failed && directory.listFiles().orEmpty().any {it.name.endsWith(".jpg")}) {failed=true;throw IOException("Synthetic pre-grant sync failure")}
            BackupFixture.syncDirectory(directory)
        })
        assertTrue(runCatching {interrupted.prepare(access.id)}.isFailure);assertTrue(failed)
        assertTrue(files.pending(access.id).isEmpty())
    }

}
