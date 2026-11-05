package com.r0ybt.arachn0de.ui

import android.content.ContextWrapper
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.r0ybt.arachn0de.domain.model.*
import com.r0ybt.arachn0de.ui.theme.*

internal data class TaskViewInput(val tree:NodeTreeSnapshot,val project:Project,val parent:String?,val filter:NodeFilter,val calendarNodes:List<Node>?,val people:Map<String,List<Person>>,val tags:TagState,val status:String?) {
    fun calculate():TaskViewCalculation {
        val eligible=calendarNodes?.mapTo(hashSetOf()) {it.id} ?: tree.nodes.filter {
            filter.matches(it,people[it.id].orEmpty().map {p->p.id}.toSet(),tagIds=tags.nodeIds[it.id].orEmpty())
        }.mapTo(hashSetOf()) {it.id}
        return TaskViewCalculation(this,TaskViewProjection.build(tree,project,parent,eligible,status?.let(TaskStatus::valueOf)))
    }
}
internal data class TaskViewCalculation(val source:TaskViewInput,val projection:TaskViewProjection)

/** Activity-retained memory only: no SavedStateHandle, preferences, Room or backup. */
internal class TaskViewSession:ViewModel() {
    var status by mutableStateOf<TaskStatus?>(null)
    fun validate(verificationAvailable:Boolean) {if(status==TaskStatus.VERIFY && !verificationAvailable) status=null}
}
@Composable internal fun rememberTaskViewSession():TaskViewSession {
    val context=LocalContext.current
    val activity=remember(context) {generateSequence(context) {(it as? ContextWrapper)?.baseContext}.filterIsInstance<ComponentActivity>().firstOrNull()}
    return remember(activity) {if(activity==null) TaskViewSession() else ViewModelProvider(activity)[TaskViewSession::class.java]}
}
internal fun taskViewLabel(status:TaskStatus?)=when(status) {null->"Todas";TaskStatus.PENDING->"Pendientes";TaskStatus.VERIFY->"Por verificar";TaskStatus.COMPLETED->"Completadas"}
@Composable internal fun TaskViewIcon(status:TaskStatus?,modifier:Modifier=Modifier.size(20.dp)) {
    if(status==TaskStatus.VERIFY) Icon(androidx.compose.ui.res.painterResource(com.r0ybt.arachn0de.R.drawable.lucide_scan_eye),null,modifier,tint=cardIndicatorColor("verify",true))
    else Icon(when(status) {null->Icons.Default.List;TaskStatus.PENDING->Icons.Default.CheckBoxOutlineBlank;else->Icons.Default.CheckBox},null,modifier)
}
internal data class TaskViewControls(val status:TaskStatus?,val counts:Map<TaskStatus,Int>,val verificationAvailable:Boolean,val expanded:Boolean,val onToggle:()->Unit,val onClose:()->Unit,val onSelect:(TaskStatus?)->Unit,val ready:Boolean=true) {
    val options get()=listOf(null,TaskStatus.PENDING)+(if(verificationAvailable) listOf(TaskStatus.VERIFY) else emptyList())+TaskStatus.COMPLETED
    fun count(status:TaskStatus?)=if(status==null)counts.values.sum() else counts[status] ?: 0
}
@Composable internal fun TaskViewPanel(controls:TaskViewControls) {
    BackHandler(controls.expanded,controls.onClose)
    Surface(color=Arachn0deColors.Surface.copy(alpha=1f),contentColor=Arachn0deColors.TextPrimary,modifier=Modifier.fillMaxWidth().testTag("task-view-selector")) {
        BoxWithConstraints(Modifier.padding(8.dp)) {
            val measurer=rememberTextMeasurer();val density=LocalDensity.current;val style=MaterialTheme.typography.labelMedium
            // Measure the complete active label/count and every inactive count, plus icons,
            // padding and gaps. No screen-width/font-scale breakpoint or truncation.
            val padding=with(density) {52.dp.toPx()}
            val compactWidths=controls.options.associateWith {measurer.measure(controls.count(it).toString(),style,softWrap=false).size.width+padding}
            val fullWidths=controls.options.associateWith {measurer.measure("${taskViewLabel(it)} · ${controls.count(it)}",style,softWrap=false).size.width+padding}
            val required=controls.options.maxOf {active->fullWidths.getValue(active)+compactWidths.filterKeys {it!=active}.values.sum()}+with(density) {(6.dp*(controls.options.size-1)).toPx()}
            val segmented=required<=constraints.maxWidth
            val widths=controls.options.associateWith {if(it==controls.status)fullWidths.getValue(it) else compactWidths.getValue(it)}
            val option:@Composable RowScope.(TaskStatus?)->Unit={status->
                val selected=status==controls.status
                val text=if(!segmented || selected) "${taskViewLabel(status)} · ${controls.count(status)}" else controls.count(status).toString()
                val modifier=Modifier.weight(if(segmented)widths.getValue(status).toFloat() else 1f).heightIn(min=48.dp).testTag("task-view:${status?.name ?: "ALL"}")
                    .semantics {this.selected=selected;contentDescription="${taskViewLabel(status)}, ${controls.count(status)} tareas, ${if(selected) "seleccionada" else "no seleccionada"}"}
                val content:@Composable RowScope.()->Unit={TaskViewIcon(status);Spacer(Modifier.width(4.dp));Text(text,style=style,softWrap=true)}
                if(selected) FilledTonalButton({controls.onSelect(status)},modifier,enabled=controls.ready,contentPadding=PaddingValues(8.dp),content=content)
                else OutlinedButton({controls.onSelect(status)},modifier,enabled=controls.ready,contentPadding=PaddingValues(8.dp),content=content)
            }
            Column(verticalArrangement=Arrangement.spacedBy(6.dp)) {
                (if(segmented)listOf(controls.options) else controls.options.chunked(2)).forEach {row->
                    Row(Modifier.fillMaxWidth().testTag(if(segmented)"task-view-segments" else "task-view-grid"),horizontalArrangement=Arrangement.spacedBy(6.dp)) {row.forEach {option(it)}}
                }
            }
        }
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable internal fun EmptyTaskViewLayer(node:Node,modifier:Modifier=Modifier,onOpen:()->Unit,onOptions:()->Unit,onSelect:(()->Unit)?=null,selecting:Boolean=false,selected:Boolean=false) {
    Row(modifier.fillMaxWidth().heightIn(min=48.dp).testTag("task-view-empty-layer:${node.id}")
        .clickable(onClick=onOpen).semantics {this.selected=selected;onSelect?.let {select->onLongClick("Seleccionar") {select();true}}}.padding(start=12.dp),
        verticalAlignment=androidx.compose.ui.Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
        if(selecting)Checkbox(selected,{onSelect?.invoke()})
        Box(Modifier.width(HierarchyCardIconSize).height(48.dp),contentAlignment=androidx.compose.ui.Alignment.TopCenter) {
            HierarchyCardIcon(com.r0ybt.arachn0de.R.drawable.bw90_layer,"Capa")
            if(node.isPinned) Icon(Icons.Default.PushPin,"Capa fijada",Modifier.size(14.dp).align(androidx.compose.ui.Alignment.BottomEnd),tint=Arachn0deColors.TextSecondary)
        }
        Text(node.title,Modifier.weight(1f),color=Arachn0deColors.TextSecondary,maxLines=1,overflow=androidx.compose.ui.text.style.TextOverflow.Ellipsis)
        Text("0",color=Arachn0deColors.TextSecondary)
        FavoriteCardIndicator("node:${node.id}")
        Box(Modifier.size(48.dp).testTag("node-options:${node.id}").combinedClickable(onClick=onOptions,onLongClick=onSelect),contentAlignment=androidx.compose.ui.Alignment.Center) {Icon(Icons.Default.MoreVert,"Opciones del elemento")}
    }
}

/** No scrim: scroll gestures pass through; taps/long presses cannot activate obscured intent. */
internal fun Modifier.ignoreTaskViewTaps(enabled:Boolean):Modifier=if(!enabled)this else pointerInput(Unit) {
    awaitEachGesture {
        val down=awaitFirstDown(requireUnconsumed=false,pass=androidx.compose.ui.input.pointer.PointerEventPass.Initial)
        down.consume();var moved=false;var pressed=true
        while(pressed) {
            val event=awaitPointerEvent(androidx.compose.ui.input.pointer.PointerEventPass.Initial)
            if(event.changes.any {(it.position-down.position).getDistance()>viewConfiguration.touchSlop})moved=true
            if(!moved)event.changes.filter {!it.pressed}.forEach {it.consume()}
            pressed=event.changes.any {it.pressed}
        }
    }
}
