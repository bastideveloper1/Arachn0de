package com.r0ybt.arachn0de.document

import android.app.Application
import android.graphics.*
import com.r0ybt.arachn0de.domain.model.DescriptionParser.Format
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.*
import java.io.*
import java.util.zip.ZipInputStream
import javax.xml.parsers.DocumentBuilderFactory

@RunWith(RobolectricTestRunner::class)
@Config(application=Application::class,sdk=[28])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ReportLayoutTest {
    private fun image():ReportImage {val b=Bitmap.createBitmap(400,300,Bitmap.Config.ARGB_8888);b.eraseColor(Color.BLUE);val out=ByteArrayOutputStream();b.compress(Bitmap.CompressFormat.PNG,100,out);b.recycle();return ReportImage(out.toByteArray(),400,300)}
    private fun content(parts:List<ReportPart>):ReportContent=ReportContent(listOf(ReportSection("Proyecto",0,"",emptyList(),parts)),mapOf("image" to image()))
    @Test fun floatingTextUsesBothSidesAndImmediatelyRecoversFullWidthBelowImage() {
        for(side in listOf(ReportImageLayout.LEFT,ReportImageLayout.RIGHT,ReportImageLayout.AUTO)) {
            val data=content(listOf(ReportPart.Text("Antes de la imagen.\n"),ReportPart.Image("image","Muestra"),ReportPart.Text("Texto que rodea la imagen y continúa sin columnas rígidas. ".repeat(100))))
            val plan=ReportLayout.measure(data,ReportOptions("Informe",index=false,images=side))
            val im=plan.pages.first().items.filterIsInstance<ReportDraw.Image>().single().rect
            val lines=plan.pages.first().items.filterIsInstance<ReportDraw.Text>().filter {it.size==11f}
            val alongside=lines.filter {it.baseline-11>=im.top&&it.baseline-11<im.bottom}
            assertTrue(alongside.isNotEmpty())
            if(side==ReportImageLayout.LEFT)assertTrue(alongside.all {it.x>=im.right+9}) else assertTrue(alongside.all {it.x+ReportLayout.paint(it.size,it.formats).measureText(it.value)<=im.left-9})
            val after=lines.filter {it.baseline-11>=im.bottom};assertTrue(after.isNotEmpty());assertTrue(after.any {it.x==ReportLayout.MARGIN})
            assertTrue(after.any {it.x+ReportLayout.paint(it.size,it.formats).measureText(it.value)>ReportLayout.RIGHT-15})
            assertEquals(4f/3f,im.width()/im.height(),.001f)
        }
    }
    @Test fun fullWidthImagesAndMultipleFloatsNeverOverlapTextOrPageFooter() {
        val data=content(List(5) {listOf(ReportPart.Image("image",""),ReportPart.Text("Una descripción después de la imagen. ".repeat(30)))}.flatten())
        for(layout in ReportImageLayout.entries) {
            val plan=ReportLayout.measure(data,ReportOptions("Muestra",images=layout,index=false))
            plan.pages.forEach {page->
                val images=page.items.filterIsInstance<ReportDraw.Image>()
                if(layout==ReportImageLayout.FULL)images.forEach {assertEquals(ReportLayout.RIGHT-ReportLayout.MARGIN,it.rect.width(),.01f)}
                images.forEach {im->assertTrue(im.rect.bottom<=ReportLayout.BOTTOM);assertTrue(im.rect.top>=ReportLayout.MARGIN)}
                page.items.filterIsInstance<ReportDraw.Text>().forEach {t->assertTrue(t.baseline<=ReportLayout.BOTTOM);val r=RectF(t.x,t.baseline-t.size,t.x+ReportLayout.paint(t.size,t.formats).measureText(t.value),t.baseline);assertTrue(images.none {RectF.intersects(it.rect,r)})}
            }
        }
    }
    @Test fun compactCoverIndexAndNumberedHeadingPagesAreConsistentAcrossPageBreaks() {
        val sections=listOf(ReportSection("Raíz",0,"",emptyList(),emptyList()))+(1..110).map {i->ReportSection("Sección $i",1,"$i",listOf("Estado: Completada"),listOf(ReportPart.Text("Descripción extensa ".repeat(50))))}
        val data=ReportContent(sections,emptyMap());val options=ReportOptions("Informe",subtitle="Subtítulo",date="2026-10-19")
        val compact=ReportLayout.measure(data,options);assertTrue(compact.pages.size>5)
        assertTrue(compact.pages.first().items.filterIsInstance<ReportDraw.Text>().any {it.value=="Índice"})
        for(i in 1 until sections.size) {
            val page=compact.pages[compact.sectionPages[i]-1]
            assertTrue(page.items.filterIsInstance<ReportDraw.Text>().any {it.value=="$i Sección $i"})
        }
        val indexRows=compact.pages.flatMap {it.items}.filterIsInstance<ReportDraw.Text>().filter {it.x==ReportLayout.RIGHT-26}
        assertEquals(110,indexRows.size);assertEquals(compact.sectionPages.drop(1).map(Int::toString),indexRows.map {it.value})
        val cover=ReportLayout.measure(data,options.copy(cover=true));assertEquals(compact.pages.size+1,cover.pages.size)
        assertFalse(cover.pages.first().items.filterIsInstance<ReportDraw.Text>().any {it.value=="Índice"})
        val noIndex=ReportLayout.measure(data,options.copy(index=false));assertTrue(noIndex.pages.size<compact.pages.size)
        // An index entry spanning pages keeps its number beside its first line.
        val longIndex=ReportLayout.measure(ReportContent(listOf(sections.first(),sections[1].copy(title="Largo ".repeat(1600))),emptyMap()),options)
        val first=longIndex.pages.first().items.filterIsInstance<ReportDraw.Text>()
        val entry=first.first {it.value.startsWith("1 Largo")}
        val number=first.single {it.x==ReportLayout.RIGHT-26}
        assertEquals(entry.baseline,number.baseline,.01f)

    }
    @Test fun docxHasRealHeadingsTocPageFieldsAnchoredWrapAndEmbeddedImagesWithoutAudio() {
        val data=ReportContent(listOf(ReportSection("Origen",0,"",emptyList(),emptyList()),ReportSection("Tarea & nota <real>",1,"1",listOf("Estado: Pendiente"),listOf(ReportPart.Text("Antes ",setOf(Format.Bold)),ReportPart.Image("image","Imagen"),ReportPart.Text("Texto posterior ".repeat(100),setOf(Format.Italic,Format.Underline))))),mapOf("image" to image()))
        val output=ByteArrayOutputStream();ReportDocx.write(data,ReportOptions("Informe"),output)
        val zip=linkedMapOf<String,ByteArray>();ZipInputStream(output.toByteArray().inputStream()).use {z->while(true) {val e=z.nextEntry ?: break;zip[e.name]=z.readBytes()}}
        val factory=DocumentBuilderFactory.newInstance().apply {isNamespaceAware=true;setFeature("http://apache.org/xml/features/disallow-doctype-decl",true)}
        zip.filterKeys {it.endsWith(".xml")||it.endsWith(".rels")}.forEach {(_,bytes)->factory.newDocumentBuilder().parse(bytes.inputStream())}
        val doc=zip.getValue("word/document.xml").toString(Charsets.UTF_8)
        assertTrue(doc.contains("w:val=\"Heading1\""));assertTrue(doc.contains(" TOC "));assertTrue(doc.contains("PAGEREF section1"));assertTrue(doc.contains("wp:anchor"));assertTrue(doc.contains("wp:wrapSquare"));assertFalse(doc.contains("w:tbl"));assertTrue(doc.contains("w:clear=\"all\""));assertTrue(doc.contains("w:b/"));assertTrue(doc.contains("w:i/"));assertTrue(doc.contains("w:u "))
        assertTrue(doc.indexOf("Antes")<doc.indexOf("wp:anchor"));assertTrue(doc.indexOf("wp:anchor")<doc.indexOf("Texto posterior"))
        assertTrue(zip.getValue("word/footer1.xml").toString(Charsets.UTF_8).contains("NUMPAGES"))
        assertTrue(zip.keys.none {it.endsWith(".aac")||it.endsWith(".mp3")});assertArrayEquals(data.images.getValue("image").bytes,zip.getValue("word/media/image1.png"))
        assertFalse(zip.getValue("word/_rels/document.xml.rels").toString(Charsets.UTF_8).contains("TargetMode=\"External\""))
        File("/tmp/arachnode-report-fixture.docx").writeBytes(output.toByteArray())

    }
    @Test fun portraitLandscapeAndSquareImagesKeepAspectAlignmentAndAllFourCorners() {
        for((w,h) in listOf(1200 to 200,200 to 1200,400 to 400)) {
            val source=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888);val canvas=Canvas(source)
            val colors=listOf(Color.RED,Color.GREEN,Color.YELLOW,Color.MAGENTA)
            colors.forEachIndexed {i,color->canvas.drawRect(if(i%2==0)0f else w/2f,if(i<2)0f else h/2f,if(i%2==0)w/2f else w.toFloat(),if(i<2)h/2f else h.toFloat(),Paint().apply {this.color=color})}
            val bytes=ByteArrayOutputStream().also {source.compress(Bitmap.CompressFormat.PNG,100,it)}.toByteArray();source.recycle()
            val normalized=reportImage(bytes)
            val data=ReportContent(listOf(ReportSection("Raíz",0,"",emptyList(),listOf(ReportPart.Image("im","")))),mapOf("im" to normalized))
            for(mode in ReportImageLayout.entries) {
                val plan=ReportLayout.measure(data,ReportOptions("Informe",images=mode,index=false))
                val page=plan.pages.first {it.items.any {it is ReportDraw.Image}}
                val rect=page.items.filterIsInstance<ReportDraw.Image>().single().rect
                assertEquals(w.toFloat()/h,rect.width()/rect.height(),.001f)
                assertTrue(rect.left>=ReportLayout.MARGIN-.01f&&rect.right<=ReportLayout.RIGHT+.01f&&rect.bottom<=ReportLayout.BOTTOM+.01f)
                if(mode==ReportImageLayout.LEFT||mode==ReportImageLayout.FULL)assertEquals(ReportLayout.MARGIN,rect.left,.01f)
                else assertEquals(ReportLayout.RIGHT,rect.right,.01f)
                val output=Bitmap.createBitmap(ReportLayout.WIDTH,ReportLayout.HEIGHT,Bitmap.Config.ARGB_8888)
                try {
                    ReportLayout.draw(Canvas(output),page,data,1,plan.pages.size)
                    colors.forEachIndexed {i,color->assertEquals(color,output.getPixel((rect.left+rect.width()*(if(i%2==0).25f else .75f)).toInt(),(rect.top+rect.height()*(if(i<2).25f else .75f)).toInt()))}
                } finally {output.recycle()}
            }
        }
    }
    @Test fun indexContainsOnlyElementTitlesAndPageReferencesNeverDescriptionsOrCardMetadata() {
        val data=ReportContent(listOf(ReportSection("Origen",0,"",emptyList(),emptyList()),
            ReportSection("Título tarea",1,"1",listOf("METADATO_SECRETO"),listOf(ReportPart.Text("DESCRIPCIÓN_SECRETA"))),
            ReportSection("Título nota",1,"2",emptyList(),listOf(ReportPart.Text("CONTENIDO_TARJETA")))),emptyMap())
        val plan=ReportLayout.measure(data,ReportOptions("Informe"))
        val rows=plan.pages.flatMap {it.items}.filterIsInstance<ReportDraw.Text>()
        val start=rows.indexOfFirst {it.value=="Índice"}+1
        val end=rows.indexOfFirst {it.value=="1 Título tarea"&&it.size==16f}
        val index=rows.subList(start,end)
        assertEquals(listOf("1 Título tarea","2 Título nota"),index.filter {it.x!=ReportLayout.RIGHT-26}.map {it.value})
        assertTrue(index.none {it.value.contains("SECRETO")||it.value.contains("SECRETA")||it.value.contains("TARJETA")})
        assertTrue(rows.any {it.value.contains("DESCRIPCIÓN_SECRETA")})
    }
    @Test fun previewRendersEveryPlannedPageAndCancellationNeverMutatesImageBytes() {
        val data=content(listOf(ReportPart.Image("image",""),ReportPart.Text("Texto nítido con imagen. ".repeat(1000))))
        val plan=ReportLayout.measure(data,ReportOptions("Informe",index=false))
        plan.pages.forEachIndexed {index,page->
            val bitmap=Bitmap.createBitmap(ReportLayout.WIDTH,ReportLayout.HEIGHT,Bitmap.Config.ARGB_8888)
            try {ReportLayout.draw(Canvas(bitmap),page,data,index+1,plan.pages.size);assertEquals(Color.WHITE,bitmap.getPixel(5,5));assertTrue((40..780 step 20).any {y->(44..550 step 10).any {x->bitmap.getPixel(x,y)!=Color.WHITE}})} finally {bitmap.recycle()}
        }
        val before=data.images.getValue("image").bytes.copyOf()
        assertTrue(runCatching {ReportLayout.measure(data,ReportOptions("Informe")) {throw java.util.concurrent.CancellationException()}}.isFailure)
        assertArrayEquals(before,data.images.getValue("image").bytes)
    }
}
