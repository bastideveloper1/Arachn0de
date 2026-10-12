package com.r0ybt.arachn0de.gantt

import com.r0ybt.arachn0de.data.local.GanttAnnotationEntity
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.math.*

internal object GanttPalette {
    const val BACKGROUND=0xff202124.toInt();const val TEXT=0xfff0f0f0.toInt()
    const val SECONDARY=0xffaaaaaa.toInt();const val BORDER=0xff66686a.toInt()
    const val DAY=0xff3d3f42.toInt();const val PLAN=0xff66686c.toInt()
    const val PROGRESS=0xffd8d8d8.toInt();const val NOTE=0xffb86a2c.toInt()
}
internal data class GanttMarker(val annotation:GanttAnnotationEntity,val number:Int,val x:Float,val y:Float,val story:String?)
/** Logical units shared by Android, hit testing and PNG; original lane coordinates remain durable. */
internal class GanttCompactModel(val document:GanttDocument) {
    companion object {const val LEFT=62f;const val CELL=18f;const val ROW=18f;const val HEADER=24f;const val RADIUS=8f}
    val rows=document.items.filter {it.kind=="Historia"}.distinctBy {it.id}.sortedBy {document.storyLabels[it.id]?.removePrefix("HU")?.toIntOrNull() ?: Int.MAX_VALUE}
    val ticks=document.ticks
    fun label(index:Int)=document.storyLabels[rows[index].id] ?: "HU${index+1}"
    fun month(column:Int)=ticks.getOrNull(column)?.start?.format(DateTimeFormatter.ofPattern("MMM yyyy",ganttLocale))?.replace(".","")?.uppercase(ganttLocale).orEmpty()
    fun coordinate(day:LocalDate):Float {
        if(day<document.first) return (day.toEpochDay()-document.first.toEpochDay()).toFloat()/(ticks.first().end.toEpochDay()-ticks.first().start.toEpochDay()+1)
        val i=ticks.indexOfFirst {day<=it.end}
        if(i<0) return ticks.size+(day.toEpochDay()-document.last.toEpochDay()-1).toFloat()/(ticks.last().end.toEpochDay()-ticks.last().start.toEpochDay()+1)
        val tick=ticks[i]
        return i+(day.toEpochDay()-tick.start.toEpochDay()).toFloat()/(tick.end.toEpochDay()-tick.start.toEpochDay()+1)
    }
    private fun originalPosition(note:GanttAnnotationEntity):Pair<Int,Float> {
        if(document.lanes.isEmpty()) return 0 to .5f
        val scaled=note.lane*document.lanes.size
        val lane=floor(scaled).toInt().coerceIn(0,document.lanes.lastIndex)
        val owner=document.lanes[lane].items.firstNotNullOfOrNull {document.storyOwners[it.id] ?: it.id.takeIf {id->rows.any {r->r.id==id}}}
        val row=rows.indexOfFirst {it.id==owner}
        return (if(row>=0) row else floor(note.lane*rows.size.coerceAtLeast(1)).toInt().coerceIn(0,(rows.size-1).coerceAtLeast(0))) to (scaled-lane).toFloat().coerceIn(0f,.999f)
    }
    val markers:List<GanttMarker> = buildList {
        val occupied=mutableMapOf<Pair<Int,Int>,MutableList<Pair<Float,Float>>>()
        val nextY=mutableMapOf<Int,Float>()
        document.annotations.sortedWith(compareBy<GanttAnnotationEntity> {it.epochDay}.thenBy {it.id}).forEachIndexed {index,note->
            val (row,fraction)=originalPosition(note)
            val x=LEFT+coordinate(LocalDate.ofEpochDay(note.epochDay))*CELL+CELL/2
            var y=(HEADER+(row+fraction)*ROW).coerceAtLeast(HEADER+RADIUS)
            val column=floor((x-LEFT)/CELL).toInt()
            fun blocked():Boolean {
                val slot=floor((y-HEADER)/ROW).toInt()
                return (-1..1).any {dx->(-1..1).any {dy->occupied[column+dx to slot+dy].orEmpty().any {(otherX,otherY)->hypot(x-otherX,y-otherY)<18f}}}
            }
            while(blocked()) y=maxOf(y+ROW,(-1..1).maxOf {dx->nextY[column+dx] ?: y+ROW})
            nextY[column]=maxOf(nextY[column] ?: 0f,y+ROW)
            occupied.getOrPut(column to floor((y-HEADER)/ROW).toInt()) {mutableListOf()}.add(x to y)
            val sourceLane=if(document.lanes.isEmpty()) null else document.lanes[(note.lane*document.lanes.size).toInt().coerceIn(0,document.lanes.lastIndex)]
            val associated=sourceLane?.items?.firstNotNullOfOrNull {document.storyOwners[it.id] ?: it.id.takeIf {id->rows.any {r->r.id==id}}}
            add(GanttMarker(note,index+1,x,y,associated))
        }
    }
    val rowCount=maxOf(rows.size,markers.maxOfOrNull {ceil((it.y-HEADER+RADIUS)/ROW).toInt()} ?: 0,1)
    val width=LEFT+ticks.size*CELL
    val height=HEADER+rowCount*ROW
    fun annotationPosition(x:Float,y:Float):Pair<Long,Double> {
        val column=((x-LEFT)/CELL).coerceIn(0f,(ticks.size-.001f).coerceAtLeast(0f))
        val tick=ticks[column.toInt()]
        val days=tick.end.toEpochDay()-tick.start.toEpochDay()+1
        val date=tick.start.toEpochDay()+floor((column-column.toInt())*days).toLong()
        val row=((y-HEADER)/ROW).coerceIn(0f,(rows.size-.001f).coerceAtLeast(0f))
        val id=rows.getOrNull(row.toInt())?.id
        val source=document.lanes.indexOfFirst {lane->lane.items.any {it.id==id}}
        val lane=if(source>=0) ((source+(row-row.toInt()))/document.lanes.size).toDouble() else (row/rows.size.coerceAtLeast(1)).toDouble()
        return date to lane.coerceIn(0.0,1.0)
    }
    fun markerAt(x:Float,y:Float,radius:Float=24f)=markers.minByOrNull {hypot(it.x-x,it.y-y)}?.takeIf {hypot(it.x-x,it.y-y)<=radius}
}
