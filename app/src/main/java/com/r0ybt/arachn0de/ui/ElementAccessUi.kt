package com.r0ybt.arachn0de.ui

import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.r0ybt.arachn0de.access.*
import com.r0ybt.arachn0de.domain.model.TitleLimits
import com.r0ybt.arachn0de.ui.state.OperationState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID

internal val LocalElementAccess=staticCompositionLocalOf<AccessRepository?> {null}
internal val LocalElementAccessState=staticCompositionLocalOf {AccessState()}

@Composable internal fun FavoriteToggle(key:String,compactSquare:Boolean=false) {
    val repo=LocalElementAccess.current ?: return
    val selected=key in LocalElementAccessState.current.favorites
    val scope=rememberCoroutineScope();val op=remember(repo,scope) {OperationState(scope)}
    if(compactSquare) CompactCardIconButton(if(selected) Icons.Default.Star else Icons.Default.StarBorder,
        if(selected) "Quitar de Favoritos" else "Marcar favorito",{op.submit("No se pudo cambiar Favoritos.",{repo.toggle(key);true},{})},
        Modifier.testTag("favorite:$key"),enabled=!op.busy,selected=selected,tint=if(selected) com.r0ybt.arachn0de.ui.theme.Arachn0deColors.TextPrimary else com.r0ybt.arachn0de.ui.theme.BlackWhite90.Inactive)
    else {
    IconButton(enabled=!op.busy,onClick={op.submit("No se pudo cambiar Favoritos.",{repo.toggle(key);true},{})},modifier=Modifier.testTag("favorite:$key")) {
        LucideIcon(if(selected) Icons.Default.Star else Icons.Default.StarBorder,if(selected) "Quitar de Favoritos" else "Marcar favorito",tint=if(selected) com.r0ybt.arachn0de.ui.theme.Arachn0deColors.TextPrimary else com.r0ybt.arachn0de.ui.theme.BlackWhite90.Inactive)
    }
    }
    OperationErrorDialog(op)
}

@Composable internal fun ElementSearchScreen(index:List<ElementResult>,mode:String,query:String,onQuery:(String)->Unit,onMode:(String)->Unit,onBack:()->Unit,onOpen:(ElementResult)->Unit,listState:androidx.compose.foundation.lazy.LazyListState,ready:Boolean=true) {
    val state=LocalElementAccessState.current
    val results by produceState<List<ElementResult>?>(null,index,query,mode,state,ready) {
        if(!ready) {value=null;return@produceState}
        value=withContext(Dispatchers.Default) {
            val filtered=ElementSearch.find(index,query)
            when(mode) {
                "Favoritos"->filtered.filter {it.key in state.favorites}
                "Recientes"->state.recent.mapNotNull {key->filtered.find {it.key==key}}
                "Frecuentes"->filtered.filter {it.key in state.counts}.sortedByDescending {state.counts[it.key]}
                else->filtered
            }
        }
    }
    androidx.activity.compose.BackHandler(onBack=onBack)
    Surface(Modifier.fillMaxSize(), color = if (mode == "Favoritos") com.r0ybt.arachn0de.ui.theme.Arachn0deColors.Background else MaterialTheme.colorScheme.surface) {
        Column(Modifier.fillMaxSize().padding(14.dp).testTag("search-screen")) {
            Row(Modifier.fillMaxWidth(),verticalAlignment=androidx.compose.ui.Alignment.CenterVertically) {
                GlobalChromeActions(leading=true)
                IconButton(onClick=onBack,modifier=Modifier.size(48.dp)) {LucideIcon(androidx.compose.material.icons.Icons.Default.ArrowBack,"Volver")}
                Text(if(mode=="Favoritos") "Favoritos" else "Buscar",Modifier.weight(1f),style=MaterialTheme.typography.titleLarge)
                GlobalChromeActions()
            }
            // Reserve the result viewport even while loading; controls remain above the IME
            // because the owning AppSafeArea already consumes safeDrawing (including keyboard).
            Box(Modifier.weight(1f).fillMaxWidth()) {
            if(!ready || results==null) Text("Cargando…")
            else if(results.orEmpty().isEmpty()) Text(if(mode=="Favoritos" && state.favorites.isEmpty()) "No hay favoritos" else "Sin resultados")
            else LazyColumn(Modifier.fillMaxSize().testTag("search-results"),state=listState,
                verticalArrangement=Arrangement.spacedBy(if(mode=="Favoritos") 8.dp else 0.dp)) {items(results.orEmpty(),key={it.key}) {result->
                val content: @Composable () -> Unit = {
                Row(Modifier.fillMaxWidth().heightIn(min=56.dp).testTag("access-result:${result.key}").clickable(onClickLabel="Abrir ${result.type}") {onOpen(result)}
                    .then(if(mode=="Favoritos") Modifier.padding(horizontal=12.dp) else Modifier),verticalAlignment=androidx.compose.ui.Alignment.CenterVertically) {
                    Column(Modifier.weight(1f).padding(vertical=8.dp)) {
                        Text(result.title,style=MaterialTheme.typography.titleSmall,maxLines=2,overflow=androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                        Text("${result.type} · ${result.path}",style=MaterialTheme.typography.bodySmall,maxLines=2,overflow=androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                    }
                    FavoriteToggle(result.key)
                }
                }
                if(mode=="Favoritos") Card(Modifier.fillMaxWidth(),
                    colors=CardDefaults.cardColors(containerColor=com.r0ybt.arachn0de.ui.theme.Arachn0deColors.Surface)) {content()}
                else content()
            }}
            }
            Column(Modifier.fillMaxWidth().padding(top=8.dp).testTag("search-controls"),verticalArrangement=Arrangement.spacedBy(6.dp)) {
                OutlinedTextField(query,onQuery,label={Text(if(mode=="Favoritos") "Buscar en favoritos" else "Título o descripción")},modifier=Modifier.fillMaxWidth(),singleLine=true)
                if(mode!="Favoritos") BoxWithConstraints(Modifier.fillMaxWidth()) {
                    val fontScale=androidx.compose.ui.platform.LocalDensity.current.fontScale
                    val columns=if(maxWidth >= 440.dp * fontScale) 4 else 2
                    Column(verticalArrangement=Arrangement.spacedBy(4.dp)) {
                        listOf("Buscar","Recientes","Frecuentes").chunked(columns).forEach {group ->
                            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                                group.forEach {label ->
                                    FilterChip(mode==label,{onMode(label)},label={Text(label,style=MaterialTheme.typography.labelSmall)},
                                        modifier=Modifier.weight(1f).heightIn(min=48.dp).testTag("search-mode:$label"))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable internal fun QuickCaptureDialog(index:List<ElementResult>,repo:AccessRepository,onDismiss:()->Unit) = QuickCaptureDialog(index,repo,onDismiss,null,null)

@Composable internal fun QuickCaptureDialog(index:List<ElementResult>,repo:AccessRepository,onDismiss:()->Unit,pending:com.r0ybt.arachn0de.sharing.SharedCaptureQueue.Entry?,queue:com.r0ybt.arachn0de.sharing.SharedCaptureQueue?) {
    val state=LocalElementAccessState.current
    val linkNote=pending?.linkNote==true
    val targets=index.filter {it.destination}
    var key by rememberSaveable {mutableStateOf(pending?.destination ?: state.destination?.takeIf {id->targets.any {it.key==id}} ?: targets.firstOrNull()?.key)}
    val draftState=rememberSaveable(pending?.id,stateSaver=com.r0ybt.arachn0de.ui.state.EditorDraft.Saver) {mutableStateOf<com.r0ybt.arachn0de.ui.state.EditorDraft?>(
        com.r0ybt.arachn0de.ui.state.EditorDraft(null,null,pending?.title.orEmpty(),pending?.description.orEmpty(),creationId=pending?.id ?: UUID.randomUUID().toString(),attachmentDraftId=pending?.id ?: UUID.randomUUID().toString()).apply {removedAttachmentIds=pending?.removed.orEmpty()} )}
    val draft=checkNotNull(draftState.value)
    var title by draft::title
    var description by draft::description
    var descriptionOpen by rememberSaveable {mutableStateOf(pending!=null)}
    var choosing by rememberSaveable {mutableStateOf(false)}
    LaunchedEffect(targets.map {it.key}) {if(key==null) key=targets.firstOrNull()?.key}
    val creationId=draft.creationId
    val scope=rememberCoroutineScope();val op=remember(repo,scope) {OperationState(scope)}
    val gate=LocalCaptureGate.current
    DisposableEffect(creationId) {gate.active=creationId;onDispose {if(gate.active==creationId) gate.active=null}}
    LaunchedEffect(pending?.id) {
        if(pending!=null) {
            pending.destination?.takeIf {dest->targets.any {it.key==dest}}?.let {key=it}
            snapshotFlow {pending.copy(title=title,description=description,destination=key,removed=draft.removedAttachmentIds)}.collect {value->
                try {queue?.update(value.id,value.title,value.description,value.destination,value.removed)}
                catch(c:kotlinx.coroutines.CancellationException) {throw c}
                catch(_:Exception) {draft.attachmentError="No se pudieron conservar los últimos cambios de la captura."}
            }
        }
    }
    fun cancel() {
        if(pending==null) onDismiss()
        else op.submit("No se pudo descartar la captura. Se conserva para reintentar.",{requireNotNull(queue).discard(pending.id);true},onDismiss)
    }
    val destination=targets.firstOrNull {it.key==key}
    AccessDialog(onDismissRequest={if(!op.busy) cancel()},title={Text("Captura rápida")},text={Column(Modifier.verticalScroll(androidx.compose.foundation.rememberScrollState())) {
        OutlinedTextField(title,{title=it},enabled=!op.busy,label={Text("Título")},modifier=Modifier.fillMaxWidth(),singleLine=true,isError=TitleLimits.count(title)>TitleLimits.NODE)
        TextButton(enabled=!op.busy,onClick={descriptionOpen=!descriptionOpen}) {Text(if(linkNote) "Enlace" else "Descripción opcional")}
        if(descriptionOpen) {
            if(linkNote) OutlinedTextField(description,{description=it},enabled=!op.busy,label={Text("Enlace")},modifier=Modifier.fillMaxWidth())
            else if(pending!=null) AttachmentDescriptionEditor(draft,!op.busy,tools={},repository=requireNotNull(queue).attachments)
            else OutlinedTextField(description,{description=it},enabled=!op.busy,label={Text("Descripción")},modifier=Modifier.fillMaxWidth())
        }
        OutlinedButton(enabled=!op.busy,onClick={choosing=true}) {Text("Destino: ${destination?.let {it.path+if(it.nodeId!=null) " › ${it.title}" else ""} ?: "Seleccionar"}")}
        if(targets.isEmpty()) Text(if(linkNote) "Crea primero un Proyecto para guardar notas." else "Crea primero un Proyecto para guardar tareas.")
    }},confirmButton={TextButton(enabled=!op.busy && title.isNotBlank() && TitleLimits.count(title)<=TitleLimits.NODE && destination!=null && (!linkNote || com.r0ybt.arachn0de.sharing.SharedContent.isLink(description.trim())),onClick={
        op.submit(if(linkNote) "No se pudo crear la nota. Revisa el título, el enlace y el destino." else "No se pudo crear la tarea. Revisa el título y el destino.",{if(pending==null) repo.capture(requireNotNull(key),title,description,creationId) else requireNotNull(queue).save(pending.id,requireNotNull(key),title,description,draft.removedAttachmentIds.toSet());true},onDismiss)
    }) {Text(if(linkNote) "Crear nota" else "Crear tarea")}},dismissButton={TextButton(enabled=!op.busy,onClick=::cancel) {Text("Cancelar")}})
    if(choosing) {
        var query by rememberSaveable {mutableStateOf("")}
        AccessDialog(onDismissRequest={choosing=false},title={Text(if(linkNote) "Destino de la nota" else "Destino de la tarea")},text={Column {
            OutlinedTextField(query,{query=it},label={Text("Buscar destino")},singleLine=true)
            LazyColumn(Modifier.heightIn(max=360.dp)) {items(ElementSearch.find(targets,query),key={it.key}) {target->TextButton(onClick={key=target.key;choosing=false}) {Text("${target.type} · ${target.path}${if(target.nodeId!=null) " › ${target.title}" else ""}")}}}
        }},confirmButton={TextButton(onClick={choosing=false}) {Text("Cerrar")}})
    }
    OperationErrorDialog(op)
}

@Composable
private fun AccessDialog(onDismissRequest:()->Unit,title:@Composable ()->Unit,text:@Composable ()->Unit,confirmButton:@Composable ()->Unit,dismissButton:(@Composable ()->Unit)?=null) {
    androidx.compose.ui.window.Dialog(onDismissRequest=onDismissRequest,properties=androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth=false)) {
        Surface(modifier=Modifier.fillMaxWidth().padding(16.dp),shape=androidx.compose.foundation.shape.RoundedCornerShape(16.dp),color=MaterialTheme.colorScheme.surface) {
            Column(Modifier.fillMaxWidth().heightIn(max=620.dp).padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                androidx.compose.runtime.CompositionLocalProvider(LocalTextStyle provides MaterialTheme.typography.titleLarge) {title()}
                Column(Modifier.weight(1f,fill=false)) {text()}
                FlowRow(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.End) {dismissButton?.invoke();confirmButton()}
            }
        }
    }
}
