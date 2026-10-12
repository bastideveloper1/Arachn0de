package com.r0ybt.arachn0de.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.r0ybt.arachn0de.domain.model.*
import com.r0ybt.arachn0de.data.local.SprintLinkEntity
import com.r0ybt.arachn0de.data.repository.SprintLinkRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

internal fun sprintOrigin(node:Node,tree:NodeTreeSnapshot):String = node.parentId?.let {tree.nodesById[it]?.title} ?: "Raíz del proyecto"

@Composable internal fun SprintLinksDialog(sprint:Node,tree:NodeTreeSnapshot,links:List<SprintLinkEntity>,repository:SprintLinkRepository,onDismiss:()->Unit) {
    var query by remember {mutableStateOf("")}
    var selected by remember {mutableStateOf(setOf<String>())}
    var error by remember {mutableStateOf<String?>(null)}
    var busy by remember {mutableStateOf(false)}
    val scope=rememberCoroutineScope()
    val candidates=tree.nodes.filter {it.projectId==sprint.projectId && it.isCompletable &&
        tree.nodesById[it.parentId]?.sprintMode!=true && links.none {link->link.taskId==it.id} &&
        (it.title.contains(query,true) || sprintOrigin(it,tree).contains(query,true))}
    Dialog(onDismissRequest={if(!busy) onDismiss()},properties=DialogProperties(usePlatformDefaultWidth=false)) {
        Surface(Modifier.fillMaxWidth().fillMaxHeight(.85f).safeDrawingPadding().padding(12.dp),shape=MaterialTheme.shapes.large) {
            Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                Text("Vincular tareas",style=MaterialTheme.typography.titleLarge)
                OutlinedTextField(query,{query=it},label={Text("Buscar tarea o capa de origen")},singleLine=true,modifier=Modifier.fillMaxWidth())
                LazyColumn(Modifier.fillMaxWidth().weight(1f)) {
                    if(candidates.isEmpty()) item {Text("No hay tareas disponibles con esta búsqueda.")}
                    items(candidates,key={it.id}) {task->Row {
                        Checkbox(task.id in selected,{checked->selected=if(checked) selected+task.id else selected-task.id},enabled=!busy)
                        Column {Text(task.title);Text("Origen: ${sprintOrigin(task,tree)}",style=MaterialTheme.typography.labelSmall)}
                    }}
                }
                error?.let {Text(it,color=MaterialTheme.colorScheme.error)}
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.End) {
                    TextButton(enabled=!busy,onClick=onDismiss) {Text("Cancelar")}
                    TextButton(enabled=!busy && selected.isNotEmpty(),onClick={busy=true;error=null;scope.launch {
                        try {repository.link(sprint.id,selected);onDismiss()}
                        catch(c:CancellationException) {throw c}
                        catch(e:Exception) {error=e.message ?: "No se pudieron vincular las tareas."}
                        finally {busy=false}
                    }}) {Text("Vincular")}
                }
            }
        }
    }
}

@Composable internal fun UnlinkSprintTaskDialog(sprintId:String,task:Node,phase:WorkState,repository:SprintLinkRepository,onDismiss:()->Unit) {
    var busy by remember {mutableStateOf(false)};var error by remember {mutableStateOf<String?>(null)}
    val scope=rememberCoroutineScope()
    AlertDialog(onDismissRequest={if(!busy) onDismiss()},title={Text("Desvincular tarea")},text={Column {
        Text("${task.title}: se descartará la fase Sprint «${phase.label}». La tarea, su ubicación y su completitud se conservan. Si vuelves a vincularla, comenzará en No planificada o Terminada según su completitud.")
        error?.let {Text(it,color=MaterialTheme.colorScheme.error)}
    }},confirmButton={TextButton(enabled=!busy,onClick={busy=true;scope.launch {
        try {repository.unlink(sprintId,task.id,phase);onDismiss()}
        catch(c:CancellationException) {throw c}
        catch(e:Exception) {error=e.message ?: "No se pudo desvincular. Revisa la fase y reintenta."}
        finally {busy=false}
    }}) {Text("Desvincular y descartar fase")}},dismissButton={TextButton(enabled=!busy,onClick=onDismiss) {Text("Cancelar")}})
}
