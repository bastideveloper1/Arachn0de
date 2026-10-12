package com.r0ybt.arachn0de.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.material.icons.filled.Article
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.r0ybt.arachn0de.data.local.*
import com.r0ybt.arachn0de.data.repository.*
import com.r0ybt.arachn0de.domain.model.Person
import com.r0ybt.arachn0de.domain.model.WorkState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId

private data class IterationConfirmation(val title:String,val text:String,val action:suspend ()->Unit)
internal fun planDate(time:Long?)=time?.let {java.time.Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate().toString()}.orEmpty()
private fun parsePlanDate(text:String):Long?=if(text.isBlank()) null else try {LocalDate.parse(text.trim()).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()} catch(e:java.time.DateTimeException) {throw IllegalArgumentException("Usa una fecha válida con formato AAAA-MM-DD.")}

@Composable internal fun IterationDialog(context:String,repository:IterationRepository,onOpenNode:(String)->Unit,onDismiss:()->Unit)=IterationDialog(context,repository,onOpenNode,null,onDismiss)

@OptIn(ExperimentalLayoutApi::class)
@Composable internal fun IterationDialog(context:String,repository:IterationRepository,onOpenNode:(String)->Unit,initialSprintId:String?,onDismiss:()->Unit) {
    val loadedState by remember(repository,context) {repository.observe(context)}.collectAsState<IterationState, IterationState?>(initial=null)
    val state=loadedState ?: IterationState()
    val storyNumbers=remember(state.work.nodes,state.work.records.roles) {storyLabels(state.work.nodes,state.work.records.roles)}
    val peopleRepository=(androidx.compose.ui.platform.LocalContext.current.applicationContext as com.r0ybt.arachn0de.Arachn0deApplication).personRepository
    val people by peopleRepository.observePeople().collectAsState(initial=emptyList())
    var responsible by rememberSaveable {mutableStateOf<String?>(null)}
    val storyRefs by repository.relations.stories.collectAsState(initial=emptyList())
    val self by repository.relations.self.collectAsState(initial=null)
    val traditional by repository.assignments.collectAsState(initial=emptyList())
    var ownership by rememberSaveable(context) {mutableStateOf(WorkOwnership.ALL)}
    val responsibility=remember(state.people.records.nodes,traditional) {workResponsibilities(state.people.records.nodes,traditional.map {it.nodeId to it.id})}
    var sprintTeam by remember {mutableStateOf(false)}
    var showWork by rememberSaveable {mutableStateOf(false)}
    var showHistory by rememberSaveable {mutableStateOf(false)}
    var pickingStory by rememberSaveable {mutableStateOf(false)}
    val records=state.records;val nodes=state.work.nodes.associateBy {it.id}
    var selected by rememberSaveable {mutableStateOf<String?>(null)};var pickingSprint by rememberSaveable {mutableStateOf(false)}
    var initialSelected by rememberSaveable(initialSprintId) {mutableStateOf(false)}
    var pickingTask by rememberSaveable {mutableStateOf(false)};var editing by remember {mutableStateOf<IterationPlanEntity?>(null)}
    var query by rememberSaveable {mutableStateOf("")};var filter by rememberSaveable {mutableStateOf("Todas")}
    var historicalCycle by rememberSaveable {mutableStateOf<String?>(null)}
    var confirmation by remember {mutableStateOf<IterationConfirmation?>(null)}
    var busy by remember {mutableStateOf(false)};var error by remember {mutableStateOf<String?>(null)}
    LaunchedEffect(loadedState,initialSprintId) {
        if(!initialSelected && loadedState!=null && initialSprintId!=null) {
            try {selected=(state.records.plans.firstOrNull {it.sprintId==initialSprintId} ?: repository.configure(context,initialSprintId)).id;initialSelected=true}
            catch(c:CancellationException) {throw c} catch(e:Exception) {error=e.message;initialSelected=true}
        }
    }
    val scope=rememberCoroutineScope()
    fun perform(action:suspend ()->Unit) {if(busy) return;busy=true;error=null;scope.launch {
        try {action()} catch(c:CancellationException) {throw c} catch(e:Exception) {error=e.message ?: "No se pudo guardar la iteración."} finally {busy=false}
    }}
    val plan=records.plans.firstOrNull {it.id==selected && it.contextId==context}
    val history=records.cycles.filter {it.planId==plan?.id}
    val cycle=history.firstOrNull {it.id==historicalCycle} ?: history.lastOrNull()
    val frozen=historicalCycle!=null || plan?.state=="CLOSED"
    val refs=records.commitments.filter {it.cycleId==cycle?.id};val initial=refs.filter {it.initial}.mapTo(hashSetOf()) {it.taskId}
    val snapshots=records.closures.filter {it.cycleId==cycle?.id}.associateBy {it.taskId}
    val live=plan?.let {state.current(it.sprintId)}.orEmpty()
    val current=if(frozen) snapshots.values.filter {it.inScope}.mapTo(hashSetOf()) {it.taskId} else live.mapTo(hashSetOf()) {it.id}
    val added=refs.filterNot {it.initial}.mapTo(hashSetOf()) {it.taskId}
    val removed=refs.mapTo(hashSetOf()) {it.taskId}-current
    fun location(node:NodeEntity)=nodes[node.parentId]?.title ?: "Raíz del proyecto"
    fun relations(task:String):String {
        val stories=state.work.records.storyTasks.filter {it.taskId==task}.map {it.storyId}
        val epics=state.work.records.epicStories.filter {it.storyId in stories}.map {it.epicId}
        return (stories.mapNotNull {nodes[it]?.let {n->storyDisplayTitle(n.id,n.title,storyNumbers)}}.map {"Historia: $it"}+epics.distinct().mapNotNull {nodes[it]?.let {n->storyDisplayTitle(n.id,n.title,storyNumbers)}}.map {"Épica: $it"}).joinToString(" · ")
    }
    fun summary():String {
        val phases=live.count {it.workState in listOf("DONE","VALIDATED")}
        return "Compromiso inicial: ${initial.size}. Alcance actual: ${live.size}. En fase TERMINADA/VALIDADA: $phases. En fases previas: ${live.size-phases}. Checks pendientes: ${live.count {!it.isCompleted}}. Añadidas después: ${added.size}. Retiradas: ${removed.size}.\nFecha de cierre: al confirmar (${formatTaskDate(System.currentTimeMillis())}).\nNo se cambiarán fases, checks, vínculos ni criterios. Se conservará el resultado de este ciclo."
    }
    responsible?.let {id->ResponsibleDialog(id,people,people.filter {person->traditional.any {it.nodeId==id && it.id==person.id}},busy,{responsible=null}) {selectedPeople->perform {require(peopleRepository.setResponsiblePeople(id,selectedPeople)) {"La selección ya no existe."};responsible=null}}}
    fun back() {when {pickingStory->pickingStory=false;pickingTask->pickingTask=false;showWork->showWork=false;showHistory->{showHistory=false;historicalCycle=null};pickingSprint->pickingSprint=false;initialSprintId==null && selected!=null->{selected=null;historicalCycle=null};else->onDismiss()}}
    androidx.activity.compose.BackHandler {if(!busy) back()}
    if(sprintTeam && plan!=null) ParticipationDialog(context,ParticipationRepository((androidx.compose.ui.platform.LocalContext.current.applicationContext as com.r0ybt.arachn0de.Arachn0deApplication).database),target=plan.sprintId) {sprintTeam=false}
    Dialog(onDismissRequest={if(!busy) back()},properties=DialogProperties(usePlatformDefaultWidth=false)) {
        Surface(Modifier.fillMaxSize().safeDrawingPadding()) {Column(Modifier.padding(12.dp)) {
            Text(if(plan==null) "Sprints" else if(pickingStory) "Gestionar historias" else if(showHistory) "Historial de ciclos" else if(showWork) "Trabajo del Sprint" else "Planificar Sprint",style=MaterialTheme.typography.titleLarge)
            error?.let {Text(it,color=MaterialTheme.colorScheme.error)}
            if(loadedState!=null) LazyColumn(Modifier.weight(1f).fillMaxWidth().testTag("iteration-list"),state=rememberLazyListState(),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                if(pickingSprint) {
                    item {OutlinedButton(onClick={pickingSprint=false}) {Text("Volver a iteraciones")};OutlinedTextField(query,{query=it},label={Text("Buscar Sprint existente")},modifier=Modifier.fillMaxWidth())}
                    val available=state.work.nodes.filter {it.purpose=="LAYER" && it.sprintMode && state.work.owners[it.id]==context && records.plans.none {p->p.sprintId==it.id} && it.title.contains(query.trim(),true)}
                    if(available.isEmpty()) item {Text("No hay Sprints disponibles en este contexto. Activa el modo Sprint en una Capa existente.")}
                    items(available,key={it.id}) {node->OutlinedButton(enabled=!busy,onClick={perform {selected=repository.configure(context,node.id).id;pickingSprint=false;query=""}}) {Column(Modifier.fillMaxWidth()) {Text("Planificar: ${node.title}");Text("Origen: ${location(node)}")}}}
                } else if(plan==null) {
                    item {OutlinedButton(enabled=!busy,onClick={pickingSprint=true;query=""}) {Text("Planificar Sprint existente")}}
                    val plans=records.plans.filter {it.contextId==context}
                    if(plans.isEmpty()) item {Text("No hay iteraciones planificadas.")}
                    items(plans,key={it.id}) {p->Card(onClick={selected=p.id;historicalCycle=null;query="";filter="Todas"},modifier=Modifier.fillMaxWidth()) {Column(Modifier.padding(10.dp)) {
                        Text(nodes[p.sprintId]?.title.orEmpty());Text(when(p.state) {"ACTIVE"->"Activa";"CLOSED"->"Cerrada";else->"Preparada"})
                        if(p.goal.isNotBlank()) Text(p.goal,maxLines=2,overflow=TextOverflow.Ellipsis)
                        Text(sprintCardVisual(p.sprintId,nodes[p.sprintId]?.title.orEmpty(),state,storyRefs.count {it.sprintId==p.sprintId}).description)
                    }}}
                } else if(pickingStory) {
                    item {Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {OutlinedTextField(query,{query=it},label={Text("Buscar historias")},modifier=Modifier.fillMaxWidth())}}
                    val candidates=state.work.records.roles.filter {it.role=="STORY" && it.contextId==context}.mapNotNull {nodes[it.nodeId]}.filter {it.title.contains(query.trim(),true)}
                    if(candidates.isEmpty()) item {Text("Crea una historia dentro de una épica del Backlog.")}
                    val ordered=candidates.sortedWith(compareBy<NodeEntity> {node->storyRefs.none {it.storyId==node.id && it.sprintId==plan.sprintId}}.thenBy {it.title.lowercase()})
                    listOf(true,false).forEach {incorporated->
                        val group=ordered.filter {node->(storyRefs.any {it.storyId==node.id && it.sprintId==plan.sprintId})==incorporated}
                        item {Text(if(incorporated) "Incorporadas (${group.size})" else "Disponibles (${group.size})",style=MaterialTheme.typography.titleMedium)}
                        items(group,key={it.id}) {node->
                            val other=storyRefs.firstOrNull {it.storyId==node.id}
                            Card(onClick={if(incorporated) {
                                val ids=storyWorkIds(state.work.nodes,node.id,state.work.records.storyTasks)
                                val expected=state.work.nodes.filter {n->n.id in ids && state.links.any {it.taskId==n.id && it.sprintId==plan.sprintId}}.associate {it.id to it.workState}
                                confirmation=IterationConfirmation("Retirar historia","Se conservarán la Historia, su épica y las referencias históricas. Se descartarán las fases Sprint de ${expected.size} tareas vinculadas; los checks se conservarán.") {repository.relations.removeStory(plan.sprintId,node.id,expected);confirmation=null}
                            } else perform {repository.relations.addStory(plan.sprintId,node.id)}},enabled=!busy && plan.state!="CLOSED" && (other==null || other.sprintId==plan.sprintId),modifier=Modifier.fillMaxWidth().testTag("sprint-story:${node.id}")) {
                                Row(Modifier.padding(12.dp),horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                                    Icon(androidx.compose.material.icons.Icons.Default.Article,contentDescription=null)
                                    Column {Text(storyDisplayTitle(node.id,node.title,storyNumbers),style=MaterialTheme.typography.titleMedium);Text("Épica de origen: ${location(node)}");Text(if(incorporated) "Incorporada · Toca para retirar" else if(other!=null) "En otro Sprint" else "Disponible · Toca para incorporar")}
                                }
                            }
                        }
                    }
                } else if(pickingTask) {
                    item {OutlinedButton(onClick={pickingTask=false;query=""}) {Text("Volver a planificación")};OutlinedTextField(query,{query=it},label={Text("Buscar tareas del proyecto")},modifier=Modifier.fillMaxWidth())}
                    val backlog=state.work.records.entries.filter {it.contextId==context}.mapTo(hashSetOf()) {it.nodeId}
                    val choices=state.work.nodes.filter {state.work.owners[it.id]==context && it.purpose=="ACTION" && it.id !in current && it.title.contains(query.trim(),true)}
                    if(choices.isEmpty()) item {Text("No hay tareas disponibles que coincidan.")}
                    items(choices,key={it.id}) {node->Card(Modifier.fillMaxWidth()) {Column(Modifier.padding(10.dp)) {
                        Text(storyDisplayTitle(node.id,node.title,storyNumbers));Text("Origen: ${location(node)}");val related=relations(node.id);if(related.isNotBlank()) Text(related)
                        val other=state.links.firstOrNull {it.taskId==node.id}?.sprintId ?: nodes[node.parentId]?.takeIf {it.sprintMode}?.id
                        if(other!=null && other!=plan.sprintId) Text("Pertenece a otro Sprint: ${nodes[other]?.title}. Desvincula primero con confirmación de fase.")
                        OutlinedButton(enabled=!busy && (other==null || other==plan.sprintId),onClick={perform {repository.add(plan.id,setOf(node.id))}}) {Text("Incorporar: ${node.title}")}
                    }}}
                } else {
                    item {Column(Modifier.fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                        Text(nodes[plan.sprintId]?.title.orEmpty(),style=MaterialTheme.typography.titleLarge)
                        if(showWork || showHistory) {
                            Text(if(showHistory) "Resultados conservados por ciclo; las fases actuales pueden haber cambiado después." else "Consulta las fases y checks. Ejecuta el trabajo en Kanban o abre su ubicación original.",style=MaterialTheme.typography.bodySmall)
                            if(showWork && !frozen) OutlinedButton(enabled=!busy,onClick={pickingTask=true;query=""}) {Text("Incorporar tarea técnica")}
                            if(showHistory) FlowRow(horizontalArrangement=Arrangement.spacedBy(6.dp)) {history.forEach {c->OutlinedButton(onClick={historicalCycle=if(c.closedAt==null) null else c.id}) {Text("Ciclo ${c.number}${if(c.closedAt!=null) " · Cerrado" else " · Activo"}")}}}
                            if(historicalCycle!=null) OutlinedButton(onClick={historicalCycle=null}) {Text("Ver planificación actual")}
                            Text("Compromiso inicial: ${initial.size} · ${if(frozen) "Alcance al cierre" else "Alcance actual"}: ${current.size}")
                            Text("Añadidas después: ${added.size} · Retiradas: ${removed.size}")
                            FlowRow {listOf("Todas","Compromiso inicial","Alcance","Añadidas después","Retiradas").forEach {label->FilterChip(selected=filter==label,onClick={filter=label},label={Text(label)},modifier=Modifier.heightIn(min=48.dp))}}
                        } else {
                            OutlinedButton(enabled=!busy,onClick={editing=plan}) {Text("Editar planificación")}
                            PlanningSection("Fechas") {
                                Text("Inicio previsto: ${planDate(plan.startAt).ifBlank {"Sin definir"}}")
                                Text("Término previsto: ${planDate(plan.endAt).ifBlank {"Sin definir"}}")
                            }
                            PlanningSection("Objetivo de la iteración") {Text(plan.goal.ifBlank {"Sin objetivo definido"})}
                            PlanningSection("Notas de planificación") {Text(plan.notes.ifBlank {"Sin observaciones"})}
                            PlanningSection("Historias") {
                                Text("Historias incorporadas: ${storyRefs.count {it.sprintId==plan.sprintId}}")
                                Button(enabled=!busy,onClick={pickingStory=true;query=""}) {Text("Gestionar historias")}
                            }
                            PlanningSection("Equipo del Sprint") {
                                Text("Responsabilidades específicas de esta iteración, independientes de los roles del proyecto y de sus Historias.",style=MaterialTheme.typography.bodySmall)
                                OutlinedButton(enabled=!busy,onClick={sprintTeam=true}) {Text("Responsabilidades del Sprint")}
                                OutlinedButton(enabled=!busy,onClick={responsible=plan.sprintId}) {Text("Responsables tradicionales")}
                            }
                            PlanningSection("Ciclo del Sprint") {
                                Text("Estado de planificación: ${sprintStateLabel(plan.state)}",style=MaterialTheme.typography.titleMedium)
                                cycle?.let {c->Text("Inicio del ciclo: ${formatTaskDate(c.startedAt)}");c.closedAt?.let {Text("Cierre registrado: ${formatTaskDate(it)}")}}
                                Text("Progreso actual del motor Sprint: ${state.progress(plan.sprintId)?.let {"${kotlin.math.round(it).toInt()} %"} ?: "Sin tareas"}")
                                if(plan.state=="PREPARED") {Text("El compromiso se registrará al iniciar.");Button(enabled=!busy,onClick={confirmation=IterationConfirmation("Iniciar iteración","Se conservará como compromiso inicial el conjunto actual de ${live.size} tareas. No se cambiarán sus fases ni checks.") {repository.start(plan.id);confirmation=null}}) {Text("Iniciar iteración")}}
                                if(plan.state=="ACTIVE") Button(enabled=!busy,onClick={val expected=live.toList();confirmation=IterationConfirmation("Cerrar iteración",summary()) {repository.close(plan.id,expected);confirmation=null}}) {Text("Cerrar iteración")}
                                if(plan.state=="CLOSED") Button(enabled=!busy,onClick={confirmation=IterationConfirmation("Reabrir iteración","Se iniciará un nuevo ciclo con las tareas actualmente vinculadas. Los compromisos y cierres anteriores se conservarán sin cambios.") {repository.start(plan.id,true);historicalCycle=null;confirmation=null}}) {Text("Reabrir iteración")}
                                OutlinedButton(onClick={showHistory=true}) {Text("Historial de ciclos")}
                            }
                            OutlinedButton(onClick={showWork=true}) {Text("Consultar trabajo y tareas técnicas")}
                        }
                    }}
                    if(showWork || showHistory) {
                    item {FlowRow(horizontalArrangement=Arrangement.spacedBy(4.dp)) {WorkOwnership.entries.forEach {choice->FilterChip(selected=ownership==choice,onClick={ownership=choice},label={Text(choice.label)})}}}
                    if(ownership==WorkOwnership.MINE && self==null) item {Text("Identifica una Persona como Yo desde su ficha General.")}
                    val allIds=when(filter) {"Compromiso inicial"->initial;"Alcance"->current;"Añadidas después"->added;"Retiradas"->removed;else->refs.mapTo(hashSetOf()) {it.taskId}+current}
                    val ids=allIds.filterTo(hashSetOf()) {matchesOwnership(it,ownership,self?.personId,responsibility)}
                    if(ids.isEmpty()) item {Text("No hay tareas en esta selección.")}
                    items(ids.sorted(),key={it}) {id->val node=nodes[id];if(node!=null) Card(onClick={onDismiss();onOpenNode(id)},modifier=Modifier.fillMaxWidth()) {Column(Modifier.padding(10.dp)) {
                        Text(storyDisplayTitle(node.id,node.title,storyNumbers));if(self?.personId!=null && self!!.personId in responsibility[id].orEmpty()) Text("Mi tarea",style=MaterialTheme.typography.labelSmall);Text("Origen: ${location(node)}");val related=relations(id);if(related.isNotBlank()) Text(related)
                        Text(when {id in removed->"Retirada del alcance";id in initial->"Compromiso inicial";id in added->"Añadida después del inicio";else->"Seleccionada; aún no comprometida"})
                        val phase=if(frozen) snapshots[id]?.phaseAtClose else node.workState
                        Text("${if(frozen) "Fase al cierre" else "Fase actual"}: ${phase?.let {WorkState.valueOf(it).label} ?: "Fuera del alcance Sprint"}")
                        if(id in current) Text("Check ${if(frozen) "al cierre" else "actual"}: ${if(if(frozen) snapshots[id]?.completedAtClose==true else node.isCompleted) "completada" else "pendiente"}")
                        val responsibilities=state.people.records.nodes.filter {it.contextId==context && it.nodeId==id}
                        if(responsibilities.isNotEmpty()) {Text("Responsabilidades asignadas (no autoría)");FlowRow {responsibilities.forEach {r->state.people.people.firstOrNull {it.id==r.personId}?.let {person->Row {PersonAvatar(Person(person.id,person.name,person.avatarFile,person.avatarZoom,person.avatarX,person.avatarY));Text("${person.name}: ${ResponsibilityRole.valueOf(r.role).label}")}}}}}

                        var menu by remember {mutableStateOf(false)}
                        Box {
                        IconButton(onClick={menu=true}) {Icon(androidx.compose.material.icons.Icons.Default.MoreVert,"Opciones trabajo: ${node.title}")}
                        DropdownMenu(menu,{menu=false}) {
                        DropdownMenuItem(text={Text("Consultar origen")},onClick={menu=false;onDismiss();onOpenNode(id)})
                        if(!frozen) DropdownMenuItem(text={Text("Responsables tradicionales")},onClick={menu=false;responsible=id})
                        if(!frozen && plan.state!="CLOSED" && id in current) {
                            if(node.parentId==plan.sprintId) Text("Tarea directa del Sprint; retirarla requiere moverla.")
                            else if(storyRefs.filter {it.sprintId==plan.sprintId}.any {id in storyWorkIds(state.work.nodes,it.storyId,state.work.records.storyTasks)}) DropdownMenuItem(text={Text("Gestionar historia asociada")},onClick={menu=false;pickingStory=true;query=""})
                            else DropdownMenuItem(text={Text("Retirar vínculo técnico")},enabled=!busy && node.workState!=null,onClick={menu=false;val phase=WorkState.valueOf(requireNotNull(node.workState));confirmation=IterationConfirmation("Retirar tarea","Se desvinculará «${node.title}» y se descartará su fase «${phase.label}», con tu confirmación. La tarea y su referencia histórica se conservarán.") {repository.remove(plan.id,id,phase);confirmation=null}})
                        }
                        }
                        }
                    }}}
                    val documents=state.work.nodes.filter {it.parentId==plan.sprintId && it.purpose!="ACTION"}
                    if(documents.isNotEmpty()) item {Text("Capas y notas del Sprint",style=MaterialTheme.typography.titleMedium)}
                    items(documents,key={"document:${it.id}"}) {n->ManagementNavigationCard(ManagementToolVisual(storyDisplayTitle(n.id,n.title,storyNumbers),n.description.take(120),androidx.compose.material.icons.Icons.Default.Article)) {onDismiss();onOpenNode(n.id)}}
                    }
                }
            }
            OutlinedButton(enabled=!busy,onClick={back()}) {Text("Volver")}
        }}
    }
    confirmation?.let {c->AlertDialog(onDismissRequest={if(!busy) confirmation=null},title={Text(c.title)},text={Column(Modifier.verticalScroll(rememberScrollState())) {Text(c.text);error?.let {Text(it,color=MaterialTheme.colorScheme.error)}}},confirmButton={OutlinedButton(enabled=!busy,onClick={perform(c.action)}) {Text("Confirmar")}},dismissButton={OutlinedButton(enabled=!busy,onClick={confirmation=null}) {Text("Cancelar")}})}
    editing?.let {p->IterationPlanEditor(p,busy,error,{editing=null}) {goal,start,end,notes->perform {repository.configure(context,p.sprintId,goal,parsePlanDate(start),parsePlanDate(end),notes);editing=null}}}
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun IterationPlanEditor(plan:IterationPlanEntity,busy:Boolean,error:String?,dismiss:()->Unit,save:(String,String,String,String)->Unit) {
    var goal by rememberSaveable {mutableStateOf(plan.goal)}
    var start by rememberSaveable {mutableStateOf(planDate(plan.startAt))}
    var end by rememberSaveable {mutableStateOf(planDate(plan.endAt))}
    var notes by rememberSaveable {mutableStateOf(plan.notes)}
    var dateTarget by remember {mutableStateOf<String?>(null)}
    Dialog(onDismissRequest={if(!busy) dismiss()},properties=DialogProperties(usePlatformDefaultWidth=false)) {
        Surface(Modifier.fillMaxSize().safeDrawingPadding()) {Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            Text("Editar planificación",style=MaterialTheme.typography.titleLarge)
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                Text("Fechas",style=MaterialTheme.typography.titleMedium)
                Text("Opcionales. No cambian los vencimientos de las tareas.",style=MaterialTheme.typography.bodySmall)
                OutlinedButton(enabled=!busy,onClick={dateTarget="start"}) {Text("Inicio previsto: ${start.ifBlank {"Sin definir"}}")}
                if(start.isNotBlank()) OutlinedButton(enabled=!busy,onClick={start=""}) {Text("Quitar inicio previsto")}
                OutlinedButton(enabled=!busy,onClick={dateTarget="end"}) {Text("Término previsto: ${end.ifBlank {"Sin definir"}}")}
                if(end.isNotBlank()) OutlinedButton(enabled=!busy,onClick={end=""}) {Text("Quitar término previsto")}
                HorizontalDivider()
                Text("Objetivo de la iteración",style=MaterialTheme.typography.titleMedium)
                OutlinedTextField(goal,{goal=it},label={Text("Objetivo del Sprint")},enabled=!busy,modifier=Modifier.fillMaxWidth())
                HorizontalDivider()
                Text("Notas de planificación",style=MaterialTheme.typography.titleMedium)
                OutlinedTextField(notes,{notes=it},label={Text("Observaciones")},enabled=!busy,modifier=Modifier.fillMaxWidth())
                error?.let {Text(it,color=MaterialTheme.colorScheme.error)}
            }
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                OutlinedButton(enabled=!busy,onClick=dismiss,modifier=Modifier.weight(1f)) {Text("Cancelar")}
                Button(enabled=!busy,onClick={save(goal,start,end,notes)},modifier=Modifier.weight(1f)) {Text("Guardar planificación")}
            }
        }}
    }
    dateTarget?.let {target->
        val raw=if(target=="start") start else end
        val initial=raw.takeIf {it.isNotBlank()}?.let {LocalDate.parse(it).atStartOfDay(java.time.ZoneOffset.UTC).toInstant().toEpochMilli()}
        val picker=rememberDatePickerState(initialSelectedDateMillis=initial)
        DatePickerDialog(onDismissRequest={dateTarget=null},confirmButton={TextButton(onClick={picker.selectedDateMillis?.let {millis->val date=java.time.Instant.ofEpochMilli(millis).atZone(java.time.ZoneOffset.UTC).toLocalDate().toString();if(target=="start") start=date else end=date};dateTarget=null}) {Text("Seleccionar fecha")}},dismissButton={TextButton(onClick={dateTarget=null}) {Text("Cancelar")}}) {DatePicker(picker)}
    }
}

@Composable private fun PlanningSection(title:String,content:@Composable ColumnScope.()->Unit) {
    Card(Modifier.fillMaxWidth()) {Column(Modifier.padding(12.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {Text(title,style=MaterialTheme.typography.titleMedium);content()}}
}
