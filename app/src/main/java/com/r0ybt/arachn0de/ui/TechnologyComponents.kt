package com.r0ybt.arachn0de.ui

import android.graphics.Bitmap
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Close
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
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
    val bitmap by produceState<Bitmap?>(null, storageContext, technology.iconFile) {
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
internal fun TechnologyLabels(assigned:List<TechnologyEntity>,compact:Boolean=false,modifier:Modifier=Modifier) {
    if(assigned.isEmpty()) return
    var info by remember { mutableStateOf<TechnologyEntity?>(null) }
    var all by rememberSaveable { mutableStateOf(false) }
    FlowRow(modifier,horizontalArrangement=Arrangement.spacedBy(2.dp),verticalArrangement=Arrangement.spacedBy(2.dp)) {
        assigned.take(3).forEach { t ->
            TextButton(onClick={info=t},contentPadding=PaddingValues(2.dp),modifier=Modifier.heightIn(min=48.dp)) {
                TechnologyIcon(t)
                if(!compact) { Spacer(Modifier.width(4.dp));Text(t.name,Modifier.widthIn(max=120.dp)) }
            }
        }
        TextButton(onClick={all=true},modifier=Modifier.semantics { contentDescription="Ver todas las tecnologías asignadas" }) {
            Text(if(assigned.size>3) "+${assigned.size-3}" else "Ver todas")
        }
    }
    if(all) TechnologyWideDialog("Tecnologías asignadas",{all=false}) {
        TechnologyGrid(assigned, emptyList(), true, false, {info=it})
    }
    info?.let { t->AlertDialog(onDismissRequest={info=null},title={Text(t.name)},text={TechnologyIcon(t)},confirmButton={TextButton(onClick={info=null}) {Text("Cerrar")}}) }
}

@Composable
private fun TechnologyWideDialog(title:String,onDismiss:()->Unit,content:@Composable ColumnScope.()->Unit) {
    Dialog(onDismissRequest=onDismiss,properties=DialogProperties(usePlatformDefaultWidth=false)) {
        Surface(Modifier.fillMaxWidth().fillMaxHeight(.9f).safeDrawingPadding().padding(12.dp),shape=RoundedCornerShape(16.dp),color=Arachn0deColors.Surface) {
            Column(Modifier.padding(12.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                    Text(title,Modifier.weight(1f),style=MaterialTheme.typography.titleLarge)
                    IconButton(onClick=onDismiss) {Icon(Icons.Default.Close,"Cerrar selector")}
                }
                content()
            }
        }
    }
}

@Composable
private fun ColumnScope.TechnologyGrid(catalog:List<TechnologyEntity>,selected:List<String>,enabled:Boolean,editing:Boolean,onClick:(TechnologyEntity)->Unit) {
    var query by rememberSaveable {mutableStateOf("")}
    val filtered=remember(catalog,query) {catalog.filter {it.name.contains(query.trim(),ignoreCase=true)}}
    OutlinedTextField(query,{query=it},label={Text("Buscar tecnologías")},singleLine=true,modifier=Modifier.fillMaxWidth())
    if(filtered.isEmpty()) Text("No hay tecnologías que coincidan")
    LazyVerticalGrid(columns=GridCells.Adaptive(140.dp),modifier=Modifier.weight(1f).fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(6.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
        items(filtered,key={it.id}) {t->
            val chosen=t.id in selected
            OutlinedCard(onClick={onClick(t)},enabled=enabled,modifier=Modifier.fillMaxWidth().heightIn(min=80.dp).testTag("technology-tile:${t.id}").semantics { contentDescription=t.name; if(editing) this.selected=chosen },
                colors=CardDefaults.outlinedCardColors(containerColor=if(chosen) MaterialTheme.colorScheme.primaryContainer else Arachn0deColors.ControlSurface)) {
                Row(Modifier.padding(8.dp),verticalAlignment=Alignment.CenterVertically) {
                    TechnologyIcon(t);Spacer(Modifier.width(6.dp));Text(t.name,Modifier.weight(1f))
                    if(editing) Checkbox(chosen,null,enabled=enabled)
                }
            }
        }
    }
}

@Composable
internal fun TechnologyAssignmentDialog(ownerId:String,project:Boolean,catalog:List<TechnologyEntity>,assigned:List<TechnologyEntity>,busy:Boolean,onDismiss:()->Unit,onSave:(Set<String>)->Unit) {
    var selected by rememberSaveable(ownerId,project,stateSaver=listSaver<List<String>,String>(save={it},restore={it})) {mutableStateOf(assigned.map {it.id})}
    TechnologyPicker(catalog,selected,!busy,onDismiss,{selected=it}) {onSave(selected.toSet())}
}

/** The form renders only an access button. Cancel never writes to the editor draft. */
@Composable
internal fun TechnologySelection(catalog:List<TechnologyEntity>,selected:List<String>,enabled:Boolean,onChange:(List<String>)->Unit) {
    var open by rememberSaveable {mutableStateOf(false)}
    var pending by rememberSaveable(stateSaver=listSaver<List<String>,String>(save={it},restore={it})) {mutableStateOf(selected)}
    OutlinedButton(enabled=enabled,onClick={pending=selected;open=true}) {Text("Tecnologías (${selected.size})")}
    if(open) TechnologyPicker(catalog,pending,enabled,{open=false},{pending=it}) {onChange(pending);open=false}
}

@Composable
private fun TechnologyPicker(catalog:List<TechnologyEntity>,selected:List<String>,enabled:Boolean,onDismiss:()->Unit,onChange:(List<String>)->Unit,onConfirm:()->Unit) {
    var ordering by rememberSaveable {mutableStateOf(false)}
    var info by remember {mutableStateOf<TechnologyEntity?>(null)}
    val centers=remember {mutableStateMapOf<String,Float>()}
    val latestSelected by rememberUpdatedState(selected)
    val latestChange by rememberUpdatedState(onChange)
    TechnologyWideDialog("Tecnologías",{if(enabled) onDismiss()}) {
        Text("${selected.size} seleccionadas · las tres primeras se muestran en las tarjetas",style=MaterialTheme.typography.bodySmall)
        TextButton(enabled=enabled,onClick={ordering=!ordering}) {Text(if(ordering) "Elegir tecnologías" else "Ordenar seleccionadas")}
        if(ordering) LazyColumn(Modifier.weight(1f).fillMaxWidth()) {
            items(selected,key={it}) {id->
                val t=catalog.find {it.id==id}
                DisposableEffect(id) {onDispose {centers.remove(id)}}
                Row(Modifier.fillMaxWidth().onGloballyPositioned {c->centers[id]=c.positionInRoot().y+c.size.height/2f},verticalAlignment=Alignment.CenterVertically) {
                    Icon(Icons.Default.DragHandle,"Arrastrar ${t?.name ?: id}",Modifier.size(48.dp).pointerInput(id,enabled) {
                        var dy=0f;var initial=0f
                        detectDragGesturesAfterLongPress(onDragStart={dy=0f;initial=centers[id] ?: 0f}) {change,delta->
                            if(enabled) {change.consume();dy+=delta.y;val list=latestSelected.toMutableList();val target=list.filter {it in centers}.minByOrNull {kotlin.math.abs(requireNotNull(centers[it])-(initial+dy))};val from=list.indexOf(id);val to=list.indexOf(target);if(from>=0 && to>=0 && from!=to) {list.add(to,list.removeAt(from));latestChange(list)}}
                        }
                    })
                    TextButton(onClick={if(t!=null) info=t},modifier=Modifier.weight(1f)) {if(t!=null) TechnologyIcon(t);Text(t?.name ?: "Tecnología no disponible")}
                    val index=selected.indexOf(id)
                    IconButton(enabled=enabled && index>0,onClick={onChange(selected.toMutableList().apply {add(index-1,removeAt(index))})}) {Icon(Icons.Default.ArrowUpward,"Subir ${t?.name ?: id}")}
                    IconButton(enabled=enabled && index<selected.lastIndex,onClick={onChange(selected.toMutableList().apply {add(index+1,removeAt(index))})}) {Icon(Icons.Default.ArrowDownward,"Bajar ${t?.name ?: id}")}
                    IconButton(enabled=enabled,onClick={onChange(selected-id)}) {Icon(Icons.Default.Close,"Quitar ${t?.name ?: id}")}
                }
            }
        } else TechnologyGrid(catalog,selected,enabled,true) {t->onChange(if(t.id in selected) selected-t.id else selected+t.id)}
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.End) {
            TextButton(enabled=enabled,onClick=onDismiss) {Text("Cancelar")}
            TextButton(enabled=enabled,onClick=onConfirm) {Text("Confirmar")}
        }
    }
    info?.let {t->AlertDialog(onDismissRequest={info=null},title={Text(t.name)},text={TechnologyIcon(t)},confirmButton={TextButton(onClick={info=null}) {Text("Cerrar")}})}
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
internal fun DraftTechnologies(draft:com.r0ybt.arachn0de.ui.state.EditorDraft,enabled:Boolean,project:Boolean=false) {
    val app=LocalContext.current.applicationContext as? Arachn0deApplication ?: return
    val state by app.technologyRepository.state.collectAsState()
    LaunchedEffect(state.loaded,draft.creationId) {if(state.loaded && !draft.technologiesLoaded) {draft.technologyIds=draft.id?.let {state.forOwner(it,project).map {t->t.id}}.orEmpty();draft.originalTechnologyIds=draft.technologyIds;draft.technologiesLoaded=true}}
    if(state.loaded) TechnologySelection(state.catalog,draft.technologyIds,enabled) {draft.technologyIds=it}
}

@Composable
internal fun CompactAssignments(ownerId:String,people:List<com.r0ybt.arachn0de.domain.model.Person>,modifier:Modifier=Modifier,project:Boolean=false) {
    val app=LocalContext.current.applicationContext as? Arachn0deApplication
    val state by (app?.technologyRepository?.state ?: kotlinx.coroutines.flow.MutableStateFlow(com.r0ybt.arachn0de.data.repository.TechnologyState())).collectAsState()
    val technologies=state.forOwner(ownerId,project)
    if(technologies.isEmpty() && people.isEmpty()) return
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val shared=technologies.isNotEmpty() && people.isNotEmpty()
        val sectionWidth=if(shared) (maxWidth-8.dp)/2 else maxWidth
        FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp),itemVerticalAlignment=Alignment.CenterVertically) {
            if(technologies.isNotEmpty()) TechnologyLabels(technologies,true,Modifier.widthIn(max=sectionWidth))
            if(people.isNotEmpty()) Box(Modifier.widthIn(max=sectionWidth)) {ResponsibleAvatars(people)}
        }
    }
}
