package com.r0ybt.arachn0de.visual

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.r0ybt.arachn0de.diagrams.*
import com.r0ybt.arachn0de.wireframe.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(application=Application::class,sdk=[28])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ToolPreviewTest {
    @Test fun nativePreviewsKeepPortraitAndLandscapeAspectWithoutChangingDocuments()=runBlocking<Unit> {
        val cache=ToolPreviewCache()
        val context=ApplicationProvider.getApplicationContext<Application>()
        for(format in WireFormat.entries) {
            val document=WireDocument(format=format).insert(WireTemplates.items.first {it.id=="title"})
            val before=WireCodec.encode(document)
            val bitmap=cache.get("test-wire-$format") {WireframeRenderer(context,emptyMap()).use {it.bitmap(document,minOf(240,(240f*format.width/format.height).toInt()).coerceAtLeast(1))}}
            assertTrue(bitmap.width<=240&&bitmap.height<=240)
            assertEquals(format.width.toFloat()/format.height,bitmap.width.toFloat()/bitmap.height,.02f)
            assertSame(bitmap,cache.get("test-wire-$format") {error("Cache miss")})
            assertEquals(before,WireCodec.encode(document))
        }
        for(portrait in listOf(true,false)) {
            val document=diagramTemplate(1,portrait=portrait)
            val before=DiagramCodec.encode(document)
            val bitmap=diagramLibraryBitmap(document,emptyMap())
            assertTrue(bitmap.width<=240&&bitmap.height<=240)
            assertEquals(document.width.toFloat()/document.height,bitmap.width.toFloat()/bitmap.height,.02f)
            assertEquals(before,DiagramCodec.encode(document))
            bitmap.recycle()
        }
    }
}
