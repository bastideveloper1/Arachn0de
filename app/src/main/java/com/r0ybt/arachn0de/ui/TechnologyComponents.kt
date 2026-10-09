package com.r0ybt.arachn0de.ui

import android.graphics.Bitmap
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.clickable
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Close
import androidx.compose.foundation.Image
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Code
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.r0ybt.arachn0de.Arachn0deApplication
import com.r0ybt.arachn0de.data.local.TechnologyEntity
import com.r0ybt.arachn0de.data.local.TechnologyIconStore
import com.r0ybt.arachn0de.ui.state.TechnologyActions
import com.r0ybt.arachn0de.ui.theme.Arachn0deColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
internal fun TechnologyIcon(technology: TechnologyEntity) {
    val context = LocalContext.current
    val storageContext=privateStorageContext(context)
    val bitmap by produceState<Bitmap?>(null, technology.iconFile) {
        value = withContext(Dispatchers.IO) { technology.iconFile?.let { runCatching { TechnologyIconStore(storageContext).readThumbnail(it) }.getOrNull() } }
    }
    Box(Modifier.size(28.dp).clip(RoundedCornerShape(7.dp)).background(Arachn0deColors.ControlSurface)
        .semantics { contentDescription = technology.name }, contentAlignment = Alignment.Center) {
        if (bitmap != null) Image(bitmap!!.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
        else Text(technology.name.take(1).uppercase(), style = MaterialTheme.typography.labelSmall)
    }
}

/** Consultation never mutates assignments. Editing is an explicit separate action. */
@Composable
internal fun TechnologyOwnerControl(ownerId: String, project: Boolean, ownerName: String,
    repositoryOverride: com.r0ybt.arachn0de.data.repository.TechnologyRepository? = null) {
    val repository=repositoryOverride ?: (LocalContext.current.applicationContext as? Arachn0deApplication)?.technologyRepository ?: return
    val state by repository.state.collectAsState()
    val assigned=state.forOwner(ownerId,project)
    TechnologyLabels(assigned,compact=true)
}

@Composable
internal fun TechnologyLabels(assigned:List<TechnologyEntity>,compact:Boolean=false) {
    if(assigned.isEmpty()) return
    var info by remember { mutableStateOf<TechnologyEntity?>(null) }
    FlowRow(horizontalArrangement=Arrangement.spacedBy(4.dp),verticalArrangement=Arrangement.spacedBy(2.dp)) {
        assigned.take(if(compact) 3 else assigned.size).forEach { t ->
            TextButton(onClick={info=t},contentPadding=PaddingValues(horizontal=4.dp),modifier=Modifier.heightIn(min=48.dp)) {
                TechnologyIcon(t)
                if(!compact) { Spacer(Modifier.width(4.dp));Text(t.name,Modifier.widthIn(max=180.dp)) }
            }
        }
        if(compact && assigned.size>3) TextButton(onClick={info=assigned.first()}) { Text("+${assigned.size-3}") }
    }
    info?.let { t->AlertDialog(onDismissRequest={info=null},title={Text(t.name)},text={Column { TechnologyIcon(t);Text(assigned.joinToString { it.name }) }},confirmButton={TextButton(onClick={info=null}) {Text("Cerrar")}}) }
}

@Composable
internal fun TechnologyAssignmentDialog(ownerId:String,project:Boolean,catalog:List<TechnologyEntity>,assigned:List<TechnologyEntity>,busy:Boolean,onDismiss:()->Unit,onSave:(Set<String>)->Unit) {
    var selected by rememberSaveable(ownerId,project,stateSaver=listSaver<List<String>,String>(save={it},restore={it})) {mutableStateOf(assigned.map {it.id})}
    AlertDialog(onDismissRequest={if(!busy) onDismiss()},title={Text("Editar tecnologías")},text={
        Column(Modifier.heightIn(max=420.dp).verticalScroll(androidx.compose.foundation.rememberScrollState())) {
            TechnologySelection(catalog,selected,!busy) {selected=it}
        }
    },confirmButton={TextButton(enabled=!busy,onClick={onSave(selected.toSet())}) {Text("Guardar")}},dismissButton={TextButton(enabled=!busy,onClick=onDismiss) {Text("Cancelar")}})
}

@Composable
internal fun TechnologySelection(catalog:List<TechnologyEntity>,selected:List<String>,enabled:Boolean,onChange:(List<String>)->Unit) {
    var info by remember {mutableStateOf<TechnologyEntity?>(null)}
    val centers=remember {mutableStateMapOf<String,Float>()}
    Column(Modifier.fillMaxWidth()) {
    Text("Tecnologías · la primera es principal",style=MaterialTheme.typography.labelMedium)
    Text("Mantén pulsado ≡ y arrastra. Quitar usa ×; el nombre abre información.",style=MaterialTheme.typography.bodySmall)
    val missing=selected.filter {id->catalog.none {it.id==id}}
    if(missing.isNotEmpty()) {
        Text("Hay ${missing.size} tecnologías que ya no están disponibles.",color=MaterialTheme.colorScheme.error)
        TextButton(enabled=enabled,onClick={onChange(selected-missing.toSet())}) {Text("Quitar referencias no disponibles")}
    }
    val latestSelected by rememberUpdatedState(selected)
    val latestChange by rememberUpdatedState(onChange)
    selected.mapNotNull {id->catalog.find {it.id==id}}.forEach {t-> key(t.id) {
        Row(Modifier.fillMaxWidth().onGloballyPositioned {coordinates->centers[t.id]=coordinates.positionInRoot().y+coordinates.size.height/2f},verticalAlignment=Alignment.CenterVertically) {
            Icon(Icons.Default.DragHandle,"Arrastrar ${t.name}",Modifier.size(48.dp).pointerInput(t.id,enabled) {
                var dy=0f
                var initialCenter=0f
                detectDragGesturesAfterLongPress(onDragStart={dy=0f;initialCenter=centers[t.id] ?: 0f},onDragEnd={dy=0f},onDragCancel={dy=0f}) {change,delta->
                    if(enabled) {
                        change.consume();dy+=delta.y
                        val list=latestSelected.toMutableList()
                        val target=list.filter {it in centers}.minByOrNull {kotlin.math.abs(requireNotNull(centers[it])-(initialCenter+dy))}
                        val from=list.indexOf(t.id);val to=list.indexOf(target)
                        if(from>=0 && to>=0 && from!=to) {list.add(to,list.removeAt(from));latestChange(list)}
                    }
                }
            })
            TextButton(onClick={info=t},modifier=Modifier.weight(1f)) {TechnologyIcon(t);Spacer(Modifier.width(6.dp));Text(t.name)}
            IconButton(enabled=enabled,onClick={onChange(selected-t.id)}) {Icon(Icons.Default.Close,"Quitar ${t.name}")}
        }
    } }
    FlowRow(horizontalArrangement=Arrangement.spacedBy(4.dp)) {
        catalog.filter {it.id !in selected}.forEach {t->InputChip(selected=false,enabled=enabled,onClick={onChange(selected+t.id)},label={Text("Añadir ${t.name}")},avatar={TechnologyIcon(t)})}
    }
    info?.let {t->AlertDialog(onDismissRequest={info=null},title={Text(t.name)},text={TechnologyIcon(t)},confirmButton={TextButton(onClick={info=null}) {Text("Cerrar")}})}
    }
}

@Composable
internal fun EditOwnerTechnologies(ownerId:String,project:Boolean,onDismiss:()->Unit) {
    val app=LocalContext.current.applicationContext as? Arachn0deApplication ?: return
    val repo=app.technologyRepository;val state by repo.state.collectAsState();val scope=rememberCoroutineScope();val actions=remember(repo,scope){TechnologyActions(repo,scope)}
    if(state.loaded) TechnologyAssignmentDialog(ownerId,project,state.catalog,state.forOwner(ownerId,project),actions.operation.busy,onDismiss) {ids->actions.assign(ownerId,project,ids,onDismiss)}
    else AlertDialog(onDismissRequest=onDismiss,title={Text("Cargando tecnologías")},confirmButton={TextButton(onClick=onDismiss){Text("Cerrar")}})
    OperationErrorDialog(actions.operation)
}

@Composable
internal fun DraftTechnologies(draft:com.r0ybt.arachn0de.ui.state.EditorDraft,enabled:Boolean) {
    val app=LocalContext.current.applicationContext as? Arachn0deApplication ?: return
    val state by app.technologyRepository.state.collectAsState()
    LaunchedEffect(state.loaded,draft.creationId) {if(state.loaded && !draft.technologiesLoaded) {draft.technologyIds=draft.id?.let {state.forOwner(it,false).map {t->t.id}}.orEmpty();draft.originalTechnologyIds=draft.technologyIds;draft.technologiesLoaded=true}}
    if(state.loaded) TechnologySelection(state.catalog,draft.technologyIds,enabled) {draft.technologyIds=it}
}

@Composable
internal fun CompactAssignments(ownerId:String,people:List<com.r0ybt.arachn0de.domain.model.Person>,modifier:Modifier=Modifier) {
    val app=LocalContext.current.applicationContext as? Arachn0deApplication
    val state by (app?.technologyRepository?.state ?: kotlinx.coroutines.flow.MutableStateFlow(com.r0ybt.arachn0de.data.repository.TechnologyState())).collectAsState()
    val technologies=state.forOwner(ownerId,false)
    if(technologies.isEmpty() && people.isEmpty()) return
    FlowRow(modifier,horizontalArrangement=Arrangement.spacedBy(8.dp),itemVerticalAlignment=Alignment.CenterVertically) {
        TechnologyLabels(technologies,true)
        ResponsibleAvatars(people)
    }
}
