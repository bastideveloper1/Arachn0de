package com.r0ybt.arachn0de.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ArrowDropDown
import com.r0ybt.arachn0de.domain.model.Node
import com.r0ybt.arachn0de.ui.theme.Arachn0deColors
import com.r0ybt.arachn0de.ui.theme.SemanticColors
import kotlin.math.roundToInt
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.ui.semantics.selected
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.r0ybt.arachn0de.data.local.toNode
import com.r0ybt.arachn0de.data.repository.*
import com.r0ybt.arachn0de.domain.model.WorkState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@OptIn(ExperimentalLayoutApi::class)
@Composable internal fun ManagedKanbanDialog(context:String,repository:KanbanRepository,onOpenNode:(String)->Unit,onPlanning:()->Unit,inline:Boolean=false,description:String="",onEditDescription:(()->Unit)?=null,modifier:Modifier=Modifier,onDismiss:()->Unit) {
    val state by remember(repository,context) {repository.observe(context)}.collectAsState(initial=IterationState())
    var selectingSprint by rememberSaveable(context) {mutableStateOf(false)}
    var selectedPhase by rememberSaveable(context) {mutableStateOf<String?>(null)}
    var view by rememberSaveable(context) {mutableStateOf(KanbanScope.SPRINT)}
    var sprint by rememberSaveable(context) {mutableStateOf<String?>(null)}
    var query by rememberSaveable(context) {mutableStateOf("")}
    var collapsed by rememberSaveable(context) {mutableStateOf(listOf<String>())}
    var changing by rememberSaveable(context) {mutableStateOf<String?>(null)}
    var busy by remember {mutableStateOf(false)};var error by remember {mutableStateOf<String?>(null)}
    val scope=rememberCoroutineScope()
    fun perform(action:suspend ()->Unit) {if(busy) return;busy=true;error=null;scope.launch {
        try {action()} catch(c:CancellationException) {throw c} catch(e:Exception) {error=e.message ?: "No se pudo actualizar la tarea."} finally {busy=false}
    }}
    val self by repository.relations.self.collectAsState(initial=null)
    val traditional by repository.assignments.collectAsState(initial=emptyList())
    var ownership by rememberSaveable(context) {mutableStateOf(WorkOwnership.ALL)}
    val responsibility=remember(state.people.records.nodes,traditional) {workResponsibilities(state.people.records.nodes,traditional.map {it.nodeId to it.id})}
    val storyRefs by repository.relations.stories.collectAsState(initial=emptyList())
    val tasks=kanbanTasks(state,context,view,sprint,query).filter {matchesOwnership(it.id,ownership,self?.personId,responsibility)}.map {it.toNode(false)}
    val nodes=state.work.nodes.associateBy {it.id}
    val sprints=state.work.nodes.filter {it.purpose=="LAYER" && it.sprintMode && state.work.owners[it.id]==context}
    LaunchedEffect(sprints,state.records.plans) {
        if(sprint==null || sprints.none {it.id==sprint}) sprint=state.records.plans.firstOrNull {it.contextId==context && it.state=="ACTIVE" && sprints.any {s->s.id==it.sprintId}}?.sprintId ?: sprints.firstOrNull()?.id
    }
    val enabled=state.work.contexts.any {it.id==context && it.enabled}
    val plan=state.records.plans.firstOrNull {it.contextId==context && it.sprintId==sprint}
    val content:@Composable ()->Unit = {
        Surface(modifier.fillMaxSize().safeDrawingPadding()) {Column(Modifier.padding(12.dp)) {
            Text("Kanban",style=MaterialTheme.typography.titleLarge)
            error?.let {Text(it,color=MaterialTheme.colorScheme.error)}
            LazyColumn(Modifier.weight(1f).fillMaxWidth().testTag("kanban-list"),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                if(description.isNotBlank() || onEditDescription!=null) item {Card(onClick={onEditDescription?.invoke()},enabled=onEditDescription!=null,modifier=Modifier.fillMaxWidth()) {Column(Modifier.padding(12.dp)) {Text("Descripción",style=MaterialTheme.typography.labelLarge);Text(description.ifBlank {"Añadir descripción"})}}}
                item {OutlinedTextField(query,{query=it},label={Text("Buscar tareas u origen")},singleLine=true,modifier=Modifier.fillMaxWidth())}
                item {Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min).testTag("kanban-filters"),horizontalArrangement=Arrangement.spacedBy(4.dp)) {
                    KanbanSelector("Ámbito",view.label,listOf(KanbanScope.SPRINT,KanbanScope.PROJECT,KanbanScope.BACKLOG).map {it to it.label},enabled && !busy,Modifier.weight(1f).fillMaxHeight()) {view=it}
                    KanbanSelector("Responsable",ownership.label,WorkOwnership.entries.map {it to it.label},true,Modifier.weight(1f).fillMaxHeight()) {ownership=it}
                    val phases=listOf(null to "Todas")+WorkState.entries.map {it.name to it.label}+if(tasks.any {it.workState==null}) listOf("unassigned" to "Sin fase Sprint") else emptyList()
                    KanbanSelector("Fase",phases.firstOrNull {it.first==selectedPhase}?.second ?: "Todas",phases,true,Modifier.weight(1f).fillMaxHeight()) {selectedPhase=it}
                }}
                if(ownership==WorkOwnership.MINE && self==null) item {Text("Identifica una Persona como Yo desde su ficha General.")}
                if(!enabled) item {Text("La gestión está desactivada. Los datos se conservan.")}
                if(view==KanbanScope.SPRINT) {
                    if(sprints.isEmpty()) item {Column {Text("No hay Capas Sprint en este contexto.");Button(onClick=onPlanning) {Text("Crear un Sprint")}}}
                    else item {Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                        TextButton(enabled=enabled && !busy,onClick={selectingSprint=true},modifier=Modifier.weight(1f)) {Text("Sprint seleccionado: ${nodes[sprint]?.title ?: "Seleccionar Sprint"}",style=MaterialTheme.typography.bodySmall)}
                        if(plan!=null) TextButton(enabled=!busy,onClick=onPlanning,modifier=Modifier.weight(1f)) {Text("Planificación e historial",style=MaterialTheme.typography.bodySmall)}
                    }}
                    if(sprint==null || sprints.none {it.id==sprint}) item {Text("Selecciona un Sprint para consultar sus tareas.")}
                    else item {
                        state.progress(requireNotNull(sprint))?.let {progress->
                            Text("Progreso Sprint · ${progress.roundToInt()} %",style=MaterialTheme.typography.labelSmall)
                            LinearProgressIndicator(progress={(progress/100.0).toFloat()},modifier=Modifier.fillMaxWidth().height(4.dp),color=SemanticColors.SprintDoing,trackColor=Arachn0deColors.ControlSurface)
                        }
                        if(state.current(requireNotNull(sprint)).any {state.work.owners[it.id]!=context}) Text("El progreso incluye todo el Sprint. Las tareas de otros contextos se consultan desde su Capa original.",style=MaterialTheme.typography.bodySmall)
                        if(plan?.state=="CLOSED") Text("Trabajo actual. El resultado del cierre se conserva en la planificación.",style=MaterialTheme.typography.bodySmall)
                    }
                }
                if(tasks.isEmpty()) item {Text(if(query.isNotBlank()) "Sin coincidencias" else "No hay tareas en esta vista.")}
                for(section in (WorkState.entries.map {it.name}+"unassigned").filter {selectedPhase==null || it==selectedPhase}) {
                    val rows=sprintSectionNodes(tasks,section)
                    if(section!="unassigned" || rows.isNotEmpty()) {
                        val expanded=section !in collapsed && rows.isNotEmpty()
                        item(key="phase:$section") {SprintSectionHeader(if(section=="unassigned") "Sin fase Sprint" else WorkState.valueOf(section).label,rows.size,expanded) {
                            collapsed=if(expanded) collapsed+section else collapsed-section
                        }}
                        if(rows.isEmpty() && selectedPhase==section) item(key="empty:$section") {Text("Sin tareas en esta fase.",style=MaterialTheme.typography.bodySmall)}
                        if(expanded) items(rows,key={"task:${it.id}"}) {node->
                            CompactKanbanTaskCard(node,responsibility[node.id].orEmpty(),busy || !enabled,
                                onOpen={onOpenNode(node.id)},onConfirm={perform {repository.confirm(context,node.id)}},onPhase={changing=node.id})

                        }

                    }
                }
            }
            OutlinedButton(enabled=!busy,onClick=onDismiss) {Text("Cerrar")}
        }}
    }
    if(inline) content() else Dialog(onDismissRequest={if(!busy) onDismiss()},properties=DialogProperties(usePlatformDefaultWidth=false)) {content()}
    if(selectingSprint) Dialog(onDismissRequest={selectingSprint=false},properties=DialogProperties(usePlatformDefaultWidth=false)) {
        Surface(Modifier.fillMaxSize().safeDrawingPadding()) {Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            Text("Seleccionar Sprint",style=MaterialTheme.typography.titleLarge)
            LazyColumn(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(8.dp)) {items(sprints,key={it.id}) {row->
                val visual=sprintCardVisual(row.id,row.title,state,storyRefs.count {it.sprintId==row.id})
                Card(onClick={sprint=row.id;selectingSprint=false},modifier=Modifier.fillMaxWidth().testTag("kanban-sprint:${row.id}").semantics {selected=sprint==row.id},border=BorderStroke(if(sprint==row.id) 2.dp else 1.dp,if(sprint==row.id) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant)) {Column(Modifier.padding(12.dp)) {Text(row.title,style=MaterialTheme.typography.titleMedium);Text(visual.description)}}
            }}
            OutlinedButton(onClick={selectingSprint=false}) {Text("Volver al tablero")}
        }}
    }
    changing?.let {id->tasks.firstOrNull {it.id==id}?.workState?.let {phase->WorkStateDialog(nodes[id]?.title.orEmpty(),phase,busy,{changing=null}) {chosen->perform {repository.phase(context,id,chosen);changing=null}}}}
}

@Composable private fun <T> KanbanSelector(label:String,value:String,choices:List<Pair<T,String>>,enabled:Boolean,modifier:Modifier,onSelect:(T)->Unit) {
    var expanded by remember {mutableStateOf(false)}
    Box(modifier) {
        TextButton(onClick={expanded=true},enabled=enabled,modifier=Modifier.fillMaxSize().heightIn(min=48.dp).testTag("kanban-filter:$label"),contentPadding=PaddingValues(horizontal=4.dp,vertical=4.dp)) {
            Column(Modifier.weight(1f)) {Text(label,style=MaterialTheme.typography.labelSmall);Text(value,style=MaterialTheme.typography.bodySmall)}
            Icon(Icons.Default.ArrowDropDown,null,Modifier.size(16.dp))
        }
        DropdownMenu(expanded,{expanded=false}) {choices.forEach {(key,text)->
            DropdownMenuItem(text={Text(text)},modifier=Modifier.semantics {selected=value==text},onClick={expanded=false;onSelect(key)})
        }}
    }
}

@Composable private fun CompactKanbanTaskCard(node:Node,people:Set<String>,busy:Boolean,onOpen:()->Unit,onConfirm:()->Unit,onPhase:()->Unit) {
    val phase=node.workState
    val final=phase!=null && phase.next()==phase
    val checked=if(phase!=null) final else node.isCompleted
    val checkEnabled=!busy && !final
    val colors=completionControlColors(phase!=null,node.isCompleted,checkEnabled)
    Card(onClick=onOpen,modifier=Modifier.fillMaxWidth().testTag("kanban-task:${node.id}"),shape=RoundedCornerShape(8.dp),colors=CardDefaults.cardColors(containerColor=Arachn0deColors.Surface)) {
        Column(Modifier.padding(horizontal=8.dp,vertical=4.dp)) {
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                IconButton(onClick=onConfirm,enabled=checkEnabled,modifier=Modifier.size(48.dp).semantics {
                    contentDescription=if(phase!=null) {if(final) "Sprint validado: ${node.title}" else "Confirmar ${phase.label} y avanzar a ${phase.next().label}: ${node.title}"} else if(node.isCompleted) "Marcar pendiente: ${node.title}" else "Completar: ${node.title}"
                    stateDescription=if(checked) "Completada" else "Pendiente"
                }.clip(RoundedCornerShape(10.dp)).background(if(checked) colors.fill else Arachn0deColors.ControlSurface)) {
                    if(checked) Icon(Icons.Default.Check,null,tint=colors.mark)
                    else Box(Modifier.size(14.dp).border(2.dp,colors.border,RoundedCornerShape(4.dp)))
                }
                Text(node.title,Modifier.weight(1f).padding(horizontal=8.dp),style=MaterialTheme.typography.bodyMedium,color=Arachn0deColors.PathHighlight,fontWeight=androidx.compose.ui.text.font.FontWeight.SemiBold,textDecoration=if(node.isCompleted) androidx.compose.ui.text.style.TextDecoration.LineThrough else null)
                FavoriteToggle("node:${node.id}",compactSquare=true)
            }
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                CompactCardIndicators("node:${node.id}",compactNodeFacts(node,System.currentTimeMillis()).copy(people=people),Modifier.weight(1f))
                var menu by remember {mutableStateOf(false)}
                Box {
                    IconButton(enabled=!busy,onClick={menu=true},modifier=Modifier.size(48.dp)) {Icon(Icons.Default.MoreVert,"Opciones Kanban: ${node.title}")}
                    DropdownMenu(menu,{menu=false}) {
                        if(phase!=null) DropdownMenuItem(text={Text("Cambiar fase")},modifier=Modifier.semantics {contentDescription="Cambiar fase: ${node.title}"},onClick={menu=false;onPhase()})
                        DropdownMenuItem(text={Text("Consultar origen")},onClick={menu=false;onOpen()})
                    }
                }
            }
        }
    }
}
