package com.r0ybt.arachn0de.ui.theme

import android.app.Application
import android.content.ContextWrapper
import android.graphics.Bitmap
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Test
import kotlinx.coroutines.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28],application=Application::class)
class CardPhotoMemoryTest {
    @Test fun concurrentLoadsShareResultAndReuseItOnReturn()=runBlocking {
        val context=ApplicationProvider.getApplicationContext<Application>()
        val cache=CardPhotoBitmaps()
        val release=CompletableDeferred<Unit>()
        val bitmap=Bitmap.createBitmap(64,64,Bitmap.Config.ARGB_8888)
        var reads=0
        val first=async {cache.loadPhoto(context,"same") {reads++;release.await();bitmap}}
        yield()
        val second=async {cache.loadPhoto(context,"same") {reads++;bitmap}}
        yield();assertEquals(1,reads)
        release.complete(Unit)
        assertSame(bitmap,first.await());assertSame(bitmap,second.await())
        assertSame(bitmap,cache.loadPhoto(context,"same") {error("Unnecessary reload")})
        cache.clearPhotos()
        assertNull(cache.photo(context,"same"))
    }
    @Test fun clearingDuringLoadRejectsLatePhotoAndFailedLoadsCanRetry()=runBlocking {
        val context=ApplicationProvider.getApplicationContext<Application>()
        val cache=CardPhotoBitmaps()
        val release=CompletableDeferred<Unit>()
        val bitmap=Bitmap.createBitmap(64,64,Bitmap.Config.ARGB_8888)
        val old=async {cache.loadPhoto(context,"old") {release.await();bitmap}}
        yield();cache.clearPhotos();release.complete(Unit)
        assertNull(old.await());assertNull(cache.photo(context,"old"))
        assertTrue(runCatching {cache.loadPhoto(context,"retry") {error("Decode failure")}}.isFailure)
        assertSame(bitmap,cache.loadPhoto(context,"retry") {bitmap})
    }
    @Test fun reusesPhotoBoundsMemoryAndSeparatesStorageContexts() {
        val context=ApplicationProvider.getApplicationContext<Application>()
        val cache=CardPhotoBitmaps()
        val bitmap=Bitmap.createBitmap(64,64,Bitmap.Config.ARGB_8888)
        assertNull(cache.photo(context,"photo:144"))
        cache.retainPhoto(context,"photo:144",bitmap)
        assertSame(bitmap,cache.photo(context,"photo:144"))
        assertNull(cache.photo(context,"photo:288"))
        repeat(6) {i->cache.retainPhoto(context,"large:$i",Bitmap.createBitmap(512,512,Bitmap.Config.ARGB_8888))}
        assertNull(cache.photo(context,"photo:144"))
        assertFalse(bitmap.isRecycled)
        val other=ContextWrapper(context)
        assertNull(cache.photo(other,"large:5"))
        cache.retainPhoto(context,"stale",bitmap)
        assertNull(cache.photo(other,"stale"))
    }
}
