package com.r0ybt.arachn0de.data

import android.app.Application
import android.graphics.Bitmap
import androidx.test.core.app.ApplicationProvider
import com.r0ybt.arachn0de.data.local.AvatarStore
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.*
import java.io.File
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application=Application::class,sdk=[28])
class ProjectPhotoCropQualityTest {
    @Test fun framedPanoramasKeepEnoughShortSidePixelsAndDoNotRewriteOriginals() {
        val context=ApplicationProvider.getApplicationContext<Application>()
        val store=AvatarStore(context,"project-photos")
        for((width,height) in listOf(2048 to 256,256 to 2048,1024 to 1024)) {
            val file=File(context.filesDir,"project-photos/${UUID.randomUUID()}.png")
            file.parentFile!!.mkdirs()
            val image=Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888)
            file.outputStream().use {image.compress(Bitmap.CompressFormat.PNG,100,it)};image.recycle()
            val original=file.readBytes()
            try {
                for(zoom in listOf(1f,2f)) {
                    val decoded=requireNotNull(store.readFramedThumbnail(file.name,96,zoom))
                    assertTrue(minOf(decoded.width,decoded.height)>=96*zoom)
                    assertTrue(maxOf(decoded.width,decoded.height)<=2048)
                    assertEquals(width.toDouble()/height,decoded.width.toDouble()/decoded.height,.001)
                    // Also exercise the encrypted-derivative cache's second read.
                    val cached=requireNotNull(store.readFramedThumbnail(file.name,96,zoom))
                    assertEquals(decoded.width,cached.width);assertEquals(decoded.height,cached.height)
                }
                assertArrayEquals(original,file.readBytes())
            } finally {file.delete()}
        }
    }
}
