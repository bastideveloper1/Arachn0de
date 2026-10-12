package com.r0ybt.arachn0de.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.r0ybt.arachn0de.data.local.ManagementContextEntity
import com.r0ybt.arachn0de.data.repository.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** Configuration is contextual. Work is entered through actual child layers, never a parallel menu. */
@Composable internal fun ManagementSection(projectId:String?,nodeId:String?,context:ManagementContextEntity?,repository:ManagementRepository,ownerTitle:String="",ownerDescription:String="",onOpenNode:(String)->Unit={},configurationOpen:Boolean=false,onConfigurationDismiss:()->Unit={}) {
    var gantt by rememberSaveable(projectId,nodeId) {mutableStateOf(false)}
    if(context?.enabled==true) ManagementNavigationCard(requireNotNull(managementToolVisual("GANTT"))) {gantt=true}
    if(gantt && context!=null) ProjectGanttDialog(context.id,repository,{onOpenNode(it)},ownerTitle) {gantt=false}
    if(configurationOpen) ManagementConfiguration(projectId,nodeId,context,repository,onConfigurationDismiss)
}
@Composable private fun ManagementConfiguration(project:String?,node:String?,context:ManagementContextEntity?,repository:ManagementRepository,onDismiss:()->Unit) {
    val method=context?.methodology?.let(ManagementMethodology::valueOf) ?: ManagementMethodology.AGILE
    var error by remember {mutableStateOf<String?>(null)}
    var busy by remember {mutableStateOf(false)}
    val scope=rememberCoroutineScope()
    fun save(enabled:Boolean) {busy=true;error=null;scope.launch {
        try {repository.configure(project,node,enabled,method);onDismiss()}
        catch(c:CancellationException) {throw c}
        catch(e:Exception) {error=e.message ?: "No se pudo guardar. Los datos se conservaron."}
        finally {busy=false}
    }}
    AlertDialog(onDismissRequest={if(!busy) onDismiss()},title={Text("Planificación")},text={Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
        Text(if(context==null) "Activar Planificación añade Acta, Backlog, Sprints y Kanban como capas reales, con acceso a Gantt. Las capas personales se conservan." else "Desactivar Planificación conserva las capas, vínculos y trabajo. Modo Sprint se configura por separado.",style=MaterialTheme.typography.bodySmall)
        if(context?.enabled==true) TextButton(enabled=!busy,onClick={save(false)}) {Text("Desactivar")}
        error?.let {Text(it,color=MaterialTheme.colorScheme.error)}
    }},confirmButton={TextButton(enabled=!busy,onClick={save(true)}) {Text(if(context?.enabled==true) "Guardar" else "Activar Planificación")}},dismissButton={TextButton(enabled=!busy,onClick=onDismiss) {Text("Cancelar")}})
}

@Composable internal fun ProjectPlanningDialog(projectId:String,onDismiss:()->Unit) {
    val app=androidx.compose.ui.platform.LocalContext.current.applicationContext as com.r0ybt.arachn0de.Arachn0deApplication
    var loaded by remember(projectId) {mutableStateOf(false)}
    var context by remember(projectId) {mutableStateOf<ManagementContextEntity?>(null)}
    var error by remember(projectId) {mutableStateOf(false)}
    LaunchedEffect(projectId) {
        try {
            val root=app.database.conversionDao().project(projectId)
            context=app.database.managementContextDao().all().firstOrNull {it.id==root?.id}
            loaded=true
        } catch(c:CancellationException) {throw c} catch(_:Exception) {error=true}
    }
    if(loaded) ManagementConfiguration(projectId,null,context,app.nodeRepository.management,onDismiss)
    else if(error) AlertDialog(onDismissRequest=onDismiss,title={Text("Planificación")},text={Text("No se pudo cargar la planificación. Reintenta desde el menú del proyecto.")},confirmButton={TextButton(onClick=onDismiss) {Text("Cerrar")}})
}
