package com.r0ybt.arachn0de.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.r0ybt.arachn0de.data.local.GanttAnnotationEntity
import com.r0ybt.arachn0de.gantt.*
import kotlin.math.hypot

/** Frozen HU/month gutter and a shared renderer for calendar, bars and annotation hit testing. */
@Composable internal fun GanttTimeline(document:GanttDocument,modifier:Modifier=Modifier,onOpen:(String)->Unit,
    onCreate:(Long,Double)->Unit={_,_->},onAnnotation:(GanttAnnotationEntity)->Unit={},onMove:(GanttAnnotationEntity)->Unit={}) {
    val model=remember(document) {GanttCompactModel(document)}
    val density=LocalDensity.current
    val units=density.fontScale.coerceAtLeast(1f)
    val pixels=density.density*units
    val horizontal=rememberScrollState();val vertical=rememberScrollState()
    var preview by remember(document) {mutableStateOf<GanttAnnotationEntity?>(null)}
    val displayed=preview?.let {note->GanttCompactModel(document.copy(annotations=document.annotations.map {if(it.id==note.id) note else it}))} ?: model
    val read by rememberUpdatedState(onAnnotation);val save by rememberUpdatedState(onMove);val create by rememberUpdatedState(onCreate)
    if(model.width*pixels>100000 || model.height*pixels>100000) {
        Text("El período es demasiado grande para esta pantalla. Selecciona un Sprint o un período menor.",modifier,color=Color(GanttPalette.TEXT));return
    }
    Column(modifier.testTag("gantt-timeline").verticalScroll(vertical)) {
        Row(Modifier.fillMaxWidth()) {
            Box(Modifier.width((GanttCompactModel.LEFT*units).dp).height((model.height*units).dp)) {
                Canvas(Modifier.fillMaxSize()) {
                    drawContext.canvas.nativeCanvas.apply {
                        save();scale(pixels,pixels);clipRect(0f,0f,GanttCompactModel.LEFT,model.height)
                        GanttRenderer.drawChart(this,displayed,columnStart=(horizontal.value/pixels/GanttCompactModel.CELL).toInt().coerceIn(0,model.ticks.lastIndex),columnCount=1)
                        restore()
                    }
                }
                model.rows.forEachIndexed {index,item->
                    // Transparent text exposes the Canvas HU labels to accessibility and UI tests.
                    Text(model.label(index),Modifier.offset(y=((GanttCompactModel.HEADER+index*GanttCompactModel.ROW)*units).dp)
                        .fillMaxWidth().height((GanttCompactModel.ROW*units).dp).testTag("gantt-item:${item.id}").clickable {onOpen(item.id)},color=Color.Transparent)
                }
            }
            Box(Modifier.weight(1f).horizontalScroll(horizontal)) {
                Canvas(Modifier.testTag("gantt-calendar").width(((model.width-GanttCompactModel.LEFT)*units).dp).height((model.height*units).dp)
                    .pointerInput(model,pixels) {
                        awaitEachGesture {
                            val down=awaitFirstDown(requireUnconsumed=false)
                            val hit=model.markerAt(down.position.x/pixels+GanttCompactModel.LEFT,down.position.y/pixels)
                            if(hit!=null) {
                                down.consume()
                                var moved=false;var current=hit.annotation
                                var pressed=true
                                while(pressed) {
                                    val event=awaitPointerEvent();val change=event.changes.firstOrNull {it.id==down.id} ?: break
                                    pressed=change.pressed
                                    if(pressed && hypot(change.position.x-down.position.x,change.position.y-down.position.y)>viewConfiguration.touchSlop) moved=true
                                    if(moved && pressed) {
                                        val position=model.annotationPosition(change.position.x/pixels+GanttCompactModel.LEFT,change.position.y/pixels)
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
                    .pointerInput(model,pixels) {detectTapGestures(onTap={offset->
                        val x=offset.x/pixels+GanttCompactModel.LEFT;val y=offset.y/pixels
                        val hit=model.markerAt(x,y)
                        if(hit!=null) read(hit.annotation) else {
                            val position=model.annotationPosition(x,y);create(position.first,position.second)
                        }
                    })}) {
                    drawContext.canvas.nativeCanvas.apply {save();scale(pixels,pixels);translate(-GanttCompactModel.LEFT,0f);GanttRenderer.drawChart(this,displayed);restore()}
                }
                displayed.markers.forEach {marker->
                    Box(Modifier.offset(x=((marker.x-GanttCompactModel.LEFT)*units-24).dp,y=(marker.y*units-24).dp)
                        .size(48.dp).testTag("gantt-note:${marker.annotation.id}").semantics {
                            role=Role.Button
                            contentDescription="Anotación ${marker.number}: ${ganttDate(java.time.LocalDate.ofEpochDay(marker.annotation.epochDay))}"
                            onClick {read(marker.annotation);true}
                        })
                }
            }
        }
        if(model.rows.isEmpty()) Text("No hay historias en este período. Las tareas y relaciones se conservan.",color=Color(GanttPalette.SECONDARY))
    }
}
