package com.r0ybt.arachn0de.document

import com.r0ybt.arachn0de.domain.model.DescriptionParser
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.*
import java.io.ByteArrayOutputStream
import java.util.zip.ZipInputStream

@RunWith(RobolectricTestRunner::class)
@Config(application=android.app.Application::class,sdk=[28])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ContentFormatsExportTest {
    @Test fun documentExportsKeepNewStylesAndCodeIndentationWithoutSourceMarkers() {
        val h=DescriptionParser.Format.Highlight.marker;val t=DescriptionParser.Format.Title.marker;val c=DescriptionParser.Format.Code.marker
        val raw="${t}Título$t\n${h}palabra$h\n$c    x = 1\n    return x$c"
        val parts=reportParts(raw,emptySet(),emptySet())
        val content=ReportContent(listOf(ReportSection("Núcleo",0,"",emptyList(),parts)),emptyMap())
        val options=ReportOptions("Contenido",index=false)
        val out=ByteArrayOutputStream();ReportDocx.write(content,options,out)
        var document=""
        ZipInputStream(out.toByteArray().inputStream()).use {zip->while(true) {val e=zip.nextEntry ?: break;if(e.name=="word/document.xml")document=zip.readBytes().toString(Charsets.UTF_8)}}
        assertFalse(document.contains("w:highlight w:val=\"yellow\""));assertTrue(document.contains("Consolas"));assertTrue(document.contains("w:sz w:val=\"26\""))
        assertTrue(document.contains("    x = 1"));assertFalse(document.contains("arachnode:"))
        val plan=ReportLayout.measure(content,options)
        assertTrue(plan.pages.flatMap {it.items}.filterIsInstance<ReportDraw.Text>().any {it.value.startsWith("    ") && DescriptionParser.Format.Code in it.formats})
        val bitmap=android.graphics.Bitmap.createBitmap(ReportLayout.WIDTH,ReportLayout.HEIGHT,android.graphics.Bitmap.Config.ARGB_8888)
        try {
            ReportLayout.draw(android.graphics.Canvas(bitmap),plan.pages.first(),content,1,plan.pages.size)
            assertFalse((40..400).any {y->(40..300).any {x->bitmap.getPixel(x,y)==android.graphics.Color.YELLOW}})
        } finally {bitmap.recycle()}
    }
}
