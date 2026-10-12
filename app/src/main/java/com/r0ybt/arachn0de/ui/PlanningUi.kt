package com.r0ybt.arachn0de.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
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
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

private data class PlanningEdit(val title:String,val fields:List<Pair<String,String>>,val save:suspend (List<String>)->Unit)
private data class PlanningDelete(val title:String,val text:String,val action:suspend ()->Unit)
private fun moved(ids:List<String>,id:String,offset:Int):List<String> = ids.toMutableList().apply {
    val index=indexOf(id);val target=index+offset
    if(index>=0 && target in indices) {removeAt(index);add(target,id)}
}

@Composable internal fun PlanningDialog(contextId:String,ownerTitle:String,ownerDescription:String,repository:PlanningRepository,onDismiss:()->Unit)=
    PlanningDialog(contextId,ownerTitle,ownerDescription,repository,null,onDismiss)

@OptIn(ExperimentalLayoutApi::class)
@Composable internal fun PlanningDialog(contextId:String,ownerTitle:String,ownerDescription:String,repository:PlanningRepository,initialObjectiveId:String?,onDismiss:()->Unit) {
    val loadedState by remember(repository,contextId) {repository.observe(contextId)}.collectAsState<PlanningState, PlanningState?>(initial=null)
    val state=loadedState ?: PlanningState()
    var selected by rememberSaveable(contextId,initialObjectiveId) {mutableStateOf(initialObjectiveId)}
    var edit by remember {mutableStateOf<PlanningEdit?>(null)}
    var deleting by remember {mutableStateOf<PlanningDelete?>(null)}
    var busy by remember {mutableStateOf(false)};var error by remember {mutableStateOf<String?>(null)}
    val scope=rememberCoroutineScope()
    fun perform(action:suspend ()->Unit) {if(busy) return;busy=true;error=null;scope.launch {
        try {action()}
        catch(c:CancellationException) {throw c}
        catch(e:Exception) {error=e.message ?: "No se pudo guardar la planificación."}
        finally {busy=false}
    }}
    val goal=state.objectives.firstOrNull {it.id==selected}
    fun editGoal(row:ManagementObjectiveEntity?) {edit=PlanningEdit(if(row==null) "Crear objetivo" else "Editar objetivo",listOf("Título" to row?.title.orEmpty(),"Descripción" to row?.description.orEmpty())) {values->
        if(row==null) repository.createObjective(contextId,values[0],values[1]) else repository.editObjective(row.id,values[0],values[1])
    }}
    fun editCriterion(row:AcceptanceCriterionEntity?,objectiveId:String) {edit=PlanningEdit(if(row==null) "Crear criterio" else "Editar criterio",listOf("Criterio verificable" to row?.description.orEmpty())) {values->
        if(row==null) repository.createCriterion(objectiveId,values[0]) else repository.editCriterion(row.id,values[0])
    }}
    PlanningSurface({if(!busy) onDismiss()}) {
        Text("Planificación",style=MaterialTheme.typography.titleLarge)
        error?.let {Text(it,color=MaterialTheme.colorScheme.error)}
        if(loadedState!=null) LazyColumn(Modifier.weight(1f).fillMaxWidth().testTag("planning-list"),state=rememberLazyListState(),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            item {
                Text(ownerTitle,style=MaterialTheme.typography.titleMedium)
                if(ownerDescription.isNotBlank()) Text(ownerDescription)
            }
            if(goal==null) {
                item {
                    Text("Acta del proyecto",style=MaterialTheme.typography.titleMedium)
                    val charter=state.charter ?: ProjectCharterEntity(contextId)
                    val fields=listOf("Propósito" to charter.purpose,"Alcance" to charter.scope,"Exclusiones" to charter.exclusions,"Entregables principales" to charter.deliverables,"Condiciones generales de éxito" to charter.successConditions)
                    fields.filter {it.second.isNotBlank()}.forEach {(label,value)->Text(label,style=MaterialTheme.typography.labelLarge);Text(value)}
                    if(fields.all {it.second.isBlank()}) Text("Puedes completar el acta cuando lo necesites.")
                    TextButton(enabled=!busy,onClick={edit=PlanningEdit("Editar acta",fields) {values->repository.saveCharter(ProjectCharterEntity(contextId,values[0],values[1],values[2],values[3],values[4]))}}) {Text("Editar acta")}
                    Text("Objetivos",style=MaterialTheme.typography.titleMedium)
                    TextButton(enabled=!busy,onClick={editGoal(null)}) {Text("Crear objetivo")}
                    if(state.objectives.isEmpty()) Text("No hay objetivos registrados.")
                }
                items(state.objectives,key={it.id}) {row->
                    Card(Modifier.fillMaxWidth().clickable(enabled=!busy) {selected=row.id}) {Column(Modifier.padding(12.dp)) {
                        Text(row.title,style=MaterialTheme.typography.titleMedium)
                        if(row.description.isNotBlank()) Text(row.description,maxLines=2,overflow=TextOverflow.Ellipsis)
                        Text(objectiveAcceptance(state.criteria.filter {it.objectiveId==row.id}).label)
                        FlowRow {
                            TextButton(enabled=!busy,onClick={selected=row.id}) {Text("Ver detalle")}
                            TextButton(enabled=!busy,onClick={editGoal(row)}) {Text("Editar")}
                            TextButton(enabled=!busy,modifier=Modifier.semantics {contentDescription="Eliminar objetivo: ${row.title}"},onClick={deleting=PlanningDelete("Eliminar objetivo","Se eliminará «${row.title}» y todos sus criterios, responsabilidades y vínculos con épicas. Esta acción no se puede deshacer.") {repository.deleteObjective(row.id);selected=null;deleting=null}}) {Text("Eliminar")}
                        }
                        OrderButtons("objetivo",row.title,state.objectives.indexOf(row),state.objectives.size,busy) {offset->perform {repository.reorderObjectives(contextId,moved(state.objectives.map {it.id},row.id,offset))}}
                    }}
                }
            } else {
                val criteria=state.criteria.filter {it.objectiveId==goal.id}
                item {
                    TextButton(onClick={selected=null}) {Text("Volver a objetivos")}
                    ResponsibilitiesAccess(contextId,goal.id,true,repository.participation)
                    Text(goal.title,style=MaterialTheme.typography.titleLarge)
                    if(goal.description.isNotBlank()) Text(goal.description)
                    Text(objectiveAcceptance(criteria).label)
                    Text("Qué falta para cumplirlo",style=MaterialTheme.typography.titleMedium)
                    when {
                        criteria.isEmpty()->Text("Primero deben definirse criterios de aceptación.")
                        criteria.all {it.validated}->Text("No quedan criterios pendientes.")
                        else->criteria.filterNot {it.validated}.forEach {Text("• ${it.description}")}
                    }
                    TextButton(enabled=!busy,onClick={editCriterion(null,goal.id)}) {Text("Crear criterio")}
                }
                items(criteria,key={it.id}) {row->Card(Modifier.fillMaxWidth()) {Column(Modifier.padding(12.dp)) {
                    Text(row.description)
                    Text(if(row.validated) "Validado · ${DateFormat.getDateTimeInstance().format(Date(requireNotNull(row.validatedAt)))}" else "Pendiente")
                    FlowRow {
                        TextButton(enabled=!busy,modifier=Modifier.semantics {contentDescription="${if(row.validated) "Revocar" else "Validar"} criterio: ${row.description}"},onClick={perform {repository.validateCriterion(row.id,!row.validated)}}) {Text(if(row.validated) "Revocar validación" else "Validar criterio")}
                        TextButton(enabled=!busy,onClick={editCriterion(row,goal.id)}) {Text("Editar criterio")}
                        TextButton(enabled=!busy,modifier=Modifier.semantics {contentDescription="Eliminar criterio: ${row.description}"},onClick={deleting=PlanningDelete("Eliminar criterio","Se eliminará «${row.description}» y su validación, si existe. Esta acción no se puede deshacer.") {repository.deleteCriterion(row.id);deleting=null}}) {Text("Eliminar")}
                    }
                    OrderButtons("criterio",row.description,criteria.indexOf(row),criteria.size,busy) {offset->perform {repository.reorderCriteria(goal.id,moved(criteria.map {it.id},row.id,offset))}}
                }}}
            }
        }
        TextButton(enabled=!busy,onClick=onDismiss) {Text("Cerrar planificación")}
    }
    edit?.let {editor->PlanningEditor(editor) {edit=null}}
    deleting?.let {request->AlertDialog(onDismissRequest={if(!busy) deleting=null},title={Text(request.title)},text={Text(request.text)},confirmButton={TextButton(enabled=!busy,onClick={perform(request.action)}) {Text("Confirmar eliminación")}},dismissButton={TextButton(enabled=!busy,onClick={deleting=null}) {Text("Cancelar")}})}
}

@Composable private fun OrderButtons(kind:String,label:String,index:Int,count:Int,busy:Boolean,onMove:(Int)->Unit) {
    Row {
        TextButton(enabled=!busy && index>0,modifier=Modifier.semantics {contentDescription="Subir $kind: $label"},onClick={onMove(-1)}) {Text("Subir")}
        TextButton(enabled=!busy && index<count-1,modifier=Modifier.semantics {contentDescription="Bajar $kind: $label"},onClick={onMove(1)}) {Text("Bajar")}
    }
}
@Composable private fun PlanningSurface(onDismiss:()->Unit,content:@Composable ColumnScope.()->Unit) {
    Dialog(onDismissRequest=onDismiss,properties=DialogProperties(usePlatformDefaultWidth=false)) {
        Surface(Modifier.fillMaxWidth().fillMaxHeight(.9f).safeDrawingPadding().padding(12.dp),shape=MaterialTheme.shapes.large) {
            Column(Modifier.padding(12.dp),verticalArrangement=Arrangement.spacedBy(8.dp),content=content)
        }
    }
}
@Composable private fun PlanningEditor(editor:PlanningEdit,onDismiss:()->Unit) {
    var values by remember {mutableStateOf(editor.fields.map {it.second})}
    var busy by remember {mutableStateOf(false)};var error by remember {mutableStateOf<String?>(null)}
    val scope=rememberCoroutineScope()
    PlanningSurface({if(!busy) onDismiss()}) {
        Text(editor.title,style=MaterialTheme.typography.titleLarge)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            editor.fields.forEachIndexed {index,(label,_)->OutlinedTextField(values[index],{text->values=values.toMutableList().apply {this[index]=text}},enabled=!busy,label={Text(label)},modifier=Modifier.fillMaxWidth())}
        }
        error?.let {Text(it,color=MaterialTheme.colorScheme.error)}
        Row {
            TextButton(enabled=!busy,onClick=onDismiss) {Text("Cancelar")}
            TextButton(enabled=!busy,onClick={busy=true;error=null;scope.launch {
                try {editor.save(values);onDismiss()}
                catch(c:CancellationException) {throw c}
                catch(e:Exception) {error=e.message ?: "No se pudo guardar."}
                finally {busy=false}
            }}) {Text("Guardar")}
        }
    }
}
