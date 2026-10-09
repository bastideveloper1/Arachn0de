package com.r0ybt.arachn0de.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.r0ybt.arachn0de.Arachn0deApplication
import com.r0ybt.arachn0de.data.local.SavedTemplateEntity
import com.r0ybt.arachn0de.templates.*
import com.r0ybt.arachn0de.ui.state.EditorDraft
import com.r0ybt.arachn0de.ui.state.OperationState
import com.r0ybt.arachn0de.domain.model.*
import java.util.*

@Composable
internal fun SavedTemplatesAccess(draft:EditorDraft,enabled:Boolean) {
    var open by rememberSaveable(draft.creationId) {mutableStateOf(false)}
    TextButton(enabled=enabled,onClick={open=true}) {Text("Guardados")}
    if(open) SavedTemplatesLibrary(draft) {open=false}
}

@Composable
internal fun SavedTemplatesLibrary(draft:EditorDraft,onDismiss:()->Unit) {
    val app=LocalContext.current.applicationContext as? Arachn0deApplication ?: return
    val repo=app.savedTemplateRepository
    val rows by remember(repo){repo.observe()}.collectAsState(initial=emptyList())
    var query by rememberSaveable {mutableStateOf("")}
    var selected by remember {mutableStateOf<TemplateApplication?>(null)}
    var editing by remember {mutableStateOf<SavedTemplateEntity?>(null)}
    var deleting by remember {mutableStateOf<SavedTemplateEntity?>(null)}
    val scope=rememberCoroutineScope();val op=remember(repo,scope){OperationState(scope)}
    fun apply(application:TemplateApplication) {
        draft.removedAttachmentIds=(draft.removedAttachmentIds+AttachmentReferences.ids(draft.description)).distinct()
        application.configuration.apply(draft,System.currentTimeMillis(),TimeZone.getDefault());draft.technologiesLoaded=true;onDismiss()
    }
    AlertDialog(onDismissRequest={if(!op.busy) onDismiss()},title={Text("Guardados")},text={Column {
        OutlinedTextField(query,{query=it},label={Text("Buscar por nombre")},singleLine=true,modifier=Modifier.fillMaxWidth())
        Text("Rellena el formulario; tú decides cuándo crear la tarea.",style=MaterialTheme.typography.bodySmall)
        LazyColumn(Modifier.heightIn(max=420.dp)) {
            items(rows.filter {it.name.contains(query,ignoreCase=true)},key={it.id}) {row->
                Column {
                    TextButton(enabled=!op.busy,onClick={var prepared:TemplateApplication?=null;op.submit("No se pudo leer la plantilla.",{prepared=repo.prepare(row);true},{val a=requireNotNull(prepared);if(draft.hasWork || a.warnings.isNotEmpty()) selected=a else apply(a)})}) {Column {Text(row.name);Text(SavedTemplateCodec.decode(row.payload).let { if(it.metroPlan!=null) "Viaje Metro" else if(it.amount!=null) "Pago" else if(it.purpose==NodePurpose.NOTE) "Nota" else "Tarea" },style=MaterialTheme.typography.labelSmall)}}
                    Row {TextButton(enabled=!op.busy,onClick={editing=row}) {Text("Editar / renombrar")};TextButton(enabled=!op.busy,onClick={deleting=row}) {Text("Eliminar")}}
                }
            }
        }
        if(rows.isEmpty()) Text("Guarda una tarea desde ⋮ → Guardar como plantilla.")
    }},confirmButton={TextButton(enabled=!op.busy,onClick=onDismiss) {Text("Cerrar")}})
    selected?.let {a->AlertDialog(onDismissRequest={selected=null},title={Text(if(draft.hasWork) "¿Reemplazar el borrador?" else "Referencias no disponibles")},text={Text((if(draft.hasWork) "Se sustituirán los campos del borrador, incluidas fechas, participantes, tecnologías y modalidades de creación. No se crea ni ejecuta una tarea.\n" else "")+a.warnings.joinToString("\n")+(if(a.warnings.isNotEmpty()) "\nPuedes continuar sin estas referencias." else ""))},confirmButton={TextButton(onClick={selected=null;apply(a)}) {Text("Aplicar plantilla")}},dismissButton={TextButton(onClick={selected=null}) {Text("Cancelar")}})}
    deleting?.let {row->AlertDialog(onDismissRequest={deleting=null},title={Text("¿Eliminar ${row.name}?")},text={Text("Las tareas creadas antes se conservan.")},confirmButton={TextButton(enabled=!op.busy,onClick={op.submit("No se pudo eliminar.",{repo.delete(row.id);true},{deleting=null})}) {Text("Eliminar")}},dismissButton={TextButton(onClick={deleting=null}) {Text("Cancelar")}})}
    editing?.let {row->TemplateEditor(row,false,repo) {editing=null}}
    OperationErrorDialog(op)
}

@Composable
internal fun SaveTaskTemplateDialog(nodeId:String,onDismiss:()->Unit) {
    val app=LocalContext.current.applicationContext as? Arachn0deApplication ?: return
    var row by remember(nodeId) {mutableStateOf<SavedTemplateEntity?>(null)}
    var error by remember {mutableStateOf(false)}
    LaunchedEffect(nodeId) {try {val c=app.savedTemplateRepository.fromTask(nodeId);row=SavedTemplateEntity(UUID.randomUUID().toString(),c.title,SavedTemplateCodec.encode(c))} catch(c:kotlinx.coroutines.CancellationException) {throw c} catch(_:Exception) {error=true}}
    val loaded=row
    if(loaded!=null) TemplateEditor(loaded,true,app.savedTemplateRepository,onDismiss)
    else AlertDialog(onDismissRequest=onDismiss,title={Text(if(error) "No se pudo leer la tarea" else "Preparando plantilla")},confirmButton={TextButton(onClick=onDismiss) {Text("Cerrar")}})
}

@Composable
private fun TemplateEditor(row:SavedTemplateEntity,new:Boolean,repo:SavedTemplateRepository,onDismiss:()->Unit) {
    val c=remember(row.id){SavedTemplateCodec.decode(row.payload)}
    val draft=requireNotNull(rememberSaveable(row.id,stateSaver=EditorDraft.Saver) {mutableStateOf<EditorDraft?>(EditorDraft("template:${row.id}",null,c.title,c.description))}.value)
    LaunchedEffect(row.id) {if(!draft.technologiesLoaded) {c.apply(draft,System.currentTimeMillis(),TimeZone.getDefault());draft.startAt=null;draft.dueAt=null;draft.startEnabled=false;draft.dueEnabled=false;draft.technologiesLoaded=true};draft.attachmentsLoaded=true}
    var name by rememberSaveable(row.id) {mutableStateOf(row.name)}
    var startRaw by rememberSaveable(row.id) {mutableStateOf("${c.start.kind}:${c.start.number}:${c.start.minute}")}
    var dueRaw by rememberSaveable(row.id) {mutableStateOf("${c.due.kind}:${c.due.number}:${c.due.minute}")}
    fun date(raw:String):TemplateDate {val p=raw.split(':');return TemplateDate(TemplateDateKind.valueOf(p[0]),p[1].toInt(),p[2].toInt())}
    val valid=remember { mutableStateMapOf<String,Boolean>() }
    val scope=rememberCoroutineScope();val op=remember(repo,scope){OperationState(scope)}
    val app=LocalContext.current.applicationContext as? Arachn0deApplication
    val people by remember(app){app?.personRepository?.observePeople() ?: kotlinx.coroutines.flow.flowOf(emptyList())}.collectAsState(initial=emptyList())
    NodeDialog(draft,op.busy,onDismiss,{_,_->op.submit("No se pudo guardar la plantilla. Revisa nombre, referencias y fechas.",{
        val money=draft.obligation()
        val config=c.copy(title=draft.title.trim(),description=draft.description,purpose=draft.purpose,priority=draft.priority,amount=money?.amountMinor,currency=money?.currencyCode,people=draft.responsibleIds.distinct(),technologies=draft.technologyIds.distinct(),tags=draft.tagIds.distinct(),start=date(startRaw),due=date(dueRaw),metroPlan=draft.templateMetroPlan,metroCatalog=draft.templateMetroCatalog,traveler=draft.templateTraveler)
        repo.save(row.copy(name=name.trim(),payload=SavedTemplateCodec.encode(config)),new);true
    },onDismiss)},people=people,tagRepository=app?.nodeRepository?.tags,templateEditing=true,extraValid=name.trim().isNotEmpty() && name.trim().length<=240 && valid.values.all {it},extraFields={
        OutlinedTextField(name,{name=it},label={Text("Nombre de la plantilla")},singleLine=true,modifier=Modifier.fillMaxWidth())
        Text("Fechas se calculan al aplicar. Al guardar desde una tarea se propone Hoy con su hora. Puedes cambiarlas. Las imágenes adjuntas se excluyen; se conserva el texto.",style=MaterialTheme.typography.bodySmall)
        TemplateDateFields("Inicio",date(startRaw),{valid["start"]=it}) {startRaw="${it.kind}:${it.number}:${it.minute}"}
        TemplateDateFields("Vencimiento",date(dueRaw),{valid["due"]=it}) {dueRaw="${it.kind}:${it.number}:${it.minute}"}
        if(draft.templateMetroPlan!=null) Text("Plan Metro sin sesiones ni historial")
    })
    OperationErrorDialog(op)
}

@Composable
internal fun TemplateDateFields(label:String,value:TemplateDate,onValidity:(Boolean)->Unit = {},onChange:(TemplateDate)->Unit) {
    Text(label,style=MaterialTheme.typography.titleSmall)
    var menu by remember {mutableStateOf(false)}
    fun label(k:TemplateDateKind)=when(k) {TemplateDateKind.NONE->"Sin fecha";TemplateDateKind.TODAY->"Hoy";TemplateDateKind.TOMORROW->"Mañana";TemplateDateKind.IN_DAYS->"Dentro de N días";TemplateDateKind.DAY_NEXT_MONTH->"Día N del próximo mes";TemplateDateKind.FIRST_DAY_NEXT->"Primer día del próximo mes";TemplateDateKind.LAST_DAY->"Último día del mes"}
    Box {OutlinedButton(onClick={menu=true}) {Text(label(value.kind))};DropdownMenu(menu,{menu=false}) {TemplateDateKind.entries.forEach {k->DropdownMenuItem(text={Text(label(k))},onClick={menu=false;onChange(TemplateDate(k,when(k){TemplateDateKind.IN_DAYS->7;TemplateDateKind.DAY_NEXT_MONTH->15;else->0},value.minute))})}}}
    var numberValid by remember {mutableStateOf(true)}
    var hourValid by remember {mutableStateOf(true)}
    LaunchedEffect(numberValid,hourValid,value.kind) {onValidity(numberValid && hourValid)}
    LaunchedEffect(value.kind) {numberValid=true;hourValid=true}
    if(value.kind in listOf(TemplateDateKind.IN_DAYS,TemplateDateKind.DAY_NEXT_MONTH)) {
        var number by remember(value.kind,value.number){mutableStateOf(value.number.toString())}
        OutlinedTextField(number,{text->number=text;val n=text.toIntOrNull();numberValid=n!=null && n in 1..(if(value.kind==TemplateDateKind.IN_DAYS) 3650 else 31);if(numberValid) onChange(value.copy(number=requireNotNull(n)))},label={Text("N")},isError=!numberValid,singleLine=true,modifier=Modifier.widthIn(max=160.dp))
    }
    if(value.kind!=TemplateDateKind.NONE) {
        var hour by remember(value.minute){mutableStateOf("%02d:%02d".format(Locale.ROOT,value.minute/60,value.minute%60))}
        OutlinedTextField(hour,{text->hour=text;val p=text.split(':');val h=p.getOrNull(0)?.toIntOrNull();val m=p.getOrNull(1)?.toIntOrNull();hourValid=p.size==2 && h!=null && h in 0..23 && m!=null && m in 0..59;if(hourValid) onChange(value.copy(minute=requireNotNull(h)*60+requireNotNull(m)))},label={Text("Hora (HH:mm)")},isError=!hourValid,singleLine=true,modifier=Modifier.widthIn(max=180.dp))
        Text("Si el día no existe, se usa el último día válido del mes.",style=MaterialTheme.typography.bodySmall)
    }
}
