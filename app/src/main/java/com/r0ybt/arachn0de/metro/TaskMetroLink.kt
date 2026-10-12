package com.r0ybt.arachn0de.metro

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.testTag
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** The selector changes only the relation, never a plan or a running session. */
@Composable internal fun TaskMetroAccess(nodeId:String,repository:MetroRepository) {
    val snapshot by remember(repository) {repository.observe()}.collectAsState(initial=null)
    val attached=snapshot?.journeys?.firstOrNull {it.row.nodeId==nodeId}
    var open by remember(nodeId) {mutableStateOf(false)}
    var unlink by remember(nodeId) {mutableStateOf(false)}
    val query=rememberTextFieldState()
    var error by remember(nodeId) {mutableStateOf<String?>(null)}
    var busy by remember(nodeId) {mutableStateOf(false)}
    val scope=rememberCoroutineScope()
    fun link(journey:MetroJourney,task:String?) {
        if(busy) return
        busy=true;error=null
        scope.launch {
            try {repository.link(journey.row.id,journey.row.revision,task);open=false;unlink=false}
            catch(c:CancellationException) {throw c}
            catch(e:Exception) {error=e.message ?: "No se pudo guardar el vínculo."}
            finally {busy=false}
        }
    }
    TextButton(enabled=snapshot!=null,onClick={open=true;error=null},modifier=Modifier.testTag("task-metro-link:$nodeId")) {Text(if(attached==null) "Vincular viaje" else "Vínculo Metro")}
    if(open) AlertDialog(onDismissRequest={if(!busy) open=false},title={Text("Viaje de la tarea")},text={Column {
        Text("Vincular o desvincular conserva el viaje, su ruta y su seguimiento.",style=MaterialTheme.typography.bodySmall)
        error?.let {Text(it,color=MaterialTheme.colorScheme.error)}
        if(attached!=null) {
            TextButton(onClick={open=false;MetroNavigation.openJourney(attached.row.id)}) {Text("Abrir viaje")}
            TextButton(enabled=!busy,onClick={unlink=true}) {Text("Desvincular")}
        } else {
            OutlinedTextField(state=query,lineLimits=TextFieldLineLimits.SingleLine,label={Text("Buscar viaje")},modifier=Modifier.fillMaxWidth())
            val available=snapshot?.journeys.orEmpty().filter {it.row.nodeId==null}.map {journey->
                val net=MetroCatalogRevision.forRoute(snapshot!!.preferences.network,journey.data.plan.networkVersion)
                journey to journey.data.plan.stops.joinToString(" → ") {net.stations[it]?.name ?: it}
            }.filter {it.second.contains(query.text.toString().trim(),true)}
            if(available.isEmpty()) Text("Sin viajes disponibles. Guarda uno desde Metro.")
            LazyColumn(Modifier.heightIn(max=320.dp)) {items(available,key={it.first.row.id}) {(journey,label)->
                TextButton(enabled=!busy,onClick={link(journey,nodeId)},modifier=Modifier.testTag("link-trip:${journey.row.id}")) {Text(label)}
            }}
        }
    }},confirmButton={TextButton(enabled=!busy,onClick={open=false}) {Text("Cerrar")}})
    if(unlink && attached!=null) AlertDialog(onDismissRequest={if(!busy) unlink=false},title={Text("Desvincular viaje")},text={Text(error ?: "La tarea y el viaje se conservarán, incluido el seguimiento activo y su historial.")},confirmButton={TextButton(enabled=!busy,onClick={link(attached,null)}) {Text("Desvincular")}},dismissButton={TextButton(enabled=!busy,onClick={unlink=false}) {Text("Cancelar")}})
}
