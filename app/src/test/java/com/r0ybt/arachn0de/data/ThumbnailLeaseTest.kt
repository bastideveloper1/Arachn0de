package com.r0ybt.arachn0de.data

import android.app.Application
import android.graphics.Bitmap
import androidx.test.core.app.ApplicationProvider
import com.r0ybt.arachn0de.backup.BackupFixture
import com.r0ybt.arachn0de.data.local.*
import com.r0ybt.arachn0de.data.repository.AttachmentRepository
import com.r0ybt.arachn0de.security.*
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.withLock
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

@RunWith(RobolectricTestRunner::class)
@Config(application=Application::class,sdk=[28])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ThumbnailLeaseTest {
    private class Fixture:AutoCloseable {
        val base=ApplicationProvider.getApplicationContext<Application>()
        val root=File(base.filesDir,"thumbnail-lease-${UUID.randomUUID()}").apply {mkdirs()}
        val owner=SecureFiles.Session(UUID.randomUUID(),root,VaultCrypto.randomKey())
        val context=VaultContext(base,owner)
        val source:File
        val lifecycle=ImageFileLifecycle(context,"project-photos",BackupFixture::syncDirectory)
        init {
            SecureFiles.register(owner);SecureFiles.activeProfile=owner.id
            source=File(File(context.filesDir,"project-photos").apply {mkdirs()},"${UUID.randomUUID()}.png")
            val image=Bitmap.createBitmap(1024,1024,Bitmap.Config.ARGB_8888)
            image.eraseColor(android.graphics.Color.RED)
            SecureFiles.output(source).use {check(image.compress(Bitmap.CompressFormat.PNG,100,it))};image.recycle()
        }
        override fun close() {context.clearMemory();SecureFiles.revoke(owner);root.deleteRecursively()}
    }
    @Test fun waitingAcquisitionGenerationReleaseAndInterruptionRespectReservations()=runBlocking<Unit> {
        for(interrupt in listOf(false,true))Fixture().use {f->
            val acquired=CountDownLatch(1);val proceed=CountDownLatch(1);val gated=AtomicBoolean(false)
            val thread=AtomicReference<Thread>()
            val store=AvatarStore(f.context,"project-photos") {directory->
                BackupFixture.syncDirectory(directory)
                if(directory==f.context.filesDir && gated.compareAndSet(false,true)) {
                    acquired.countDown();check(proceed.await(20,TimeUnit.SECONDS))
                }
            }
            AttachmentRepository.fileOperations.lock()
            val started=CompletableDeferred<Unit>()
            val worker=async(Dispatchers.IO) {
                thread.set(Thread.currentThread());started.complete(Unit)
                try {runCatching {store.readThumbnail(f.source.name,512)}} finally {Thread.interrupted()}
            }
            started.await();assertFalse(worker.isCompleted)
            assertFalse(f.lifecycle.reserved(f.source.name))
            AttachmentRepository.fileOperations.unlock()
            assertTrue(acquired.await(20,TimeUnit.SECONDS))
            // UNDISPATCHED queues behind acquisition before the reader is allowed to continue.
            val guard=async(start=CoroutineStart.UNDISPATCHED) {
                AttachmentRepository.fileOperations.withLock {
                    assertTrue(f.lifecycle.reserved(f.source.name))
                    f.lifecycle.reserve(f.source.name,"editor")
                    store.delete(f.source.name)
                    assertTrue(f.source.isFile)
                    withTimeout(20_000) {
                        while(File(f.context.cacheDir,"project-thumbnails").listFiles().orEmpty().none {it.name.endsWith(".png")})delay(1)
                    }
                    // Publication progresses while the common mutex is held by this competing operation.
                    assertFalse(worker.isCompleted)
                    if(interrupt)thread.get().interrupt()
                }
            }
            proceed.countDown();guard.await()
            val result=worker.await()
            if(interrupt)assertTrue(result.isFailure) else assertEquals(512,requireNotNull(result.getOrThrow()).width)
            assertTrue(f.lifecycle.reserved(f.source.name)) // The independent editor still owns it.
            assertTrue(f.lifecycle.reservations("project-thumbnail-reader").isEmpty())
            AttachmentRepository.fileOperations.withLock {
                f.lifecycle.release(f.source.name,"editor")
                store.cleanup(setOf(f.source.name),emptySet());assertTrue(f.source.exists())
                store.cleanup(emptySet(),emptySet());assertFalse(f.source.exists())
            }
            assertTrue(File(f.context.cacheDir,"project-thumbnails").listFiles().orEmpty().none {it.name.endsWith(".part")})
        }
    }
    @Test fun interruptedPredecessorAndPartialAcquisitionRecoverWithoutTouchingOtherOwners()=runBlocking<Unit> {
        Fixture().use {f->
            val previous=File(f.source.parentFile,"${UUID.randomUUID()}.png");SecureFiles.write(previous,SecureFiles.read(f.source))
            AttachmentRepository.fileOperations.withLock {
                f.lifecycle.reserve(previous.name,"project-thumbnail-reader")
                f.lifecycle.reserve(previous.name,"editor")
            }
            val store=AvatarStore(f.context,"project-photos",BackupFixture::syncDirectory)
            assertNotNull(store.readThumbnail(f.source.name,128))
            assertTrue(f.lifecycle.reservations("project-thumbnail-reader").isEmpty())
            assertTrue(f.lifecycle.reservations("editor").contains(previous.name));assertTrue(previous.isFile)
            val broken=AvatarStore(f.context,"project-photos") {throw IllegalStateException("Injected fsync failure")}
            assertTrue(runCatching {broken.readThumbnail(f.source.name,256)}.isFailure)
            assertFalse(f.lifecycle.reserved(f.source.name))
            assertNotNull(store.readThumbnail(f.source.name,256))
        }
    }
    @Test fun concurrentReadsCorruptionAndReplacementPreserveOriginalBytes()=runBlocking<Unit> {
        Fixture().use {f->
            val store=AvatarStore(f.context,"project-photos",BackupFixture::syncDirectory)
            val cache=File(f.context.cacheDir,"project-thumbnails").apply {mkdirs()}
            val oversize=File(cache,"${UUID.randomUUID()}-${"b".repeat(64)}.png")
            java.io.RandomAccessFile(oversize,"rw").use {it.setLength(ProjectThumbnailCache.MAX_DISK_BYTES+1)}
            oversize.setLastModified(1)
            val unknown=File(cache,"unrelated.keep").apply {writeText("preserved")}
            val before=f.source.readBytes()
            val bitmaps=coroutineScope {List(4) {async(Dispatchers.IO) {requireNotNull(store.readThumbnail(f.source.name,256))}}.awaitAll()}
            bitmaps.forEach {assertEquals(256,it.width);assertEquals(android.graphics.Color.RED,it.getPixel(0,0))}
            assertFalse(oversize.exists());assertEquals("preserved",unknown.readText())
            assertTrue(cache.listFiles().orEmpty().filter {it.name.endsWith(".png")}.sumOf {it.length()}<=ProjectThumbnailCache.MAX_DISK_BYTES)
            assertArrayEquals(before,f.source.readBytes());assertFalse(f.lifecycle.reserved(f.source.name))
            SecureFiles.write(f.source,byteArrayOf(1,2,3))
            assertNull(store.readThumbnail(f.source.name,256));assertFalse(f.lifecycle.reserved(f.source.name))
            assertNull(store.readThumbnail("${UUID.randomUUID()}.png",256))
            val replacement=File(f.source.parentFile,"${UUID.randomUUID()}.png")
            val image=Bitmap.createBitmap(64,64,Bitmap.Config.ARGB_8888).apply {eraseColor(android.graphics.Color.BLUE)}
            SecureFiles.output(replacement).use {image.compress(Bitmap.CompressFormat.PNG,100,it)};image.recycle()
            assertEquals(android.graphics.Color.BLUE,requireNotNull(store.readThumbnail(replacement.name,256)).getPixel(0,0))
            assertTrue(f.lifecycle.reservations("project-thumbnail-reader").isEmpty())
        }
    }
}
