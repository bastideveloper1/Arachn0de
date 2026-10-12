package com.r0ybt.arachn0de.gantt

import android.graphics.BitmapFactory
import com.r0ybt.arachn0de.data.local.*
import com.r0ybt.arachn0de.data.repository.*
import java.time.LocalDate
import java.io.ByteArrayOutputStream
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.*

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28],application=android.app.Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class GanttCompactTest {
    private val first=LocalDate.of(2026,10,1)
    private fun document(count:Int=4,days:Int=31,notes:List<GanttAnnotationEntity> = emptyList()):GanttDocument {
        val rows=(1..count).map {GanttItem("story$it","Nombre completo $it",first.plusDays(it.toLong()),first.plusDays(10+it.toLong()),.5f,false,emptySet(),kind="Historia")}
        return GanttDocument("Proyecto","Octubre",first,first.plusDays(days-1L),first,ganttTicks(first,first.plusDays(days-1L),GanttScale.DAY),rows.map {GanttLane(it.id,it.title,listOf(it))},rows,0,count,0,0f,notes,50.0,rows.mapIndexed {i,row->row.id to "HU${i+1}"}.toMap(),rows.associate {it.id to it.id},"Sprint de prueba")
    }
    @Test fun numberingIsPerProjectDeterministicCompactsAndDoesNotRename() {
        fun node(id:String,project:String,created:Long)=NodeEntity(id,project,null,"HU99 — Registrar", "",false,0,created,created,purpose="LAYER")
        val nodes=listOf(node("b","p",20),node("a","p",10),node("c","other",1))
        val roles=nodes.map {ManagementRoleEntity(it.id,"context","STORY")}
        val labels=storyLabels(nodes,roles)
        assertEquals(mapOf("a" to "HU1","b" to "HU2","c" to "HU1"),labels)
        assertEquals(labels,storyLabels(nodes.reversed(),roles.reversed()))
        assertEquals("HU1",storyLabels(nodes.filter {it.id!="a"},roles)["b"])
        assertEquals("HU2 — Registrar",storyDisplayTitle("b",nodes[0].title,labels))
        assertEquals("HU99 — Registrar",nodes[0].title)
        assertEquals("HU2",labels.filterKeys {it=="b"}["b"])
    }
    @Test fun compactGeometryAlignsDatesAndPngUsesSamePaletteWithoutExtraTextPages() {
        val doc=document();val model=GanttCompactModel(doc)
        assertEquals("OCT 2026",model.month(0));assertEquals(1f,model.coordinate(first.plusDays(1)),0f)
        assertEquals(1,GanttRenderer.pages(doc).size)
        val page=GanttRenderer.pages(doc).single();val bytes=ByteArrayOutputStream()
        GanttRenderer.png(doc,page,true,bytes)
        val bitmap=BitmapFactory.decodeByteArray(bytes.toByteArray(),0,bytes.size())!!
        val y=((page.headingHeight+GanttCompactModel.HEADER+9)*2).toInt()
        val start=((2+GanttCompactModel.LEFT+18)*2).toInt()
        assertEquals(GanttPalette.PROGRESS,bitmap.getPixel(start+4,y))
        assertEquals(GanttPalette.PLAN,bitmap.getPixel(start+18*2*8,y))
        assertEquals(GanttPalette.BACKGROUND,bitmap.getPixel(0,0))
        java.io.File("/tmp/arachn0de-gantt-review.png").writeBytes(bytes.toByteArray())
        bitmap.recycle()
    }
    @Test fun markersRemainDistinctAndPositionRoundTripsWithoutChangingExistingRecords() {
        val notes=(1..4).map {GanttAnnotationEntity("note$it","context","Comentario $it",first.plusDays(4).toEpochDay(),.4)}
        val doc=document(notes=notes);val model=GanttCompactModel(doc)
        assertEquals((1..4).toList(),model.markers.map {it.number})
        assertEquals(4,model.markers.map {it.x to it.y}.distinct().size)
        model.markers.zipWithNext().forEach {(a,b)->assertTrue(b.y-a.y>=18f)}
        assertEquals(notes,doc.annotations)
        val single=GanttCompactModel(document(notes=listOf(notes.first())))
        val marker=single.markers.single();val position=single.annotationPosition(marker.x,marker.y)
        assertEquals(notes.first().epochDay,position.first);assertEquals(notes.first().lane,position.second,0.000001)
        assertEquals("story2",marker.story)
    }
    @Test fun paginationIncludesAllHuAndDatesAndCompleteUnicodeCommentsWithinMemoryBound() {
        val text=("☠️ Solicitaron autorización.\n").repeat(100)
        val note=GanttAnnotationEntity("note","context",text,first.plusDays(2).toEpochDay(),.2)
        val doc=document(80,95,listOf(note));val pages=GanttRenderer.pages(doc)
        assertTrue(pages.all {it.width.toLong()*it.height<=4_000_000})
        assertEquals(doc.items.map {it.id}.toSet(),pages.flatMap {it.lanes}.map {it.id}.toSet())
        assertEquals(doc.ticks,pages.filter {it.comments.isEmpty()}.flatMap {it.ticks}.distinct())
        val comments=pages.flatMap {it.comments};assertEquals(text,comments.joinToString("") {it.text})
        assertTrue(comments.all {it.number==1 && it.caption.contains("HU17")})
        val output=ByteArrayOutputStream();GanttRenderer.png(doc,pages.first {it.comments.isNotEmpty()},true,output)
        assertTrue(output.size()>0)
        assertTrue(GanttRenderer.pages(document()).all {it.comments.isEmpty()})
    }
    @Test fun monthBoundaryAndUndatedHuDoNotInventDates() {
        val doc=document();val model=GanttCompactModel(doc.copy(first=LocalDate.of(2026,12,30),last=LocalDate.of(2027,1,2),ticks=ganttTicks(LocalDate.of(2026,12,30),LocalDate.of(2027,1,2),GanttScale.DAY)))
        assertEquals("DIC 2026",model.month(0));assertEquals("ENE 2027",model.month(2))
        val undated=doc.items.first().copy(start=null,end=null)
        assertNull(GanttCompactModel(doc.copy(items=listOf(undated))).rows.single().end)
    }
    @Test fun rendererPropagatesDestinationFailure() {
        val doc=document();val page=GanttRenderer.pages(doc).single()
        assertThrows(Exception::class.java) {GanttRenderer.png(doc,page,true,object:java.io.OutputStream() {override fun write(value:Int) {throw java.io.IOException("Injected destination failure")}})}
    }
}
