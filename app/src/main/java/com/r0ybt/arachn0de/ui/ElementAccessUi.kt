package com.r0ybt.arachn0de.ui

import androidx.compose.foundation.background
import com.r0ybt.arachn0de.ui.theme.*
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
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

internal data class FavoriteControl(val selected:Boolean,val enabled:Boolean,val toggle:()->Unit)
internal val FavoriteActionLabels=setOf("Marcar favorito","Quitar de Favoritos")

@Composable internal fun rememberFavoriteControl(key:String,enabled:Boolean=true):FavoriteControl? {
    val repo=LocalElementAccess.current ?: return null
    val confirmed=key in LocalElementAccessState.current.favorites
    val context=androidx.compose.ui.platform.LocalContext.current.applicationContext
    val session=(context as? com.r0ybt.arachn0de.Arachn0deApplication)?.security?.current?.value
    val screenScope=rememberCoroutineScope()
    val scope=session?.scope ?: screenScope
    val feedback=remember(repo,key,scope) {com.r0ybt.arachn0de.ui.state.FavoriteFeedback(scope)}
    val op=feedback.operation
    val selected=feedback.shown(confirmed)
    LaunchedEffect(confirmed,op.busy) {feedback.acknowledge(confirmed)}
    var attached by remember {mutableStateOf(true)}
    DisposableEffect(feedback) {attached=true;onDispose {attached=false}}
    val toggle:()->Unit={
        val started=com.r0ybt.arachn0de.ui.ResponseTiming.start("favorite_press")
        feedback.toggle(confirmed,{desired->
        repo.toggle(key,desired)
        com.r0ybt.arachn0de.ui.ResponseTiming.end("favorite_commit",started)
    },{if(!attached) withContext(Dispatchers.Main) {android.widget.Toast.makeText(context,"No se pudo cambiar Favoritos.",android.widget.Toast.LENGTH_LONG).show()}})}
    OperationErrorDialog(op)
    return FavoriteControl(selected,enabled && !op.busy,toggle)
}

@Composable internal fun rememberFavoriteMenuAction(key:String,enabled:Boolean=true,exterior:Boolean=false):ContextAction? {
    val control=rememberFavoriteControl(key,enabled) ?: return null
    return ContextAction(if(exterior) {if(control.selected) "Quitar de favoritos" else "Marcar como favorito"}
        else if(control.selected) "Quitar de Favoritos" else "Marcar favorito",
        if(control.selected) Icons.Default.Star else Icons.Default.StarBorder,control.toggle,enabled=control.enabled)
}

@Composable internal fun FavoriteCardIndicator(key:String) {
    if(key in LocalElementAccessState.current.favorites) LucideIcon(Icons.Default.Star,"Favorito",
        Modifier.size(16.dp).testTag("card-signal:$key:favorite"),tint=Arachn0deColors.TextPrimary)
}

@Composable internal fun FavoriteToggle(key:String,compactSquare:Boolean=false,enabled:Boolean=true) {
    val control=rememberFavoriteControl(key,enabled) ?: return
    val selected=control.selected
    if(compactSquare) CompactCardIconButton(if(selected) Icons.Default.Star else Icons.Default.StarBorder,
        if(selected) "Quitar de Favoritos" else "Marcar favorito",control.toggle,
        Modifier.testTag("favorite:$key"),enabled=control.enabled,selected=selected,tint=if(selected) Arachn0deColors.TextPrimary else com.r0ybt.arachn0de.ui.theme.BlackWhite90.Inactive)
    else IconButton(enabled=control.enabled,onClick=control.toggle,modifier=Modifier.size(48.dp).testTag("favorite:$key").background(androidx.compose.ui.graphics.Color.Transparent,androidx.compose.foundation.shape.CircleShape)) {
        LucideIcon(if(selected) Icons.Default.Star else Icons.Default.StarBorder,if(selected) "Quitar de Favoritos" else "Marcar favorito",tint=if(selected) Arachn0deColors.TextPrimary else com.r0ybt.arachn0de.ui.theme.BlackWhite90.Inactive)
    }
}

@Composable internal fun QuickCaptureDialog(index:List<ElementResult>?,repo:AccessRepository,onDismiss:()->Unit) {
    if(index==null) NavigationIndexLoading(onDismiss) else QuickCaptureDialog(index,repo,onDismiss,null,null)
}

@Composable internal fun QuickCaptureDialog(index:List<ElementResult>,repo:AccessRepository,onDismiss:()->Unit,pending:com.r0ybt.arachn0de.sharing.SharedCaptureQueue.Entry?,queue:com.r0ybt.arachn0de.sharing.SharedCaptureQueue?) {
    val state=LocalElementAccessState.current
    val linkNote=pending?.linkNote==true
    var purpose by rememberSaveable {mutableStateOf(if(linkNote) com.r0ybt.arachn0de.domain.model.NodePurpose.NOTE else com.r0ybt.arachn0de.domain.model.NodePurpose.ACTION)}
    val targets=index.filter {it.destination}
    var key by rememberSaveable {mutableStateOf(pending?.destination)}
    val draftState=rememberSaveable(pending?.id,stateSaver=com.r0ybt.arachn0de.ui.state.EditorDraft.Saver) {mutableStateOf<com.r0ybt.arachn0de.ui.state.EditorDraft?>(
        com.r0ybt.arachn0de.ui.state.EditorDraft(null,null,pending?.title.orEmpty(),pending?.description.orEmpty(),creationId=pending?.id ?: UUID.randomUUID().toString(),attachmentDraftId=pending?.id ?: UUID.randomUUID().toString()).apply {removedAttachmentIds=pending?.removed.orEmpty()} )}
    val draft=checkNotNull(draftState.value)
    var title by draft::title
    var description by draft::description
    var descriptionOpen by rememberSaveable {mutableStateOf(pending!=null)}
    var choosing by rememberSaveable {mutableStateOf(false)}
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
    val titleFeedback=rememberTitleInput()
    val destination=targets.firstOrNull {it.key==key}
    AccessDialog(onDismissRequest={if(!op.busy) cancel()},title={Text("Captura rápida")},text={Column(Modifier.verticalScroll(androidx.compose.foundation.rememberScrollState())) {
        if(pending==null) FormChoices(listOf(com.r0ybt.arachn0de.domain.model.NodePurpose.ACTION to "Tarea",com.r0ybt.arachn0de.domain.model.NodePurpose.NOTE to "Nota"),purpose,!op.busy) {purpose=it}
        OutlinedTextField(title,{titleFeedback.change(title,it,TitleLimits.NODE) {title=it}},supportingText={TitleLimitHint(title,TitleLimits.NODE,titleFeedback.blocked)},enabled=!op.busy,label={Text("Título")},modifier=Modifier.fillMaxWidth(),singleLine=false,maxLines=Int.MAX_VALUE,isError=TitleLimits.count(title)>TitleLimits.NODE)
        TextButton(enabled=!op.busy,onClick={descriptionOpen=!descriptionOpen}) {Text(if(linkNote) "Texto y enlaces" else "Descripción opcional")}
        if(descriptionOpen) {
            if(linkNote) OutlinedTextField(description,{description=it},enabled=!op.busy,label={Text("Texto y enlaces")},modifier=Modifier.fillMaxWidth())
            else if(pending!=null) AttachmentDescriptionEditor(draft,!op.busy,tools={},repository=requireNotNull(queue).attachments)
            else OutlinedTextField(description,{description=it},enabled=!op.busy,label={Text("Descripción")},modifier=Modifier.fillMaxWidth())
        }
        OutlinedButton(enabled=!op.busy,onClick={choosing=true}) {Text("Destino: ${destination?.let {it.path+if(it.nodeId!=null) " › ${it.title}" else ""} ?: "Borradores"}")}

    }},confirmButton={TextButton(enabled=!op.busy && title.isNotBlank() && TitleLimits.count(title)<=TitleLimits.NODE && (key==null || destination!=null),onClick={
        op.submit(if(linkNote) "No se pudo crear la nota. Revisa el título, el enlace y el destino." else "No se pudo crear la tarea. Revisa el título y el destino.",{if(pending==null) repo.capture(key,title,description,creationId,purpose) else requireNotNull(queue).save(pending.id,key,title,description,draft.removedAttachmentIds.toSet());true},onDismiss)
    }) {Text(if(key==null) "Guardar en Borradores" else if(linkNote) "Crear nota" else "Crear tarea")}},dismissButton={TextButton(enabled=!op.busy,onClick=::cancel) {Text("Cancelar")}})
    if(choosing) {
        var query by rememberSaveable {mutableStateOf("")}
        AccessDialog(onDismissRequest={choosing=false},title={Text(if(linkNote) "Destino de la nota" else "Destino de la tarea")},text={Column {
            TextButton(onClick={key=null;choosing=false}) {Text("Guardar en Borradores")}
            OutlinedTextField(query,{query=it},label={Text("Buscar destino")},singleLine=true)
            LazyColumn(Modifier.heightIn(max=360.dp)) {items(ElementSearch.find(targets,query),key={it.key}) {target->TextButton(onClick={key=target.key;choosing=false}) {Text("${target.type} · ${target.path}${if(target.nodeId!=null) " › ${target.title}" else ""}")}}}
        }},confirmButton={TextButton(onClick={choosing=false}) {Text("Cerrar")}})
    }
    OperationErrorDialog(op)
}

@Composable
internal fun AccessDialog(onDismissRequest:()->Unit,title:@Composable ()->Unit,text:@Composable ()->Unit,confirmButton:@Composable ()->Unit,dismissButton:(@Composable ()->Unit)?=null) {
    BottomModulesDialog(onDismissRequest=onDismissRequest,properties=androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth=false)) {
        Surface(modifier=Modifier.fillMaxWidth(),shape=androidx.compose.foundation.shape.RoundedCornerShape(16.dp),color=MaterialTheme.colorScheme.surface) {
            Column(Modifier.fillMaxWidth().heightIn(max=620.dp).padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                androidx.compose.runtime.CompositionLocalProvider(LocalTextStyle provides MaterialTheme.typography.titleLarge) {title()}
                Column(Modifier.weight(1f,fill=false)) {text()}
                FlowRow(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.End) {dismissButton?.invoke();confirmButton()}
            }
        }
    }
}
