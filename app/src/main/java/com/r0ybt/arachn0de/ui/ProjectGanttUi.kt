package com.r0ybt.arachn0de.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.nativeCanvas
import kotlinx.coroutines.launch
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.r0ybt.arachn0de.data.repository.*
import com.r0ybt.arachn0de.data.local.NodeEntity
import java.time.Instant
import java.time.ZoneId

internal data class GanttRow(val node:NodeEntity,val start:Long?,val end:Long?,val progress:Float)
/** Timeline is a read projection; there is no second schedule or progress store. */
internal fun projectTimeline(state:IterationState,context:String):List<GanttRow> {
    val plans=state.records.plans.associateBy {it.sprintId}
    val roles=state.work.records.roles.filter {it.role=="STORY"}.mapTo(hashSetOf()) {it.nodeId}
    val tree=state.work.nodes;val byId=tree.associateBy {it.id};val parents=tree.mapTo(hashSetOf()) {it.parentId}
    return tree.filter {state.work.owners[it.id]==context && (it.purpose=="ACTION" || it.sprintMode || it.id in roles)}.map {n->
        val children=if(n.id in roles) storyWorkIds(tree,n.id,state.work.records.storyTasks).mapNotNull(byId::get) else emptyList()
        val leaves=children.filter {it.id !in parents};val plan=plans[n.id]
        val progress=when {n.sprintMode->((state.progress(n.id) ?: 0.0)/100).toFloat();leaves.isNotEmpty()->leaves.count {it.isCompleted}.toFloat()/leaves.size;else->if(n.isCompleted) 1f else 0f}
        GanttRow(n,plan?.startAt ?: n.startAt ?: children.mapNotNull {it.startAt}.minOrNull(),plan?.endAt ?: n.dueAt ?: children.mapNotNull {it.dueAt}.maxOrNull(),progress)
    }.sortedWith(compareBy<GanttRow> {it.start ?: Long.MAX_VALUE}.thenBy {it.node.title})
}
@Composable internal fun ProjectGanttDialog(context:String,repository:ManagementRepository,onOpenNode:(String)->Unit,projectTitle:String="Gantt",onDismiss:()->Unit) {
    val state by remember(repository,context) {repository.iterations.observe(context)}.collectAsState(initial=IterationState())
    val sprintStories by remember(repository) {repository.gantt.stories}.collectAsState(initial=emptyList())
    val annotations by remember(repository,context) {repository.gantt.annotations(context)}.collectAsState(initial=emptyList())
    val traditional by remember(repository) {repository.iterations.assignments}.collectAsState(initial=emptyList())
    val responsibility=remember(state.people.records.nodes,traditional) {workResponsibilities(state.people.records.nodes,traditional.map {it.nodeId to it.id})}
    var controls by rememberSaveable(context) {mutableStateOf(false)}
    var scopeName by rememberSaveable(context) {mutableStateOf("SPRINT")}
    var scaleName by rememberSaveable(context) {mutableStateOf("DAY")}
    var sprint by rememberSaveable(context) {mutableStateOf<String?>(null)}
    var anchor by rememberSaveable(context) {mutableStateOf(java.time.LocalDate.now().toString())}
    var sliding by rememberSaveable(context) {mutableStateOf(false)}
    var person by rememberSaveable(context) {mutableStateOf<String?>(null)}
    var draft by remember(context) {mutableStateOf<com.r0ybt.arachn0de.data.local.GanttAnnotationEntity?>(null)}
    var error by remember {mutableStateOf<String?>(null)}
    var busy by remember {mutableStateOf(false)}
    val local=androidx.compose.ui.platform.LocalContext.current
    val coroutine=rememberCoroutineScope()
    fun perform(action:suspend ()->Unit) {if(busy) return;busy=true;error=null;coroutine.launch {
        try {action()} catch(c:kotlinx.coroutines.CancellationException) {throw c} catch(e:Exception) {error=e.message ?: "No se pudo completar la operación. Los datos se conservan."} finally {busy=false}
    }}
    val selection=com.r0ybt.arachn0de.gantt.GanttSelection(com.r0ybt.arachn0de.gantt.GanttScope.valueOf(scopeName),com.r0ybt.arachn0de.gantt.GanttScale.valueOf(scaleName),sprint,java.time.LocalDate.parse(anchor),person=person,sliding=sliding)
    val available=state.work.nodes.filter {it.sprintMode && state.work.owners[it.id]==context}
    LaunchedEffect(available,state.records.plans) {
        if(available.none {it.id==sprint}) sprint=available.firstOrNull {n->state.records.plans.any {it.sprintId==n.id && it.state=="ACTIVE"}}?.id ?: available.firstOrNull()?.id
    }
    val result=remember(state,selection,responsibility,annotations,sprintStories) {runCatching {com.r0ybt.arachn0de.gantt.ganttDocument(state,context,selection,responsibility,annotations,projectTitle,sprintStories=sprintStories)}}
    val doc=result.getOrNull()
    val pageResult=remember(doc) {runCatching {doc?.let {com.r0ybt.arachn0de.gantt.GanttRenderer.pages(it)}.orEmpty()}}
    val pages=pageResult.getOrDefault(emptyList())
    val export=androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.CreateDocument("image/png")) {uri->if(uri!=null && doc!=null && pages.size==1) perform {
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {local.contentResolver.openOutputStream(uri)?.use {com.r0ybt.arachn0de.gantt.GanttRenderer.png(doc,pages.single(),true,it)} ?: error("No se pudo abrir el destino.")}
        error="PNG exportado."
    }}
    val exportAll=androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.OpenDocumentTree()) {uri->if(uri!=null && doc!=null) perform {
        var written=0
        try {kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            val directory=android.provider.DocumentsContract.buildDocumentUriUsingTree(uri,android.provider.DocumentsContract.getTreeDocumentId(uri))
            val batch=java.util.UUID.randomUUID().toString().take(8)
            for(page in pages) {
                val suffix=if(page.comments.isEmpty()) "Gantt" else "Anotaciones"
                val destination=android.provider.DocumentsContract.createDocument(local.contentResolver,directory,"image/png","ARACHN0DE-$batch-${page.index}-$suffix.png") ?: error("No se pudo crear PNG.")
                try {local.contentResolver.openOutputStream(destination)?.use {com.r0ybt.arachn0de.gantt.GanttRenderer.png(doc,page,true,it)} ?: error("No se pudo escribir PNG.");written++}
                catch(e:Exception) {runCatching {android.provider.DocumentsContract.deleteDocument(local.contentResolver,destination)};throw e}
            }
        };error="Exportación completa: $written PNG."} catch(e:Exception) {error="Exportación incompleta ($written PNG): ${e.message}"}
    }}
    val p=com.r0ybt.arachn0de.gantt.GanttPalette
    MaterialTheme(colorScheme=MaterialTheme.colorScheme.copy(primary=androidx.compose.ui.graphics.Color(p.TEXT),onPrimary=androidx.compose.ui.graphics.Color(p.BACKGROUND),primaryContainer=androidx.compose.ui.graphics.Color(p.DAY),onPrimaryContainer=androidx.compose.ui.graphics.Color(p.TEXT),secondary=androidx.compose.ui.graphics.Color(p.SECONDARY),onSecondary=androidx.compose.ui.graphics.Color(p.BACKGROUND),secondaryContainer=androidx.compose.ui.graphics.Color(p.DAY),onSecondaryContainer=androidx.compose.ui.graphics.Color(p.TEXT),surface=androidx.compose.ui.graphics.Color(p.BACKGROUND),onSurface=androidx.compose.ui.graphics.Color(p.TEXT),surfaceVariant=androidx.compose.ui.graphics.Color(p.DAY),onSurfaceVariant=androidx.compose.ui.graphics.Color(p.SECONDARY),background=androidx.compose.ui.graphics.Color(p.BACKGROUND),onBackground=androidx.compose.ui.graphics.Color(p.TEXT),outline=androidx.compose.ui.graphics.Color(p.BORDER),error=androidx.compose.ui.graphics.Color(p.TEXT))) {
        Dialog(onDismissRequest={if(!busy) onDismiss()},properties=DialogProperties(usePlatformDefaultWidth=false)) {
            Surface(Modifier.fillMaxSize().safeDrawingPadding()) {Column(Modifier.padding(8.dp),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                Row(Modifier.fillMaxWidth(),verticalAlignment=androidx.compose.ui.Alignment.CenterVertically) {
                    Text(doc?.heading ?: projectTitle,Modifier.weight(1f),style=MaterialTheme.typography.titleSmall)
                    TextButton(onClick={controls=!controls}) {Text("Opciones")}
                    TextButton(enabled=!busy,onClick=onDismiss) {Text("Volver")}
                }
                doc?.let {Text("${it.sprintProgress?.toInt()?.toString() ?: "—"}% · ${com.r0ybt.arachn0de.gantt.ganttDate(it.first)} → ${com.r0ybt.arachn0de.gantt.ganttDate(it.last)}",style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)}
                if(controls) Column(Modifier.heightIn(max=220.dp).verticalScroll(rememberScrollState())) {
                    GanttChoice("Escala",scaleName,com.r0ybt.arachn0de.gantt.GanttScale.entries.map {it.name to it.label}) {scaleName=it}
                    GanttChoice("Período",scopeName,com.r0ybt.arachn0de.gantt.GanttScope.entries.map {it.name to it.label}) {scopeName=it;sliding=false}
                    GanttChoice("Sprint",sprint.orEmpty(),available.map {it.id to it.title}) {sprint=it}
                    GanttChoice("Responsable",person.orEmpty(),listOf("" to "Todos")+state.people.people.map {it.id to it.name}) {person=it.ifEmpty {null}}
                    if(annotations.isNotEmpty()) {
                        Text("Anotaciones guardadas",style=MaterialTheme.typography.labelSmall)
                        LazyRow {items(annotations,key={it.id}) {note->TextButton(enabled=!busy,onClick={draft=note}) {Text(com.r0ybt.arachn0de.gantt.ganttDate(java.time.LocalDate.ofEpochDay(note.epochDay))+" · "+note.text.take(25),style=MaterialTheme.typography.labelSmall)}}}
                    }
                }
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
                    val period=when(selection.scale) {com.r0ybt.arachn0de.gantt.GanttScale.DAY->"día";com.r0ybt.arachn0de.gantt.GanttScale.WEEK->"semana";else->"mes"}
                    fun shift(forward:Boolean) {
                        sliding=true
                        val delta=if(forward) 1L else -1L
                        anchor=when(selection.scale) {com.r0ybt.arachn0de.gantt.GanttScale.DAY->selection.anchor.plusDays(delta);com.r0ybt.arachn0de.gantt.GanttScale.WEEK->selection.anchor.plusWeeks(delta);else->selection.anchor.plusMonths(delta)}.toString()
                        scopeName=if(selection.scale==com.r0ybt.arachn0de.gantt.GanttScale.MONTH) "MONTH" else "WEEK"
                    }
                    TextButton(onClick={shift(false)},modifier=Modifier.weight(1f)) {Text("Anterior · $period",style=MaterialTheme.typography.labelSmall)}
                    TextButton(modifier=Modifier.weight(1f),onClick={anchor=java.time.LocalDate.now().toString();sliding=false;scopeName=if(selection.scale==com.r0ybt.arachn0de.gantt.GanttScale.MONTH) "MONTH" else "WEEK"}) {Text("Hoy")}
                    TextButton(onClick={shift(true)},modifier=Modifier.weight(1f)) {Text("Siguiente · $period",style=MaterialTheme.typography.labelSmall)}
                }
                if(doc!=null) GanttTimeline(doc,Modifier.weight(1f),onOpenNode,onCreate={day,lane->if(!busy) draft=com.r0ybt.arachn0de.data.local.GanttAnnotationEntity(java.util.UUID.randomUUID().toString(),context,"",day,lane)},onAnnotation={if(!busy) draft=it},onMove={note->perform {repository.gantt.save(context,note.text,note.epochDay,note.lane,note.id)}})
                else Text(result.exceptionOrNull()?.message ?: "Cargando Gantt…")
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
                    TextButton(enabled=!busy && pages.isNotEmpty(),onClick={if(pages.size==1) export.launch("ARACHN0DE-Gantt.png") else exportAll.launch(null)}) {Text("PNG")}
                    TextButton(enabled=!busy,onClick={draft=com.r0ybt.arachn0de.data.local.GanttAnnotationEntity(java.util.UUID.randomUUID().toString(),context,"",(doc?.let {selection.anchor.coerceIn(it.first,it.last)} ?: selection.anchor).toEpochDay(),.5)}) {Text("Anotar")}
                }
                error?.let {Text(it,style=MaterialTheme.typography.bodySmall)}
                pageResult.exceptionOrNull()?.let {Text(it.message.orEmpty(),style=MaterialTheme.typography.bodySmall)}
                draft?.let {note->
                    var content by remember(note.id) {mutableStateOf(note.text)}
                    var day by remember(note.id) {mutableLongStateOf(note.epochDay)}
                    val editorHeight=minOf(320f*androidx.compose.ui.platform.LocalDensity.current.fontScale,androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp*.7f)
                    Dialog(onDismissRequest={if(!busy) draft=null},properties=DialogProperties(usePlatformDefaultWidth=false)) {
                        Box(Modifier.fillMaxSize().safeDrawingPadding().padding(12.dp),contentAlignment=androidx.compose.ui.Alignment.Center) {
                        Surface(Modifier.widthIn(max=560.dp).fillMaxWidth().height(editorHeight.dp)) {
                            Column(Modifier.padding(12.dp)) {
                                Text("Anotación",style=MaterialTheme.typography.titleSmall)
                                Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())) {
                                    TextButton(onClick={val date=java.time.LocalDate.ofEpochDay(day);android.app.DatePickerDialog(local,{_,y,m,d->day=java.time.LocalDate.of(y,m+1,d).toEpochDay()},date.year,date.monthValue-1,date.dayOfMonth).show()}) {Text(com.r0ybt.arachn0de.gantt.ganttDate(java.time.LocalDate.ofEpochDay(day)))}
                                    OutlinedTextField(content,{content=it},label={Text("Texto")},enabled=!busy,maxLines=5,modifier=Modifier.fillMaxWidth().height(120.dp))
                                    Text("Arrastra el marcador para moverlo.",style=MaterialTheme.typography.labelSmall)
                                    if(annotations.any {it.id==note.id}) TextButton(enabled=!busy,onClick={perform {repository.gantt.delete(context,note.id);draft=null}}) {Text("Eliminar")}
                                }
                                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.End) {
                                    TextButton(enabled=!busy,onClick={draft=null}) {Text("Cancelar")}
                                    TextButton(enabled=!busy && content.isNotBlank(),onClick={perform {repository.gantt.save(context,content,day,note.lane,note.id);draft=null}}) {Text("Guardar")}
                                }
                            }
                        }
                        }
                    }

                }
            }}
        }
    }
}
@Composable private fun GanttChoice(label:String,value:String,choices:List<Pair<String,String>>,onSelect:(String)->Unit) {
    var expanded by remember {mutableStateOf(false)}
    Box {
        TextButton(onClick={expanded=true},modifier=Modifier.fillMaxWidth()) {Text("$label: ${choices.firstOrNull {it.first==value}?.second ?: "Seleccionar"}",Modifier.weight(1f))}
        DropdownMenu(expanded,{expanded=false}) {choices.forEach {(key,title)->DropdownMenuItem(text={Text(title)},onClick={expanded=false;onSelect(key)})}}
    }
}
