package com.r0ybt.arachn0de.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.r0ybt.arachn0de.Arachn0deApplication
import com.r0ybt.arachn0de.data.local.SavedTemplateEntity
import com.r0ybt.arachn0de.templates.*
import com.r0ybt.arachn0de.ui.state.OperationState

@OptIn(ExperimentalMaterial3Api::class)
@Composable internal fun SaveStructurePanel(project:String?,node:String?,title:String,onDismiss:()->Unit) {
    val app=LocalContext.current.applicationContext as? Arachn0deApplication ?: return
    var name by remember {mutableStateOf(title)};var files by remember {mutableStateOf(true)}
    val scope=rememberCoroutineScope();val op=remember {OperationState(scope)}
    ModalBottomSheet(onDismissRequest={if(!op.busy)onDismiss()}) {
        Column(Modifier.fillMaxWidth().padding(14.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            Text("Guardar como plantilla",style=MaterialTheme.typography.titleMedium)
            OutlinedTextField(name,{name=it},label={Text("Nombre")},enabled=!op.busy,modifier=Modifier.fillMaxWidth())
            Row {Checkbox(files,{files=it},enabled=!op.busy);Text("Incluir imágenes y adjuntos")}
            Text("Incluye la estructura completa. Al insertar se reinician estados, fechas e historial. No se copian Personas, recurrencias ni vínculos externos.",style=MaterialTheme.typography.bodySmall)
            Text("Usa el contenido guardado del elemento.",style=MaterialTheme.typography.bodySmall)
            TextButton(enabled=!op.busy && name.isNotBlank() && name.length<=240,onClick={op.submit("No se pudo guardar la plantilla. El original se conserva.",{
                val row=app.savedTemplateRepository.captureStructure(app.privateContext,app.attachmentRepository,project,node,files)
                app.savedTemplateRepository.save(row.copy(name=name.trim()),true);true
            },onDismiss)}) {Text("Guardar")}
        }
    }
    OperationErrorDialog(op)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable internal fun StructureCreationPanel(project:String?,parent:String?,onNew:()->Unit,draftDestination:Boolean=false,openLibrary:Boolean=false,onDismiss:()->Unit) {
    val app=LocalContext.current.applicationContext as? Arachn0deApplication ?: return
    val rows by remember(app) {app.savedTemplateRepository.observe()}.collectAsState(initial=emptyList())
    var library by remember {mutableStateOf(openLibrary)};var selected by remember {mutableStateOf<SavedTemplateEntity?>(null)}
    var deleting by remember {mutableStateOf<SavedTemplateEntity?>(null)}
    val scope=rememberCoroutineScope();val op=remember {OperationState(scope)}
    val parentKind by produceState<String?>(null,parent) {value=parent?.let {kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {app.database.nodeDao().getById(it)?.purpose}}}
    val compatible=remember(rows,project,parentKind,draftDestination) {rows.filter {row->runCatching {
        val kind=StructuralTemplateCodec.document(row.payload).getJSONArray("elements").getJSONObject(0).getString("kind")
        if(draftDestination)kind in setOf("ACTION","NOTE") else
            ((kind=="CORE")== (project==null)) && (kind!="LAYER" || parent==null || parentKind=="LAYER")
    }.getOrDefault(false)}}
    ModalBottomSheet(onDismissRequest={if(!op.busy)onDismiss()}) {
        Column(Modifier.fillMaxWidth().heightIn(max=460.dp).verticalScroll(rememberScrollState()).padding(14.dp).testTag("structure-template-panel"),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            Text(if(draftDestination)"Crear borrador" else if(project==null)"Crear Núcleo" else "Crear elemento",style=MaterialTheme.typography.titleMedium)
            if(!library) {
                TextButton(onClick=onNew,enabled=!op.busy,modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)) {Text("Nuevo")}
                TextButton(onClick={library=true},enabled=!op.busy,modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)) {Text("Desde plantilla")}
            } else {
                Text("Desde plantilla",style=MaterialTheme.typography.titleSmall)
                if(compatible.isEmpty())Text("No hay plantillas compatibles. Guarda una desde Opciones.")
                compatible.forEach {row->Row(Modifier.fillMaxWidth()) {
                    TextButton(onClick={selected=row},enabled=!op.busy,modifier=Modifier.weight(1f)) {Text(row.name)}
                    TextButton(onClick={deleting=row},enabled=!op.busy) {Text("Eliminar")}
                }}
            }
        }
    }
    selected?.let {row->
        val elements=remember(row) {StructuralTemplateCodec.document(row.payload).getJSONArray("elements")}
        val kind=when(elements.getJSONObject(0).getString("kind")) {"CORE"->"Núcleo";"LAYER"->"Capa";"NOTE"->"Nota";else->"Tarea"}
        AlertDialog(onDismissRequest={if(!op.busy)selected=null},title={Text(row.name)},text={Text("$kind · ${elements.length()} elementos\nSe añadirán copias independientes en Pendiente. Sin fechas, recurrencias, Personas ni historial operativo.")},
            confirmButton={TextButton(enabled=!op.busy,onClick={op.submit("No se pudo insertar. No se reemplazó contenido existente.",{
                app.savedTemplateRepository.insertStructure(app.privateContext,app.attachmentRepository,row,project,parent);true
            },onDismiss)}) {Text("Insertar")}},dismissButton={TextButton(enabled=!op.busy,onClick={selected=null}) {Text("Cancelar")}})
    }
    deleting?.let {row->AlertDialog(onDismissRequest={if(!op.busy)deleting=null},title={Text("¿Eliminar ${row.name}?")},text={Text("Las instancias insertadas se conservan.")},confirmButton={TextButton(enabled=!op.busy,onClick={op.submit("No se pudo eliminar.",{app.savedTemplateRepository.delete(row.id);true},{deleting=null})}) {Text("Eliminar")}},dismissButton={TextButton(onClick={deleting=null}) {Text("Cancelar")}})}
    OperationErrorDialog(op)
}
