package com.r0ybt.arachn0de.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Workspaces
import androidx.compose.material.icons.filled.Article
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.r0ybt.arachn0de.data.local.*
import com.r0ybt.arachn0de.data.repository.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private data class BacklogChoice(val id:String,val title:String,val location:String)
private data class BacklogSelection(val title:String,val choices:List<BacklogChoice>,val action:suspend (String)->Unit)
private data class BacklogConfirmation(val text:String,val action:suspend ()->Unit)

@Composable internal fun BacklogDialog(contextId:String,repository:BacklogRepository,onOpenNode:(String)->Unit,onDismiss:()->Unit) =
    BacklogDialog(contextId,repository,onOpenNode,false,onDismiss)

@Composable internal fun BacklogDialog(contextId:String,repository:BacklogRepository,onOpenNode:(String)->Unit,initialRolesView:Boolean,onDismiss:()->Unit) = BacklogDialog(contextId,repository,onOpenNode,initialRolesView,null,onDismiss)

@OptIn(ExperimentalLayoutApi::class)
@Composable internal fun BacklogDialog(contextId:String,repository:BacklogRepository,onOpenNode:(String)->Unit,initialRolesView:Boolean,initialNodeId:String?,onDismiss:()->Unit) {
    val loadedState by remember(repository,contextId) {repository.observe(contextId)}.collectAsState<BacklogState, BacklogState?>(initial=null)
    val state=loadedState ?: BacklogState()
    val storyNumbers=remember(state.nodes,state.records.roles) {storyLabels(state.nodes,state.records.roles)}
    val records=state.records;val nodes=remember(state.nodes) {state.nodes.associateBy {it.id}}
    val roles=remember(records.roles) {records.roles.associateBy {it.nodeId}}
    val entries=records.entries.filter {it.contextId==contextId}
    val entryIds=remember(entries) {entries.mapTo(hashSetOf()) {it.nodeId}}
    val tasksByStory=remember(records.storyTasks,nodes) {records.storyTasks.groupBy {it.storyId}.mapValues {(_,links)->links.mapNotNull {nodes[it.taskId]}.distinctBy {it.id}}}
    val eligible=remember(state) {state.nodes.filter {state.owners[it.id]==contextId}}
    var rolesView by rememberSaveable {mutableStateOf(initialRolesView)};var pendingOnly by rememberSaveable {mutableStateOf(false)}
    var selected by rememberSaveable {mutableStateOf<String?>(initialNodeId)};var selection by remember {mutableStateOf<BacklogSelection?>(null)}
    var roleNode by remember {mutableStateOf<String?>(null)};var confirmation by remember {mutableStateOf<BacklogConfirmation?>(null)}
    var busy by remember {mutableStateOf(false)};var error by remember {mutableStateOf<String?>(null)}
    val scope=rememberCoroutineScope()
    fun perform(action:suspend ()->Unit) {if(busy) return;busy=true;error=null;scope.launch {
        try {action()}
        catch(c:CancellationException) {throw c}
        catch(e:Exception) {error=e.message ?: "No se pudo guardar el Backlog."}
        finally {busy=false}
    }}
    fun openOriginal(id:String) {
        if(nodes[id]==null) {error="El elemento original ya no está disponible.";return}
        onDismiss()
        onOpenNode(id)
    }
    fun location(node:NodeEntity)=node.parentId?.let {nodes[it]?.let {n->storyDisplayTitle(n.id,n.title,storyNumbers)}} ?: "Raíz del proyecto"
    fun choose(title:String,rows:List<NodeEntity>,action:suspend (String)->Unit) {selection=BacklogSelection(title,rows.map {BacklogChoice(it.id,storyDisplayTitle(it.id,it.title,storyNumbers),location(it))},action)}
    fun confirm(text:String,action:suspend ()->Unit) {confirmation=BacklogConfirmation(text,action)}
    fun taskRows(story:String)=tasksByStory[story].orEmpty()
    fun pending(node:NodeEntity)=if(node.purpose=="ACTION") !node.isCompleted else taskRows(node.id).let {it.isEmpty() || it.any {task->!task.isCompleted}}
    fun relatedObjectives(id:String):List<ManagementObjectiveEntity> {
        val stories=if(roles[id]?.role=="STORY") setOf(id) else records.storyTasks.filter {it.taskId==id}.mapTo(hashSetOf()) {it.storyId}
        val epics=if(roles[id]?.role=="EPIC") setOf(id) else records.epicStories.filter {it.storyId in stories}.mapTo(hashSetOf()) {it.epicId}
        val objectiveIds=records.objectiveEpics.filter {it.epicId in epics}.mapTo(hashSetOf()) {it.objectiveId}
        return state.objectives.filter {it.id in objectiveIds}
    }
    BacklogSurface({if(!busy) onDismiss()}) {
        Text(if(rolesView) "Historias y épicas" else "Backlog",style=MaterialTheme.typography.titleLarge)
        error?.let {Text(it,color=MaterialTheme.colorScheme.error)}
        if(loadedState!=null) LazyColumn(Modifier.weight(1f).fillMaxWidth().testTag("backlog-list"),state=rememberLazyListState(),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            val detail=nodes[selected]
            if(detail!=null) {
                item {
                    TextButton(onClick={selected=null}) {Text("Volver al Backlog")}
                    ResponsibilitiesAccess(contextId,detail.id,false,repository.participation)
                    Text(storyDisplayTitle(detail.id,detail.title,storyNumbers),style=MaterialTheme.typography.titleLarge)
                    if(roles[detail.id]?.role=="STORY") {
                        Text("Épica principal",style=MaterialTheme.typography.titleMedium)
                        val parent=nodes[detail.parentId]?.takeIf {roles[it.id]?.role=="EPIC"}
                        if(parent!=null) ManagementNavigationCard(ManagementToolVisual(parent.title,"Contiene esta Historia",Icons.Default.Workspaces)) {openOriginal(parent.id)}
                        else Text("Esta Historia no tiene una épica contenedora. Sus vínculos no cambian su ubicación.")
                        Text("Vínculos adicionales",style=MaterialTheme.typography.titleMedium)
                        Text("Relacionan esta Historia con otros elementos sin trasladarla ni cambiar su épica principal.",style=MaterialTheme.typography.bodySmall)
                    } else Text("Origen: ${location(detail)}")
                    Text(roles[detail.id]?.role?.let {LayerRole.valueOf(it).label} ?: "Tarea")
                    OutlinedButton(onClick={openOriginal(detail.id)}) {Text("Abrir original")}
                    Text("Objetivos relacionados",style=MaterialTheme.typography.titleMedium)
                    val goals=relatedObjectives(detail.id)
                    if(goals.isEmpty()) Text("Sin objetivos relacionados.") else goals.forEach {Text(it.title)}
                    if(roles[detail.id]?.role=="EPIC") {
                        TextButton(enabled=!busy,onClick={selection=BacklogSelection("Vincular objetivo",state.objectives.filter {goal->records.objectiveEpics.none {it.objectiveId==goal.id && it.epicId==detail.id}}.map {BacklogChoice(it.id,storyDisplayTitle(it.id,it.title,storyNumbers),"Objetivo")}) {repository.linkObjective(contextId,it,detail.id)}}) {Text("Vincular objetivo")}
                        records.objectiveEpics.filter {it.epicId==detail.id}.forEach {link->
                            Card(Modifier.fillMaxWidth()) {
                                Row(Modifier.padding(12.dp),verticalAlignment=androidx.compose.ui.Alignment.CenterVertically) {
                                    Text("Objetivo · ${state.objectives.firstOrNull {it.id==link.objectiveId}?.title.orEmpty()}",Modifier.weight(1f))
                                    var menu by remember {mutableStateOf(false)}
                                    Box {
                                        IconButton(onClick={menu=true}) {Icon(Icons.Default.MoreVert,"Opciones del objetivo vinculado")}
                                        DropdownMenu(menu,{menu=false}) {
                                            DropdownMenuItem(text={Text("Desvincular")},enabled=!busy,modifier=Modifier.semantics {contentDescription="Desvincular objetivo: ${link.objectiveId}"},onClick={menu=false;confirm("¿Retirar este vínculo con el objetivo? No se modificarán sus criterios.") {repository.unlinkObjective(contextId,link.objectiveId,link.epicId)}})
                                        }
                                    }
                                }
                            }
                        }
                        TextButton(enabled=!busy,onClick={choose("Vincular historia",eligible.filter {row->roles[row.id]?.role=="STORY" && records.epicStories.none {it.epicId==detail.id && it.storyId==row.id}}) {repository.linkEpic(contextId,detail.id,it)}}) {Text("Vincular historia")}
                    } else if(roles[detail.id]?.role=="STORY") {
                        TextButton(enabled=!busy,onClick={choose("Vincular otra épica",eligible.filter {row->row.id!=detail.parentId && roles[row.id]?.role=="EPIC" && records.epicStories.none {it.epicId==row.id && it.storyId==detail.id}}) {repository.linkEpic(contextId,it,detail.id)}}) {Text("Vincular otra épica")}
                        TextButton(enabled=!busy,onClick={choose("Vincular tarea",eligible.filter {row->row.purpose=="ACTION" && records.storyTasks.none {it.storyId==detail.id && it.taskId==row.id}}) {repository.linkTask(contextId,detail.id,it)}}) {Text("Vincular tarea")}
                    } else TextButton(enabled=!busy,onClick={choose("Vincular historia",eligible.filter {row->roles[row.id]?.role=="STORY" && records.storyTasks.none {it.storyId==row.id && it.taskId==detail.id}}) {repository.linkTask(contextId,it,detail.id)}}) {Text("Vincular historia")}
                }
                val epicLinks=records.epicStories.filter {it.epicId==detail.id || (it.storyId==detail.id && it.epicId!=detail.parentId)}
                items(epicLinks,key={"epic:${it.epicId}:${it.storyId}"}) {link->
                    val other=if(link.epicId==detail.id) link.storyId else link.epicId
                    RelationshipCard(nodes[other],"${roles[other]?.role?.let {LayerRole.valueOf(it).label}}",!busy,{selected=other}) {
                        confirm("¿Retirar la relación entre épica e historia? Los nodos permanecerán en su ubicación.") {repository.unlinkEpic(contextId,link.epicId,link.storyId)}
                    }
                }
                val taskLinks=records.storyTasks.filter {it.storyId==detail.id || it.taskId==detail.id}
                items(taskLinks,key={"task:${it.storyId}:${it.taskId}"}) {link->
                    val other=if(link.storyId==detail.id) link.taskId else link.storyId
                    RelationshipCard(nodes[other],if(other==link.storyId) "Historia" else "Tarea",!busy,{selected=other}) {
                        confirm("¿Desvincular historia y tarea? El vínculo Sprint, la fase y la ubicación se conservarán.") {repository.unlinkTask(contextId,link.storyId,link.taskId)}
                    }
                }
            } else if(selected!=null) {
                item {Text("El elemento original ya no está disponible.");OutlinedButton(onClick={selected=null}) {Text("Volver al Backlog")}}
            } else if(rolesView) {
                item {
                    TextButton(onClick={rolesView=false}) {Text("Ver Backlog")}
                    TextButton(enabled=!busy,onClick={choose("Seleccionar Capa",eligible.filter {it.purpose=="LAYER"}) {roleNode=it}}) {Text("Asignar rol a Capa")}
                    Text("Épicas e historias",style=MaterialTheme.typography.titleMedium)
                }
                items(records.roles.filter {it.contextId==contextId},key={it.nodeId}) {row->nodes[row.nodeId]?.let {node->Card(onClick={selected=node.id},modifier=Modifier.fillMaxWidth()) {Column(Modifier.padding(12.dp)) {
                    Text(storyDisplayTitle(node.id,node.title,storyNumbers),style=MaterialTheme.typography.titleMedium,maxLines=2,overflow=TextOverflow.Ellipsis);Text("${LayerRole.valueOf(row.role).label} · Origen: ${location(node)}")
                    var menu by remember {mutableStateOf(false)}
                    Box {
                        IconButton(onClick={menu=true}) {Icon(Icons.Default.MoreVert,"Opciones de rol: ${node.title}")}
                        DropdownMenu(expanded=menu,onDismissRequest={menu=false}) {
                            DropdownMenuItem(text={Text("Cambiar rol")},enabled=!busy,modifier=Modifier.semantics {contentDescription="Cambiar rol: ${node.title}"},onClick={menu=false;roleNode=node.id})
                            DropdownMenuItem(text={Text("Abrir original")},onClick={menu=false;openOriginal(node.id)})
                        }
                    }
                }}}}
                item {Text("Objetivos y épicas",style=MaterialTheme.typography.titleMedium)}
                items(state.objectives,key={"objective:${it.id}"}) {goal->Card(Modifier.fillMaxWidth()) {Column(Modifier.padding(12.dp)) {
                    Text(goal.title,style=MaterialTheme.typography.titleMedium)
                    val epics=records.objectiveEpics.filter {it.objectiveId==goal.id}
                    if(epics.isEmpty()) Text("Sin épicas vinculadas.")
                    epics.forEach {link->TextButton(enabled=!busy,onClick={selected=link.epicId}) {Text(nodes[link.epicId]?.title.orEmpty())}}
                    TextButton(enabled=!busy,modifier=Modifier.semantics {contentDescription="Vincular épica a objetivo: ${goal.title}"},onClick={choose("Vincular épica",eligible.filter {roles[it.id]?.role=="EPIC"}) {repository.linkObjective(contextId,goal.id,it)}}) {Text("Vincular épica")}
                }}}
            } else {
                item {
                    FlowRow {
                        TextButton(onClick={rolesView=true}) {Text("Relaciones y roles")}
                        TextButton(enabled=!busy,onClick={choose("Agregar tarea",eligible.filter {it.purpose=="ACTION" && it.id !in entryIds}) {repository.addTask(contextId,it)}}) {Text("Agregar tarea")}
                        TextButton(enabled=!busy,onClick={choose("Agregar historia",eligible.filter {roles[it.id]?.role=="STORY" && it.id !in entryIds}) {repository.addStory(contextId,it)}}) {Text("Agregar historia")}
                        TextButton(onClick={pendingOnly=!pendingOnly}) {Text(if(pendingOnly) "✓ Solo pendientes" else "Solo pendientes")}
                    }
                    if(pendingOnly) Text("Para reordenar, muestra todo el Backlog.")
                    if(entries.isEmpty()) Text("El Backlog está vacío.")
                    else if(pendingOnly && entries.none {nodes[it.nodeId]?.let(::pending)==true}) Text("No hay trabajo pendiente en el Backlog.")
                }
                items(entries.filter {row->!pendingOnly || nodes[row.nodeId]?.let(::pending)==true},key={it.nodeId}) {entry->nodes[entry.nodeId]?.let {node->Card(onClick={openOriginal(node.id)},modifier=Modifier.fillMaxWidth().semantics {contentDescription="Abrir original: ${node.title}"}) {Column(Modifier.padding(12.dp)) {
                    Text(storyDisplayTitle(node.id,node.title,storyNumbers),style=MaterialTheme.typography.titleMedium,maxLines=2,overflow=TextOverflow.Ellipsis)
                    Text(if(node.purpose=="ACTION") "Tarea" else "Historia",style=MaterialTheme.typography.labelMedium)
                    Text(if(node.purpose=="ACTION") {if(node.isCompleted) "Trabajo completado" else "Trabajo pendiente"} else taskRows(node.id).let {if(it.isEmpty()) "Sin tareas vinculadas" else "${it.count {task->!task.isCompleted}} tareas pendientes"})
                    FlowRow {
                        var menu by remember {mutableStateOf(false)}
                        Box {
                        IconButton(onClick={menu=true}) {Icon(Icons.Default.MoreVert,contentDescription="Opciones Backlog: ${node.title}")}
                        DropdownMenu(expanded=menu,onDismissRequest={menu=false}) {
                        DropdownMenuItem(text={Text("Relaciones")},enabled=!busy,modifier=Modifier.semantics {contentDescription="Relaciones: ${node.title}"},onClick={menu=false;selected=node.id})
                        val index=entries.indexOf(entry)
                        for((label,offset) in listOf("Subir" to -1,"Bajar" to 1)) DropdownMenuItem(text={Text(label)},enabled=!busy && !pendingOnly && index+offset in entries.indices,modifier=Modifier.semantics {contentDescription="$label Backlog: ${node.title}"},onClick={perform {
                            val ids=entries.map {it.nodeId}.toMutableList();ids.removeAt(index);ids.add(index+offset,node.id);repository.reorder(contextId,ids)
                        };menu=false})
                        DropdownMenuItem(text={Text("Retirar")},enabled=!busy,modifier=Modifier.semantics {contentDescription="Retirar del Backlog: ${node.title}"},onClick={menu=false;confirm("¿Retirar este elemento del Backlog? Se conservarán el nodo, su rol y todos sus vínculos.") {repository.removeEntry(contextId,node.id)}})
                        }
                        }
                    }
                }}}}
            }
        }
        TextButton(enabled=!busy,onClick=onDismiss) {Text("Cerrar Backlog")}
    }
    selection?.let {request->BacklogSelector(request,busy,error,{selection=null}) {id->perform {request.action(id);selection=null}}}
    roleNode?.let {id->nodes[id]?.let {node->LayerRoleDialog(node.title,roles[id]?.role?.let(LayerRole::valueOf),busy,error,{roleNode=null}) {role->perform {repository.setRole(contextId,id,role,confirmRelationLoss=true);roleNode=null}}}}
    confirmation?.let {request->AlertDialog(onDismissRequest={if(!busy) confirmation=null},title={Text("Confirmar cambio")},text={Text(request.text+(error?.let {"\n$it"} ?: ""))},confirmButton={TextButton(enabled=!busy,onClick={perform {request.action();confirmation=null}}) {Text("Confirmar")}},dismissButton={TextButton(enabled=!busy,onClick={confirmation=null}) {Text("Cancelar")}})}
}

@OptIn(ExperimentalLayoutApi::class)
@Composable private fun RelationshipCard(node:NodeEntity?,kind:String,enabled:Boolean,onView:()->Unit,onUnlink:()->Unit) {
    if(node==null) return
    Card(onClick=onView,enabled=enabled,modifier=Modifier.fillMaxWidth()) {
        Row(Modifier.padding(12.dp),verticalAlignment=androidx.compose.ui.Alignment.CenterVertically) {
            Text("$kind · ${storyDisplayTitle(node.id,node.title,LocalStoryLabels.current)}",Modifier.weight(1f),style=MaterialTheme.typography.titleMedium)
            var menu by remember {mutableStateOf(false)}
            Box {
                IconButton(onClick={menu=true}) {Icon(Icons.Default.MoreVert,"Opciones vínculo: ${node.title}")}
                DropdownMenu(expanded=menu,onDismissRequest={menu=false}) {
                    DropdownMenuItem(text={Text("Desvincular")},enabled=enabled,modifier=Modifier.semantics {contentDescription="Desvincular: ${node.title}"},onClick={menu=false;onUnlink()})
                }
            }
        }
    }
}
@Composable private fun BacklogSurface(onDismiss:()->Unit,content:@Composable ColumnScope.()->Unit) {
    Dialog(onDismissRequest=onDismiss,properties=DialogProperties(usePlatformDefaultWidth=false)) {Surface(Modifier.fillMaxSize().safeDrawingPadding()) {Column(Modifier.padding(12.dp),verticalArrangement=Arrangement.spacedBy(8.dp),content=content)}}
}
@Composable private fun BacklogSelector(request:BacklogSelection,busy:Boolean,error:String?,onDismiss:()->Unit,onSelect:(String)->Unit) {
    var query by remember {mutableStateOf("")};var selected by remember {mutableStateOf<String?>(null)}
    val choices by produceState(request.choices,request.choices,query) {value=withContext(Dispatchers.Default) {request.choices.filter {it.title.contains(query,ignoreCase=true)}}}
    BacklogSurface({if(!busy) onDismiss()}) {
        Text(request.title,style=MaterialTheme.typography.titleLarge)
        error?.let {Text(it,color=MaterialTheme.colorScheme.error)}
        OutlinedTextField(query,{query=it},enabled=!busy,label={Text("Buscar por título")},modifier=Modifier.fillMaxWidth(),singleLine=true)
        LazyColumn(Modifier.weight(1f).fillMaxWidth().testTag("backlog-selector"),verticalArrangement=Arrangement.spacedBy(4.dp)) {
            if(choices.isEmpty()) item {Text("No hay elementos compatibles con esta búsqueda.")}
            items(choices,key={it.id}) {row->TextButton(enabled=!busy,onClick={selected=row.id},modifier=Modifier.fillMaxWidth().semantics {contentDescription="Seleccionar existente: ${row.title}"}) {Column(Modifier.fillMaxWidth()) {
                Text("${if(selected==row.id) "✓ " else ""}${row.title}");Text("Origen: ${row.location}",style=MaterialTheme.typography.bodySmall)
            }}}
        }
        Row {TextButton(enabled=!busy,onClick=onDismiss) {Text("Cancelar")};TextButton(enabled=!busy && selected in choices.map {it.id},onClick={selected?.let(onSelect)}) {Text("Seleccionar")}}
    }
}
@Composable private fun LayerRoleDialog(title:String,initial:LayerRole?,busy:Boolean,error:String?,onDismiss:()->Unit,onSave:(LayerRole?)->Unit) {
    var role by remember {mutableStateOf(initial)}
    BacklogSurface({if(!busy) onDismiss()}) {
        Text("Rol de Capa",style=MaterialTheme.typography.titleLarge)
        error?.let {Text(it,color=MaterialTheme.colorScheme.error)}
        LazyColumn(Modifier.weight(1f).fillMaxWidth()) {item {
            Text(title);Text("Cambiar o retirar el rol elimina sus relaciones de gestión y su entrada del Backlog. Conserva el nodo, sus descendientes y los vínculos Sprint. Las tareas permanecen en el Backlog.")
            for(choice in listOf(null,LayerRole.EPIC,LayerRole.STORY)) TextButton(enabled=!busy,onClick={role=choice}) {Text("${if(role==choice) "✓ " else ""}${choice?.label ?: "Capa normal"}")}
        }}
        Row {TextButton(enabled=!busy,onClick=onDismiss) {Text("Cancelar")};TextButton(enabled=!busy,onClick={onSave(role)}) {Text("Confirmar rol")}}
    }
}
