package com.r0ybt.arachn0de.ui

import android.app.Application
import android.graphics.*
import androidx.test.core.app.ApplicationProvider
import com.r0ybt.arachn0de.media.ContentGif
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.*
import org.robolectric.shadows.ShadowSystemClock
import java.time.Duration

@Suppress("DEPRECATION")
@RunWith(RobolectricTestRunner::class)
@Config(application=Application::class,sdk=[28],shadows=[TestGifMovie::class])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ContentGifPlaybackTest {
    private val bytes get()=javaClass.getResourceAsStream("/content/two-frames.gif")!!.use {it.readBytes()}
    @Test fun validatesFramesAndRejectsTruncationOrOversizedCanvas() {
        val data=bytes;assertEquals(ContentGif.Info(2,2,2),ContentGif.inspect(data))
        assertTrue(runCatching {ContentGif.inspect(data.dropLast(1).toByteArray())}.isFailure)
        val large=data.copyOf();large[6]=0;large[7]=32
        assertTrue(runCatching {ContentGif.inspect(large)}.isFailure)
    }
    @Test fun drawsDifferentFramesAndStopsAdvancingWhenNotPlaying() {
        val app=ApplicationProvider.getApplicationContext<Application>()
        val movie=requireNotNull(Movie.decodeByteArray(bytes,0,bytes.size))
        val view=ContentGifView(app);view.layout(0,0,100,100);view.load(movie);view.playing(true)
        fun frame():Int {val b=Bitmap.createBitmap(100,100,Bitmap.Config.ARGB_8888);view.draw(Canvas(b));val c=b.getPixel(50,50);b.recycle();return c}
        val first=frame();ShadowSystemClock.advanceBy(Duration.ofMillis(140));val second=frame()
        assertNotEquals(first,second)
        view.playing(false);val paused=view.drawnTime
        ShadowSystemClock.advanceBy(Duration.ofMillis(100));frame();assertEquals(paused,view.drawnTime)
        view.release();assertEquals(Color.TRANSPARENT,frame())
    }
}

/** Robolectric has no Movie native decoder. Decode the real GIF fixture with the JVM reader
 * to test frame scheduling/drawing and lifecycle, without claiming Android codec coverage. */
@org.robolectric.annotation.Implements(Movie::class)
class TestGifMovie {
    private var frames=listOf<Bitmap>()
    private var time=0
    @org.robolectric.annotation.Implementation fun width()=frames.first().width
    @org.robolectric.annotation.Implementation fun height()=frames.first().height
    @org.robolectric.annotation.Implementation fun duration()=frames.size*120
    @org.robolectric.annotation.Implementation fun setTime(value:Int):Boolean {time=value;return true}
    @org.robolectric.annotation.Implementation fun draw(canvas:Canvas,x:Float,y:Float) {canvas.drawBitmap(frames[(time/120)%frames.size],x,y,null)}
    companion object {
        @JvmStatic @org.robolectric.annotation.Implementation fun decodeByteArray(bytes:ByteArray,offset:Int,length:Int):Movie {
            val input=javax.imageio.ImageIO.createImageInputStream(bytes.copyOfRange(offset,offset+length).inputStream())
            val reader=javax.imageio.ImageIO.getImageReadersByFormatName("gif").next()
            try {
                reader.input=input
                val images=(0 until reader.getNumImages(true)).map {index->
                    val image=reader.read(index)
                    Bitmap.createBitmap(image.width,image.height,Bitmap.Config.ARGB_8888).apply {
                        setPixels(image.getRGB(0,0,image.width,image.height,null,0,image.width),0,image.width,0,0,image.width,image.height)
                    }
                }
                return org.robolectric.shadow.api.Shadow.newInstanceOf(Movie::class.java).also {movie->org.robolectric.shadow.api.Shadow.extract<TestGifMovie>(movie).frames=images}
            } finally {reader.dispose();input.close()}
        }
    }
}
