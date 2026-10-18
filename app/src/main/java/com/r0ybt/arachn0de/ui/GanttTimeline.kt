package com.r0ybt.arachn0de.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import com.r0ybt.arachn0de.data.local.GanttAnnotationEntity
import com.r0ybt.arachn0de.gantt.*
import java.time.format.DateTimeFormatter
import kotlin.math.hypot

/** Mobile-only projection. Original horizontal geometry still owns dates, lanes and PNG export. */
internal class VerticalGanttGeometry(val model:GanttCompactModel) {
    companion object {const val DATE=78f;const val HEADER=48f;const val COLUMN=64f;const val DAY=40f}
    val width=model.rowCount*COLUMN
    val height=model.ticks.size*DAY
    fun markerX(marker:GanttMarker)=(marker.y-GanttCompactModel.HEADER)/GanttCompactModel.ROW*COLUMN
    fun markerY(marker:GanttMarker)=(marker.x-GanttCompactModel.LEFT)/GanttCompactModel.CELL*DAY
    fun position(x:Float,y:Float)=model.annotationPosition(
        GanttCompactModel.LEFT+y/DAY*GanttCompactModel.CELL,
        GanttCompactModel.HEADER+x/COLUMN*GanttCompactModel.ROW)
    fun markerAt(x:Float,y:Float)=model.markers.minByOrNull {hypot(markerX(it)-x,markerY(it)-y)}
        ?.takeIf {hypot(markerX(it)-x,markerY(it)-y)<=24f}
}

@Composable internal fun GanttTimeline(document:GanttDocument,modifier:Modifier=Modifier,onOpen:(String)->Unit,
    onCreate:(Long,Double)->Unit={_,_->},onAnnotation:(GanttAnnotationEntity)->Unit={},onMove:(GanttAnnotationEntity)->Unit={}) {
    val model=remember(document) {GanttCompactModel(document)}
    val geometry=remember(model) {VerticalGanttGeometry(model)}
    val density=LocalDensity.current
    val units=density.fontScale.coerceAtLeast(1f)
    val pixels=density.density*units
    val horizontal=rememberScrollState();val vertical=rememberScrollState()
    var preview by remember(document) {mutableStateOf<GanttAnnotationEntity?>(null)}
    val displayed=preview?.let {note->VerticalGanttGeometry(GanttCompactModel(document.copy(annotations=document.annotations.map {if(it.id==note.id) note else it})))} ?: geometry
    val read by rememberUpdatedState(onAnnotation);val save by rememberUpdatedState(onMove);val create by rememberUpdatedState(onCreate)
    val colors=MaterialTheme.colorScheme
    val text=colors.onSurface.toArgb();val muted=colors.onSurfaceVariant.toArgb()
    val border=colors.outline.copy(alpha=.45f).toArgb()
    val plan=Color(GanttPalette.PLAN).toArgb();val progress=Color(GanttPalette.PROGRESS).toArgb()
    if(geometry.width*pixels>100000 || geometry.height*pixels>100000) {
        Text("El período es demasiado grande para esta pantalla. Selecciona un Sprint o un período menor.",modifier);return
    }
    Column(modifier.testTag("gantt-timeline")) {
        // The header stays above the vertical viewport; both task strips share one horizontal offset.
        Row(Modifier.fillMaxWidth().height((VerticalGanttGeometry.HEADER*units).dp).testTag("gantt-task-header")) {
            Box(Modifier.width((VerticalGanttGeometry.DATE*units).dp).fillMaxHeight(),contentAlignment=androidx.compose.ui.Alignment.Center) {
                Text("Fecha",style=MaterialTheme.typography.labelSmall)
            }
            Row(Modifier.weight(1f).horizontalScroll(horizontal).clipToBounds()) {
                repeat(model.rowCount) {index->
                    val item=model.rows.getOrNull(index)
                    Box(Modifier.width((VerticalGanttGeometry.COLUMN*units).dp).fillMaxHeight()
                        .then(if(item!=null) Modifier.testTag("gantt-item:${item.id}").clickable {onOpen(item.id)} else Modifier),
                        contentAlignment=androidx.compose.ui.Alignment.Center) {
                        if(item!=null) Text(model.label(index),style=MaterialTheme.typography.labelMedium,maxLines=1)
                    }
                }
            }
        }
        Row(Modifier.weight(1f).fillMaxWidth().verticalScroll(vertical).testTag("gantt-date-viewport")) {
            Canvas(Modifier.width((VerticalGanttGeometry.DATE*units).dp).height((geometry.height*units).dp).testTag("gantt-dates")) {
                drawContext.canvas.nativeCanvas.apply {
                    save();scale(pixels,pixels)
                    val paint=android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
                    model.ticks.forEachIndexed {index,tick->
                        val y=index*VerticalGanttGeometry.DAY
                        paint.color=if(document.today in tick.start..tick.end) colors.primaryContainer.toArgb() else colors.surface.toArgb()
                        drawRect(0f,y,VerticalGanttGeometry.DATE,y+VerticalGanttGeometry.DAY,paint)
                        paint.color=text;paint.textSize=12f
                        val date=tick.start.format(DateTimeFormatter.ofPattern("d MMM",ganttLocale)).replace(".","")
                        drawText(date,8f,y+19f,paint)
                        paint.color=muted;paint.textSize=9f
                        drawText(tick.start.year.toString(),8f,y+32f,paint)
                        paint.color=border;paint.strokeWidth=.5f
                        drawLine(0f,y+VerticalGanttGeometry.DAY,VerticalGanttGeometry.DATE,y+VerticalGanttGeometry.DAY,paint)
                    }
                    restore()
                }
            }
            Box(Modifier.weight(1f).horizontalScroll(horizontal).clipToBounds()) {
                Canvas(Modifier.testTag("gantt-calendar").width((geometry.width*units).dp).height((geometry.height*units).dp)
                    .pointerInput(geometry,pixels) {
                        awaitEachGesture {
                            val down=awaitFirstDown(requireUnconsumed=false)
                            val hit=geometry.markerAt(down.position.x/pixels,down.position.y/pixels)
                            if(hit!=null) {
                                down.consume()
                                var moved=false;var current=hit.annotation;var pressed=true
                                while(pressed) {
                                    val event=awaitPointerEvent();val change=event.changes.firstOrNull {it.id==down.id} ?: break
                                    pressed=change.pressed
                                    if(pressed && hypot(change.position.x-down.position.x,change.position.y-down.position.y)>viewConfiguration.touchSlop) moved=true
                                    if(moved && pressed) {
                                        val position=geometry.position(change.position.x/pixels,change.position.y/pixels)
                                        current=current.copy(epochDay=position.first,lane=position.second);preview=current
                                    }
                                    change.consume()
                                }
                                preview=null
                                if(pressed) return@awaitEachGesture
                                if(moved) save(current) else read(hit.annotation)
                            }
                        }
                    }
                    .pointerInput(geometry,pixels) {detectTapGestures(onTap={offset->
                        val hit=geometry.markerAt(offset.x/pixels,offset.y/pixels)
                        if(hit!=null) read(hit.annotation) else {
                            val position=geometry.position(offset.x/pixels,offset.y/pixels);create(position.first,position.second)
                        }
                    })}) {
                    drawRect(colors.surface)
                    for(index in 0..model.rowCount) drawLine(Color(border),Offset(index*VerticalGanttGeometry.COLUMN*pixels,0f),Offset(index*VerticalGanttGeometry.COLUMN*pixels,size.height),.5f*density.density)
                    for(index in 0..model.ticks.size) drawLine(Color(border),Offset(0f,index*VerticalGanttGeometry.DAY*pixels),Offset(size.width,index*VerticalGanttGeometry.DAY*pixels),.5f*density.density)
                    drawContext.canvas.nativeCanvas.apply {
                        save();scale(pixels,pixels)
                        val paint=android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
                        model.rows.forEachIndexed {index,item->
                            if(item.start!=null || item.end!=null) {
                                val start=item.start ?: item.end!!;val end=item.end ?: item.start!!
                                val a=model.coordinate(start)*VerticalGanttGeometry.DAY
                                val b=model.coordinate(end.plusDays(1))*VerticalGanttGeometry.DAY
                                val top=maxOf(0f,a);val bottom=minOf(geometry.height,b)
                                val x=(index+.5f)*VerticalGanttGeometry.COLUMN
                                if(bottom>top) {
                                    paint.color=plan;drawRect(x-8f,top,x+8f,bottom,paint)
                                    val fill=minOf(bottom,a+(b-a)*item.progress.coerceIn(0f,1f))
                                    if(fill>top) {paint.color=progress;drawRect(x-8f,top,x+8f,fill,paint)}
                                }
                            }
                        }
                        displayed.model.markers.forEach {marker->
                            val x=displayed.markerX(marker);val y=displayed.markerY(marker)
                            paint.color=GanttPalette.NOTE;drawCircle(x,y,8f,paint)
                            paint.color=android.graphics.Color.WHITE;paint.textSize=9f
                            val number=marker.number.toString();drawText(number,x-paint.measureText(number)/2,y+3f,paint)
                        }
                        restore()
                    }
                }
                displayed.model.markers.forEach {marker->
                    Box(Modifier.offset(x=(displayed.markerX(marker)*units-24).dp,y=(displayed.markerY(marker)*units-24).dp)
                        .size(48.dp).testTag("gantt-note:${marker.annotation.id}").semantics {
                            role=Role.Button
                            contentDescription="Anotación ${marker.number}: ${ganttDate(java.time.LocalDate.ofEpochDay(marker.annotation.epochDay))}"
                            onClick {read(marker.annotation);true}
                        })
                }
            }
        }
        if(model.rows.isEmpty()) Text("No hay historias en este período. Las tareas y relaciones se conservan.",color=colors.onSurfaceVariant)
    }
}
