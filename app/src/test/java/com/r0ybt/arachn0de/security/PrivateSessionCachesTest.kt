package com.r0ybt.arachn0de.security

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.runtime.saveable.SaverScope
import androidx.test.core.app.ApplicationProvider
import com.r0ybt.arachn0de.completion.CompletionSoundCodec
import com.r0ybt.arachn0de.data.local.PrivateImageCache
import com.r0ybt.arachn0de.pomodoro.PomodoroSoundCodec
import com.r0ybt.arachn0de.ui.theme.*
import com.r0ybt.arachn0de.backup.legacy.*
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Files
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(application=Application::class,sdk=[28])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PrivateSessionCachesTest {
    private fun session(root:File,id:UUID=UUID.randomUUID())=SecureFiles.Session(id,root,ByteArray(32) {7})
    private fun bitmap()=Bitmap.createBitmap(40,20,Bitmap.Config.ARGB_8888).apply {eraseColor(android.graphics.Color.RED)}
    private fun retainedEntries(owner:Any,field:String):Int {
        val value=owner.javaClass.getDeclaredField(field).apply {isAccessible=true}.get(owner)
        return when(value) {
            is Map<*,*> -> value.size
            is android.util.LruCache<*,*> -> value.size()
            else -> error("Unexpected cache field")
        }
    }
    private fun png(bitmap:Bitmap)=java.io.ByteArrayOutputStream().also {bitmap.compress(Bitmap.CompressFormat.PNG,100,it)}.toByteArray()

    @Test fun soundValidationKeepsHitsOnlyInItsOwner() {
        val root=Files.createTempDirectory("cache-validation").toFile();val owner=session(root)
        try {
            val wav=ByteBuffer.allocate(124).order(ByteOrder.LITTLE_ENDIAN).apply {
                put("RIFF".toByteArray());putInt(116);put("WAVEfmt ".toByteArray());putInt(16)
                putShort(1);putShort(1);putInt(8000);putInt(8000);putShort(1);putShort(8)
                put("data".toByteArray());putInt(80);put(ByteArray(80) {127})
            }.array()
            val sound=PomodoroSoundCodec.pack(wav);val sounds=PrivateCache<String,ByteArray>(owner.caches,4)
            val first=CompletionSoundCodec.decode(sound,sounds)
            assertSame(first,CompletionSoundCodec.decode(sound,sounds))
            owner.beginClosing()
            CompletionSoundCodec.decode(sound,sounds)
            assertNull(sounds.get(sound));assertEquals(0,retainedEntries(sounds,"values"))
        } finally {owner.close();root.deleteRecursively()}
    }

    @Test fun revocationRejectsLatePreviewEvenAfterSameProfileReopens()=runBlocking<Unit> {
        val root=Files.createTempDirectory("cache-preview").toFile();val owner=session(root)
        val started=CountDownLatch(1);val release=CountDownLatch(1)
        val stale=bitmap();val fresh=bitmap()
        try {
            val oldCache=owner.toolPreviews
            assertSame(stale,oldCache.get("warm") {stale})
            val late=async(Dispatchers.Default) {runCatching {oldCache.get("same") {
                started.countDown();check(release.await(10,TimeUnit.SECONDS));stale
            }}}
            withContext(Dispatchers.IO) {assertTrue(started.await(10,TimeUnit.SECONDS))}
            owner.beginClosing()
            assertEquals(0,retainedEntries(oldCache,"cache"))
            val next=session(root,owner.id)
            try {
                assertSame(fresh,next.toolPreviews.get("same") {fresh})
                release.countDown()
                assertTrue(late.await().exceptionOrNull() is CancellationException)
                assertSame(fresh,next.toolPreviews.get("same") {error("Lost active cache")})
                val other=session(root,UUID.randomUUID())
                try {assertSame(stale,other.toolPreviews.get("same") {stale})} finally {other.close()}
            } finally {next.close()}
            assertTrue(runCatching {oldCache.get("late") {error("Must not render after revocation")}}.exceptionOrNull() is CancellationException)
        } finally {release.countDown();owner.close();root.deleteRecursively()}
    }

    @Test fun lateCardPhotoCannotRepopulateRevokedOwnerAndVisibleBitmapRemainsDrawable()=runBlocking<Unit> {
        val context=ApplicationProvider.getApplicationContext<Application>()
        val root=Files.createTempDirectory("cache-theme").toFile();val owner=session(root)
        val cache=CardPhotoBitmaps(owner.caches);val release=CompletableDeferred<Unit>();val image=bitmap()
        try {
            cache.photo(context,"warm");cache.retainPhoto(context,"warm",image)
            val shown=cache.photo(context,"warm")!!;assertSame(shown,cache.photo(context,"warm"))
            val pending=async {cache.loadPhoto(context,"photo") {release.await();image}}
            yield();owner.beginClosing();release.complete(Unit)
            assertNull(pending.await());assertNull(cache.photo(context,"photo"))
            assertEquals(0,retainedEntries(cache,"photos"))
            cache.retainPhoto(context,"photo",image);assertNull(cache.photo(context,"photo"))
            assertFalse(shown.isRecycled)
            Canvas(bitmap()).drawBitmap(shown,0f,0f,null)
            val afterClose=CardPhotoBitmaps(owner.caches)
            assertNull(afterClose.photo(context,"warm"))
        } finally {release.complete(Unit);owner.close();root.deleteRecursively()}
    }



    @Test fun encryptedThumbnailsAreIsolatedAcrossRevocationAndOriginalBytesStayIntact() {
        val root=Files.createTempDirectory("cache-encrypted").toFile();val owner=session(root)
        SecureFiles.register(owner);SecureFiles.activeProfile=owner.id
        val file=File(root,"image.png");val bytes=png(bitmap())
        try {
            SecureFiles.write(file,bytes);val encrypted=file.readBytes()
            val old=PrivateImageCache.read(file)!!
            assertSame(old,PrivateImageCache.read(file));assertTrue(owner.imageCache.sizeBytes()>0)
            SecureFiles.revoke(owner);assertEquals(0,owner.imageCache.sizeBytes())
            assertTrue(runCatching {PrivateImageCache.read(file)}.isFailure)
            assertArrayEquals(encrypted,file.readBytes());assertFalse(old.isRecycled)
            val next=session(root,owner.id);SecureFiles.register(next);SecureFiles.activeProfile=next.id
            try {
                assertArrayEquals(bytes,SecureFiles.read(file))
                assertNotSame(old,PrivateImageCache.read(file))
            } finally {SecureFiles.revoke(next)}
        } finally {SecureFiles.activeProfile=null;SecureFiles.revoke(owner);root.deleteRecursively()}
    }

    @Test fun legacyImagesRemainReadableWithoutRetainingUnownedContentAfterVaultUse() {
        val root=Files.createTempDirectory("cache-legacy").toFile()
        val store=File(root,"store").apply {mkdirs()};val owner=session(store)
        val legacy=File(root,"legacy.png").apply {writeBytes(png(bitmap()))}
        try {
            SecureFiles.register(owner)
            val first=PrivateImageCache.read(legacy)!!
            assertNotSame(first,PrivateImageCache.read(legacy))
            owner.beginClosing()
            assertNotNull(PrivateImageCache.read(legacy))
            assertEquals(0,PrivateImageCache.sizeBytes())
            assertFalse(first.isRecycled)
            val entries=PrivateImageCache.Entries()
            val generation=entries.generation();entries.clear()
            assertFalse(entries.put("late",first,generation));assertEquals(0,entries.sizeBytes())
        } finally {SecureFiles.revoke(owner);root.deleteRecursively()}
    }


}
