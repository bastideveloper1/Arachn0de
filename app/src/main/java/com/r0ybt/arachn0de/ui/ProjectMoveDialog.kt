package com.r0ybt.arachn0de.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.r0ybt.arachn0de.data.repository.ProjectRepository
import com.r0ybt.arachn0de.domain.model.*
import com.r0ybt.arachn0de.ui.state.OperationState
import com.r0ybt.arachn0de.ui.state.LoadState
import kotlinx.coroutines.flow.flow

@Composable
internal fun ProjectMoveDialog(repository:ProjectRepository,source:Project,onDismiss:()->Unit,onMoved:()->Unit, onConverted: (String, String) -> Unit = { _, _ -> onMoved() }, operationScope: kotlinx.coroutines.CoroutineScope = rememberCoroutineScope()) {
    val projectsFlow=remember(repository) { repository.observeProjects() }
    val projects by projectsFlow.collectAsState(initial=emptyList())
    var target by rememberSaveable(source.id) { mutableStateOf<String?>(null) }
    var parent by rememberSaveable(source.id) { mutableStateOf<String?>(null) }
    var confirm by rememberSaveable(source.id) { mutableStateOf(false) }
    var layers by remember(target) { mutableStateOf(emptyList<Node>()) }
    val load=remember(target) { LoadState() }
    LaunchedEffect(repository,target,load.attempt) { target?.let { id -> load.collect(flow { emit(repository.destinationLayers(id)) }) { layers=it } } }
    val operation=remember(operationScope,repository) { OperationState(operationScope) }
    var failureReason by remember { mutableStateOf<String?>(null) }
    var convertedId by remember { mutableStateOf<String?>(null) }
    val destination=projects.firstOrNull { it.id==target && it.id!=source.id }
    AlertDialog(onDismissRequest={ if(!operation.busy) onDismiss() },title={ Text("Convertir en capa") },
        text={ Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
            if(target==null) {
                Text("Elige otro proyecto")
                if(projects.none { it.id!=source.id }) Text("Crea otro proyecto para elegir un destino.")
                LazyColumn(Modifier.heightIn(max=350.dp)) { items(projects.filter { it.id!=source.id },key={ it.id }) { project -> TextButton(onClick={ target=project.id;parent=null }) { Text(project.name) } } }
            } else {
                Text(destination?.name ?: "El destino ya no existe")
                TextButton(enabled=!operation.busy,onClick={ target=null;confirm=false }) { Text("Cambiar proyecto") }
                TextButton(enabled=destination!=null && !operation.busy,onClick={ parent=null;confirm=true }) { Text("En la raíz del proyecto") }
                val byId=remember(layers) { layers.associateBy { it.id } }
                LazyColumn(Modifier.heightIn(max=350.dp)) { items(layers,key={ it.id }) { layer ->
                    val path=mutableListOf<String>();var cursor:Node?=layer;val seen=hashSetOf<String>()
                    while(cursor!=null && path.size<6 && seen.add(cursor.id)) { path.add(cursor.title);cursor=byId[cursor.parentId] }
                    TextButton(enabled=!operation.busy,onClick={ parent=layer.id;confirm=true }) { Text((if(cursor!=null) "… › " else "")+path.asReversed().joinToString(" › ")) }
                } }
            }
        } },confirmButton={},dismissButton={ TextButton(enabled=!operation.busy,onClick=onDismiss) { Text("Cancelar") } })
    if(confirm) AlertDialog(onDismissRequest={ if(!operation.busy) confirm=false },title={ Text("Confirmar conversión") },
        text={ Text("${source.name} dejará de aparecer en Proyectos y se convertirá en una capa dentro de ${destination?.name ?: "el destino elegido"}${parent?.let { " › " + (layers.firstOrNull { row -> row.id == it }?.title ?: "capa eliminada") } ?: " › raíz"}.\n\nSe conservarán el contenido actual y las propiedades. Los valores heredados se conservarán como valores propios en la nueva raíz. Su fotografía permanecerá guardada y oculta hasta reconvertirlo en proyecto.") },
        confirmButton={ TextButton(enabled=!operation.busy && destination!=null,onClick={ operation.submit("No se pudo mover el proyecto. El contenido se conserva; revisa el destino y reintenta.",{
            failureReason = null
            try { convertedId = repository.moveInside(source.id,checkNotNull(target),parent); true }
            catch (failure: IllegalArgumentException) { failureReason = failure.message; throw failure }
        }, { onConverted(checkNotNull(target), checkNotNull(convertedId)) }) }) { Text("Convertir") } },dismissButton={ TextButton(enabled=!operation.busy,onClick={ confirm=false }) { Text("Cancelar") } })
    if (operation.error != null) AlertDialog(onDismissRequest = operation::clearError,
        title = { Text("No se pudo convertir") }, text = { Text(failureReason ?: checkNotNull(operation.error)) },
        confirmButton = { TextButton(onClick = operation::clearError) { Text("Entendido") } })
    LoadErrorDialog(load)
}
