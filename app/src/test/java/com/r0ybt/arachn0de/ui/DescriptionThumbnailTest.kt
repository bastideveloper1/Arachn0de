package com.r0ybt.arachn0de.ui

import android.app.Application
import android.graphics.Bitmap
import androidx.test.core.app.ApplicationProvider
import com.r0ybt.arachn0de.data.local.*
import com.r0ybt.arachn0de.data.repository.AttachmentRepository
import com.r0ybt.arachn0de.security.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(application=Application::class,sdk=[28])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DescriptionThumbnailTest {
    @Test fun encryptedThumbnailIsBoundedKeepsOriginalAndRejectsOtherOrRevokedSessions()=runBlocking {
        val context=ApplicationProvider.getApplicationContext<Application>()
        val root=File(context.cacheDir,"thumbnail-${UUID.randomUUID()}").apply {mkdirs()}
        val owner=SecureFiles.Session(UUID.randomUUID(),root,ByteArray(32) {7})
        val previous=SecureFiles.activeProfile
        SecureFiles.register(owner);SecureFiles.activeProfile=owner.id
        val storage=VaultContext(context,owner)
        val database=Arachn0deDatabase.create(context)
        val repository=AttachmentRepository(database,AttachmentStore(storage))
        val id=UUID.randomUUID().toString()
        val row=AttachmentFileEntity(id,"$id.png","original.png","image/png",1,2048,1024,"hash",0)
        val file=repository.localFile(row).apply {parentFile!!.mkdirs()}
        val original=Bitmap.createBitmap(2048,1024,Bitmap.Config.ARGB_8888)
        val bytes=java.io.ByteArrayOutputStream().also {original.compress(Bitmap.CompressFormat.PNG,100,it)}.toByteArray()
        original.recycle()
        try {
            SecureFiles.write(file,bytes)
            val encrypted=file.readBytes();val names=root.walkTopDown().filter {it.isFile}.map {it.relativeTo(root).path}.toSet()
            val thumbnail=requireNotNull(readDescriptionThumbnail(repository,row))
            assertEquals(256,thumbnail.width);assertEquals(128,thumbnail.height)
            assertSame(thumbnail,readDescriptionThumbnail(repository,row))
            assertTrue(owner.imageCache.sizeBytes() in 1..PrivateImageCache.MAX_BYTES)
            assertArrayEquals(encrypted,file.readBytes());assertArrayEquals(bytes,SecureFiles.read(file))
            assertEquals(names,root.walkTopDown().filter {it.isFile}.map {it.relativeTo(root).path}.toSet())
            SecureFiles.activeProfile=UUID.randomUUID()
            assertNull(readDescriptionThumbnail(repository,row))
            SecureFiles.activeProfile=owner.id
            assertSame(thumbnail,readDescriptionThumbnail(repository,row))
            SecureFiles.revoke(owner)
            assertEquals(0,owner.imageCache.sizeBytes());assertNull(readDescriptionThumbnail(repository,row))
            assertArrayEquals(encrypted,file.readBytes());assertFalse(thumbnail.isRecycled)
        } finally {
            database.close();SecureFiles.revoke(owner);SecureFiles.activeProfile=previous;root.deleteRecursively()
        }
    }
    @Test fun missingCorruptAudioAndPendingFilesHaveNoThumbnail()=runBlocking {
        val context=ApplicationProvider.getApplicationContext<Application>()
        val database=Arachn0deDatabase.create(context)
        val repository=AttachmentRepository(database,AttachmentStore(context))
        val id=UUID.randomUUID().toString()
        val row=AttachmentFileEntity(id,"$id.png","original.png","image/png",1,1,1,"hash",0)
        val file=repository.localFile(row)
        try {
            assertNull(readDescriptionThumbnail(repository,row))
            file.parentFile!!.mkdirs();file.writeBytes(byteArrayOf(1,2,3))
            assertNull(readDescriptionThumbnail(repository,row))
            assertNull(readDescriptionThumbnail(repository,row.copy(mimeType="audio/aac")))
            assertNull(readDescriptionThumbnail(repository,row.copy(lifecycleState="DELETE_PENDING")))
            assertArrayEquals(byteArrayOf(1,2,3),file.readBytes())
        } finally {file.delete();database.close()}
    }
}
