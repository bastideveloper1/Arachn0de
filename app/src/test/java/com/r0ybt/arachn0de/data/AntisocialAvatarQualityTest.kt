package com.r0ybt.arachn0de.data

import android.app.Application
import android.graphics.Bitmap
import androidx.test.core.app.ApplicationProvider
import com.r0ybt.arachn0de.data.local.AvatarStore
import com.r0ybt.arachn0de.data.local.PrivateImageCache
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.*
import java.io.File
import java.util.UUID

@RunWith(RobolectricTestRunner::class) @GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application=Application::class,sdk=[28])
class AntisocialAvatarQualityTest {
    @Test fun zoomedPortraitLandscapeAndSquareReadEnoughSourcePixelsWithoutRewritingOrDistorting() {
        val context=ApplicationProvider.getApplicationContext<Application>()
        val store=AvatarStore(context)
        for((width,height) in listOf(2048 to 1024,1024 to 2048,2048 to 2048)) {
            val source=File(context.filesDir,"avatars/${UUID.randomUUID()}.png")
            source.parentFile!!.mkdirs()
            val bitmap=Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888)
            source.outputStream().use {bitmap.compress(Bitmap.CompressFormat.PNG,100,it)};bitmap.recycle()
            val original=source.readBytes()
            val legacy=store.readThumbnail(source.name,180)!!
            val framed=store.readFramedThumbnail(source.name,180,3f)!!
            assertTrue("Usar suficientes píxeles del original para el zoom",minOf(framed.width,framed.height)>=540)
            assertTrue(minOf(framed.width,framed.height)>minOf(legacy.width,legacy.height))
            assertEquals(width.toDouble()/height,framed.width.toDouble()/framed.height,0.001)
            assertArrayEquals(original,source.readBytes())
            PrivateImageCache.invalidate(source);source.delete()
        }
    }
}
