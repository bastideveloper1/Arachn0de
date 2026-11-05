package com.r0ybt.arachn0de.slots

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import com.r0ybt.arachn0de.domain.model.*
import com.r0ybt.arachn0de.ui.NodeCard
import com.r0ybt.arachn0de.ui.ProjectCard
import kotlin.math.floor
import kotlin.math.roundToInt

/** The original live NodeCard, scaled as a whole only when the physical aperture needs it. */
@Composable private fun SlotTaskCard(node:Node,tags:TagState,people:Map<String,List<Person>>) {
    SlotCardFit {
        NodeCard(node=node,progress=null,hasChildren=false,canToggleComplete=true,
            onOpen={},onEdit={},onDelete={},canMoveUp=false,canMoveDown=false,onReorder={_,_->},
            onToggleComplete={},showOptions=false,slotPreview=true,
            tags=tags.forNode(node.id),responsiblePeople=people[node.id].orEmpty(),now=System.currentTimeMillis())
    }
}

@Composable private fun SlotCardFit(content:@Composable ()->Unit) {
    Layout(content=content) {measurables,constraints->
        val desiredWidth=constraints.maxWidth
        val card=measurables.single().measure(Constraints.fixed(desiredWidth,constraints.maxHeight))
        val factor=minOf(constraints.maxWidth.toFloat()/card.width,constraints.maxHeight.toFloat()/card.height,1f)
        layout(constraints.maxWidth,constraints.maxHeight) {
            card.placeWithLayer(((constraints.maxWidth-card.width*factor)/2).roundToInt(),((constraints.maxHeight-card.height*factor)/2).roundToInt()) {
                scaleX=factor;scaleY=factor;transformOrigin=androidx.compose.ui.graphics.TransformOrigin(0f,0f)
            }
        }
    }
}

@Composable internal fun SlotTaskWindow(task:TaskSlotSession,revision:Int,busy:Boolean,tags:TagState,people:Map<String,List<Person>>,
    onChoose:(Int)->Unit,onOpen:()->Unit,modifier:Modifier=Modifier) {
    revision // Changes from the ephemeral engine invalidate the wheel's composition.
    val phase=task.machine.phase
    val result=task.result()
    val presentation=if(phase==SlotMachine.Phase.SPINNING || phase==SlotMachine.Phase.FINISHED) modifier.clearAndSetSemantics {
        contentDescription=if(result!=null) "Abrir tarea: ${result.title}" else "Sorteo de tareas"
        if(result!=null)onClick {if(!busy)onOpen();true}
    } else modifier
    BoxWithConstraints(presentation.clipToBounds(),contentAlignment=Alignment.Center) {
        when {
            phase==SlotMachine.Phase.SPINNING && task.wheel.isNotEmpty()-> {
                val position=task.position()
                val current=floor(position).toInt()
                for(step in -1..1) {
                    val index=current+step
                    Box(Modifier.fillMaxSize().offset(y=maxHeight*(position-index))) {
                        SlotTaskCard(task.at(index),tags,people)
                    }
                }
                // Passing cards are purely visual; swallow all touches before their inner controls.
                Box(Modifier.matchParentSize().clearAndSetSemantics {}.pointerInput(Unit) {
                    awaitEachGesture {awaitFirstDown(requireUnconsumed=false,pass=PointerEventPass.Initial).consume()
                        do {val e=awaitPointerEvent(PointerEventPass.Initial);e.changes.forEach {it.consume()}} while(e.changes.any {it.pressed})}
                })
            }
            phase==SlotMachine.Phase.FINISHED && result!=null-> {
                SlotTaskCard(result,tags,people)
                Box(Modifier.matchParentSize().clearAndSetSemantics {
                    contentDescription="Abrir tarea: ${result.title}"
                    onClick {if(!busy)onOpen();true}
                }.pointerInput(result.id,busy) {
                    awaitEachGesture {
                        val down=awaitFirstDown(requireUnconsumed=false,pass=PointerEventPass.Initial)
                        down.consume();var moved=false
                        do {
                            val e=awaitPointerEvent(PointerEventPass.Initial)
                            val change=e.changes.firstOrNull {it.id==down.id}
                            if(change!=null && (change.position-down.position).getDistance()>viewConfiguration.touchSlop)moved=true
                            e.changes.forEach {it.consume()}
                            if(change!=null && !change.pressed && !moved && !busy)onOpen()
                        } while(e.changes.any {it.pressed})
                    }
                })
            }
            phase==SlotMachine.Phase.READY-> {
                Column(Modifier.fillMaxWidth().pointerInput(task.selectedProjectId,busy) {
                    var travel=0f
                    detectVerticalDragGestures(onDragStart={travel=0f},onDragCancel={travel=0f},onDragEnd={
                        if(!busy && kotlin.math.abs(travel)>viewConfiguration.touchSlop)onChoose(if(travel<0)1 else -1)
                    }) {change,amount->change.consume();travel+=amount}
                },horizontalAlignment=Alignment.CenterHorizontally) {
                    val project=task.projects.firstOrNull {it.id==task.selectedProjectId}
                    if(project==null) Text("Núcleo no disponible",Modifier.fillMaxWidth(),maxLines=3,style=MaterialTheme.typography.titleSmall)
                    else Box(Modifier.fillMaxSize().clearAndSetSemantics {contentDescription=project.name}) {
                        SlotCardFit {ProjectCard(slotPreview=true,project=project,alerts=ProjectCardAlerts(),onOpen={},onEdit={},onDelete={},tags=tags.forProject(project.id))}
                        Box(Modifier.matchParentSize().pointerInput(project.id,busy) {
                            awaitEachGesture {
                                val down=awaitFirstDown(requireUnconsumed=false,pass=PointerEventPass.Initial);down.consume()
                                var travel=0f
                                do {val e=awaitPointerEvent(PointerEventPass.Initial);val change=e.changes.firstOrNull {it.id==down.id}
                                    if(change!=null)travel=change.position.y-down.position.y
                                    e.changes.forEach {it.consume()}
                                    if(change!=null && !change.pressed && !busy && kotlin.math.abs(travel)>viewConfiguration.touchSlop)onChoose(if(travel<0)1 else -1)
                                } while(e.changes.any {it.pressed})
                            }
                        })
                    }
                    task.message?.let {Text(it,style=MaterialTheme.typography.labelSmall)}
                }
            }
            else->Text(when {
                task.projects.isEmpty()->"No hay Núcleos con tareas disponibles."
                phase==SlotMachine.Phase.FINISHED->"La tarea ya no está disponible. Introduce otra moneda."
                else->"Toca + para elegir un Núcleo o GIRAR para comenzar."
            },Modifier.padding(8.dp),style=MaterialTheme.typography.bodySmall)
        }
    }
}
