package com.r0ybt.arachn0de.photos

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.r0ybt.arachn0de.backup.*
import com.r0ybt.arachn0de.data.local.*
import com.r0ybt.arachn0de.data.repository.*
import com.r0ybt.arachn0de.security.*
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.*
import java.util.UUID
import java.util.zip.CRC32

@RunWith(RobolectricTestRunner::class) @Config(application=Application::class,sdk=[28])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PrivatePhotoTest {
    @Before fun attachCameraProviderToCurrentSandbox() {
        val app=ApplicationProvider.getApplicationContext<Application>()
        val authority="${app.packageName}.photo-camera"
        val info=requireNotNull(app.packageManager.resolveContentProvider(authority,android.content.pm.PackageManager.GET_META_DATA))
        val provider=CameraCaptureProvider().apply {attachInfo(app,info)}
        org.robolectric.shadows.ShadowContentResolver.registerProviderInternal(authority,provider)
    }
    private val context get()=ApplicationProvider.getApplicationContext<Application>()
    private fun photo(w:Int=96,h:Int=48,png:Boolean=false):ByteArray {
        val bitmap=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(android.graphics.Color.RED)
        val raw=try {ByteArrayOutputStream().also {assertTrue(bitmap.compress(if(png)Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG,95,it))}.toByteArray()} finally {bitmap.recycle()}
        if(png)return raw
        // A known sRGB JPEG fixture without ICC; non-sRGB ICC conversion is tested separately.
        val out=ByteArrayOutputStream();out.write(raw,0,2);var i=2
        while(i+4<raw.size) {
            val marker=raw[i+1].toInt() and 255
            if(marker==0xDA) {out.write(raw,i,raw.size-i);break}
            val n=((raw[i+2].toInt() and 255) shl 8)+(raw[i+3].toInt() and 255)
            if(marker!=0xE2)out.write(raw,i,n+2)
            i+=n+2
        }
        return out.toByteArray()
    }
    private fun segment(marker:Int,bytes:ByteArray)=ByteArrayOutputStream().apply {write(255);write(marker);write((bytes.size+2) shr 8);write((bytes.size+2) and 255);write(bytes)}.toByteArray()
    private fun inject(jpeg:ByteArray,orientation:Int=1):ByteArray {
        val exif=ByteArrayOutputStream().apply {
            write(byteArrayOf(69,120,105,102,0,0))
            val d=DataOutputStream(this)
            fun field(tag:Int,type:Int,count:Int,value:Int) {d.writeShort(tag);d.writeShort(type);d.writeInt(count);d.writeInt(value)}
            d.writeShort(0x4D4D);d.writeShort(42);d.writeInt(8);d.writeShort(4)
            field(0x112,3,1,orientation shl 16)
            field(0x132,2,20,62)
            field(0x110,2,14,82)
            field(0x8825,4,1,96)
            d.writeInt(0)
            d.write("2026:10:23 12:34:56\u0000".toByteArray())
            d.write("PRIVATE-MODEL\u0000".toByteArray())
            d.writeShort(4)
            field(1,2,2,'N'.code shl 24);field(2,5,3,150)
            field(3,2,2,'W'.code shl 24);field(4,5,3,174)
            d.writeInt(0)
            listOf(23,15,30,70,30,1).forEach {d.writeInt(it);d.writeInt(1)}
        }.toByteArray()
        return jpeg.take(2).toByteArray()+segment(225,exif)+segment(225,"http://ns.adobe.com/xap/1.0/\u0000<XMP>gps-private</XMP>".toByteArray())+segment(237,"Photoshop 3.0\u0000IPTC-private".toByteArray())+segment(254,"private-comment".toByteArray())+jpeg.drop(2).toByteArray()+"trailing-private".toByteArray()
    }
    private fun chunk(type:String,bytes:ByteArray):ByteArray=ByteArrayOutputStream().apply {
        val d=DataOutputStream(this);val t=type.toByteArray();d.writeInt(bytes.size);d.write(t);d.write(bytes);d.writeInt(CRC32().apply {update(t);update(bytes)}.value.toInt())
    }.toByteArray()
    @Test fun jpegRemovesAllOpaqueMetadataAndPreservesEncodedPixelsAndOrientation() {
        val source=photo()
        val baseline=PrivatePhoto.clean(source)
        val clean=PrivatePhoto.clean(inject(source))
        assertArrayEquals(baseline.bytes,clean.bytes)
        assertEquals(96,clean.width);assertEquals(48,clean.height)
        val oriented=PrivatePhoto.clean(inject(source,6))
        assertArrayEquals(oriented.bytes,PrivatePhoto.clean(oriented.bytes).bytes)
        val exif=android.media.ExifInterface(oriented.bytes.inputStream())
        assertEquals(6,PrivatePhoto.sourceOrientation(oriented.bytes))
        assertNull(exif.getAttribute(android.media.ExifInterface.TAG_GPS_LATITUDE))
        assertNull(exif.getAttribute(android.media.ExifInterface.TAG_DATETIME))
        assertFalse(String(oriented.bytes,Charsets.ISO_8859_1).contains("private",ignoreCase=true))
        assertFalse(String(oriented.bytes,Charsets.ISO_8859_1).contains("2026:10:23"))
        val bitmap=BitmapFactory.decodeByteArray(oriented.bytes,0,oriented.bytes.size)
        val rotated=PrivatePhoto.orient(bitmap,6)
        assertEquals(48,rotated.width);assertEquals(96,rotated.height);rotated.recycle();bitmap.recycle()
        assertTrue(runCatching {PrivatePhoto.clean(source.copyOf(source.size/2))}.isFailure)
    }
    @Test fun pngKeepsImageDataExactlyWhileDroppingTextExifXmpAndTrailingData() {
        val original=photo(png=true)
        val noRotation=byteArrayOf(77,77,0,42,0,0,0,8,0,1,1,18,0,3,0,0,0,1,0,1,0,0,0,0,0,0)
        assertArrayEquals(PrivatePhoto.clean(original).bytes,PrivatePhoto.clean(original.take(33).toByteArray()+chunk("eXIf",noRotation)+original.drop(33).toByteArray()).bytes)
        val insertion=chunk("tEXt","GPS\u0000private".toByteArray())+chunk("iTXt","XML:com.adobe.xmp\u0000private".toByteArray())+chunk("zTXt","IPTC\u0000private".toByteArray())
        val source=original.take(33).toByteArray()+insertion+original.drop(33).toByteArray()+"private-tail".toByteArray()
        assertArrayEquals(PrivatePhoto.clean(original).bytes,PrivatePhoto.clean(source).bytes)
        val corrupt=source.copyOf().also {it[40]=(it[40].toInt() xor 1).toByte()}
        assertTrue(runCatching {PrivatePhoto.clean(corrupt)}.isFailure)
    }
    @Test fun iccAndPngExifConversionKeepsResolutionAndVisualOrientation() {
        val original=photo()
        val icc=java.awt.color.ICC_Profile.getInstance(java.awt.color.ColorSpace.CS_LINEAR_RGB).data
        val withIcc=original.take(2).toByteArray()+segment(226,"ICC_PROFILE\u0000".toByteArray()+byteArrayOf(1,1)+icc)+original.drop(2).toByteArray()
        val converted=PrivatePhoto.clean(withIcc)
        assertEquals("image/png",converted.mime);assertEquals(96,converted.width);assertEquals(48,converted.height)
        assertFalse(String(converted.bytes,Charsets.ISO_8859_1).contains("ICC_PROFILE"))
        val png=photo(png=true)
        val tiff=byteArrayOf(77,77,0,42,0,0,0,8,0,1,1,18,0,3,0,0,0,1,0,6,0,0,0,0,0,0)
        val oriented=PrivatePhoto.clean(png.take(33).toByteArray()+chunk("eXIf",tiff)+png.drop(33).toByteArray())
        assertEquals(48,oriented.width);assertEquals(96,oriented.height)
        assertFalse(String(oriented.bytes,Charsets.ISO_8859_1).contains("eXIf"))
    }
    @Test fun galleryImportDeduplicatesCleanOriginalAndRestoresSharedReferences()=runBlocking<Unit> {
        context.deleteDatabase("arachn0de.db")
        val db=Arachn0deDatabase.create(context)
        val source=File(context.cacheDir,"gallery-${UUID.randomUUID()}.jpg").apply {writeBytes(inject(photo(3072,128),6))}
        try {
            val avatars=AvatarStore(context,syncDirectory=BackupFixture::syncDirectory)
            val people=PersonRepository(db,avatars);people.save("person","Ana",null)
            val store=AttachmentStore(context,syncDirectory=BackupFixture::syncDirectory)
            val attachments=AttachmentRepository(db,store)
            val memory=PersonMemoryRepository(db,context,attachments,people)
            val first=attachments.importPrivatePhoto(Uri.fromFile(source),"first")
            assertEquals(3072,first.width)
            val original=PrivatePhoto.clean(source.readBytes()).bytes
            assertArrayEquals(original,store.file(first.storageName).readBytes())
            val a=memory.create("person",listOf(first.id),"first")
            val second=attachments.importPrivatePhoto(Uri.fromFile(source),"second")
            assertEquals(first.id,second.id)
            val b=memory.create("person",listOf(second.id),"second")
            val backup=BackupRepository(db,context,BackupAvatarFiles(context,BackupFixture::syncDirectory))
            val out=ByteArrayOutputStream();BackupContainer.writeBackup(backup.snapshot(),out)
            val destination=File(context.cacheDir,"photo-inspect-${UUID.randomUUID()}").apply {mkdirs()}
            val restored=BackupContainer.readBackup(out.toByteArray().inputStream(),destination)
            backup.restore(restored)
            val row=db.attachmentDao().get(first.id)!!
            assertArrayEquals(original,store.file(row.storageName).readBytes())
            val cancelled=attachments.importPrivatePhoto(Uri.fromFile(source),"cancel")
            attachments.discardDraft("cancel")
            assertEquals(first.id,cancelled.id);assertTrue(store.file(row.storageName).exists())
            memory.deleteEntry(a);assertTrue(store.file(row.storageName).exists())
            memory.deleteEntry(b);assertFalse(store.file(row.storageName).exists())
            destination.deleteRecursively()
        } finally {db.close();source.delete()}
    }
    @Test fun cameraUsesFullOutputUriAndCleansOnlyOwnedTemporaryCaptures() {
        val files=CameraCaptureFiles(context,BackupFixture::syncDirectory)
        val id=files.prepare();val uri=files.uri(id)
        val intent=androidx.activity.result.contract.ActivityResultContracts.TakePicture().createIntent(context,uri)
        assertEquals(android.provider.MediaStore.ACTION_IMAGE_CAPTURE,intent.action)
        assertEquals(uri,intent.getParcelableExtra<android.net.Uri>(android.provider.MediaStore.EXTRA_OUTPUT))
        val raw=photo(3072,128)
        context.contentResolver.openOutputStream(uri)!!.use {it.write(raw)}
        assertEquals(3072,PrivatePhoto.clean(files.file(id).readBytes()).width)
        files.recover();assertTrue(files.file(id).exists())
        files.finish(id);assertFalse(files.file(id).exists())
        val interrupted=files.prepare()
        val root=files.file(interrupted).parentFile!!
        val unrelated=File(root,"unrelated.txt").apply {writeText("keep")}
        File(root,"$interrupted.lease").writeText(UUID.randomUUID().toString())
        files.file(interrupted).delete();files.file(interrupted).mkdir()
        val obstruction=File(files.file(interrupted),"block").apply {writeText("temporary")}
        files.recover();assertTrue(files.file(interrupted).exists()) // Restart alone cannot prove abandonment.
        assertTrue(runCatching {files.finish(interrupted)}.isFailure)
        assertTrue(File(root,"$interrupted.lease").exists())
        obstruction.delete();files.recover()
        assertFalse(files.file(interrupted).exists());assertFalse(File(root,"$interrupted.lease").exists())
        assertTrue(unrelated.exists());unrelated.delete()
    }
    @Test fun fullResolutionEncryptedAvatarRestoresAndCancellationPreservesPreviousPhoto()=runBlocking<Unit> {
        val root=File(context.cacheDir,"photo-test-${UUID.randomUUID()}").apply {mkdirs()}
        val access=SecureFiles.Session(UUID.randomUUID(),root,VaultCrypto.randomKey())
        SecureFiles.register(access);SecureFiles.activeProfile=access.id
        val vault=VaultContext(context,access)
        context.deleteDatabase("arachn0de.db")
        val db=Arachn0deDatabase.create(context)
        vault.database=db
        val store=AvatarStore(vault,syncDirectory=BackupFixture::syncDirectory)
        val people=PersonRepository(db,store)
        val source=File(context.cacheDir,"photo-input-${UUID.randomUUID()}.jpg").apply {writeBytes(inject(photo(3072,128)))}
        val original=source.readBytes()
        try {
            val legacyName="${UUID.randomUUID()}.png"
            val legacy=BackupFixture.empty().copy(persons=listOf(PersonEntity("old","Anterior",legacyName)),avatars=mapOf(legacyName to photo(png=true)))
            assertArrayEquals(legacy.avatars.getValue(legacyName),BackupJson.decode(BackupJson.encode(legacy)).avatars.getValue(legacyName))
            val name=people.importAvatar(Uri.fromFile(source))
            assertTrue(name.endsWith(".jpg"))
            people.save("private-photo-person","Persona",name)
            assertArrayEquals(original,source.readBytes())
            val encrypted=File(vault.filesDir,"avatars/$name")
            assertFalse(encrypted.readBytes().take(2).toByteArray().contentEquals(byteArrayOf(-1,-40)))
            assertEquals(3072,PrivatePhoto.clean(SecureFiles.read(encrypted)).width)
            val backup=BackupRepository(db,vault,BackupAvatarFiles(vault,BackupFixture::syncDirectory))
            val before=File(vault.filesDir,"avatars").listFiles().orEmpty().map {it.name}.toSet()
            var checks=0
            assertTrue(runCatching {store.importPhoto(Uri.fromFile(source)) {checks++;if(checks==2)throw kotlinx.coroutines.CancellationException("cancel")}}.isFailure)
            assertEquals(before,File(vault.filesDir,"avatars").listFiles().orEmpty().map {it.name}.toSet())
            val streamed=ByteArrayOutputStream();backup.writeSnapshot(streamed)
            val destination=File(vault.cacheDir,"avatar-inspect-${UUID.randomUUID()}").apply {mkdirs()}
            val streamedData=BackupContainer.readBackup(streamed.toByteArray().inputStream(),destination)
            assertEquals("avatars",streamedData.imageFiles.single().directory)
            backup.restore(streamedData)
            destination.deleteRecursively()
            val snapshot=backup.snapshot()
            val bytes=BackupJson.encode(snapshot)
            assertEquals(48,org.json.JSONObject(String(bytes)).getInt("dataVersion"))
            val restored=BackupJson.decode(bytes)
            backup.restore(restored)
            val current=db.personDao().get("private-photo-person")!!.avatarFile!!
            assertTrue(current.endsWith(".jpg"));assertArrayEquals(restored.avatars.values.single(),SecureFiles.read(File(vault.filesDir,"avatars/$current")))
            val cancelled=people.importAvatar(Uri.fromFile(source))
            people.discardAvatar(cancelled)
            assertFalse(File(vault.filesDir,"avatars/$cancelled").exists())
            assertTrue(File(vault.filesDir,"avatars/$current").exists())
            val shared=people.importAvatar(Uri.fromFile(source))
            people.save("shared-a","A",shared);people.save("shared-b","B",shared)
            people.delete("shared-a");assertTrue(File(vault.filesDir,"avatars/$shared").exists())
            people.delete("shared-b");assertFalse(File(vault.filesDir,"avatars/$shared").exists())
        } finally {db.close();source.delete();SecureFiles.revoke(access);SecureFiles.activeProfile=null;root.deleteRecursively()}
    }
}
