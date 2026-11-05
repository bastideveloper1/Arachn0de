package com.r0ybt.arachn0de.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.r0ybt.arachn0de.Arachn0deApplication
import com.r0ybt.arachn0de.access.ElementResult
import com.r0ybt.arachn0de.data.repository.NodeRepository
import com.r0ybt.arachn0de.data.repository.PersonRepository
import com.r0ybt.arachn0de.domain.model.*
import com.r0ybt.arachn0de.ui.state.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable internal fun DraftsScreen(repository:NodeRepository,peopleRepository:PersonRepository,index:List<ElementResult>,store:EditorDraftStore = rememberSaveable(saver=EditorDraftStore.Saver) {EditorDraftStore()},onBack:()->Unit) {
    val nameSearch=rememberInlineSearch("drafts")
    val originalSearchListState=androidx.compose.foundation.lazy.rememberLazyListState()
    val filteredSearchListState=androidx.compose.foundation.lazy.rememberLazyListState()

    val load=remember(repository) {LoadState()}
    var rows by remember(repository) {mutableStateOf<List<Node>?>(null)}
    LaunchedEffect(repository,load.attempt) {load.collect(repository.observeDrafts()) {rows=it}}
    var editing by rememberSaveable {mutableStateOf(false)}
    var createStructure by remember {mutableStateOf(false)}
    var editingId by rememberSaveable {mutableStateOf<String?>(null)}
    if(createStructure) StructureCreationPanel(null,null,onNew={createStructure=false;editingId=null;editing=true},draftDestination=true,onDismiss={createStructure=false})
    var moving by rememberSaveable {mutableStateOf<String?>(null)}
    var deleting by rememberSaveable {mutableStateOf<String?>(null)}
    val scope=rememberCoroutineScope();val op=remember(scope,repository) {OperationState(scope)}
    androidx.activity.compose.BackHandler(enabled=!editing && moving==null && deleting==null && !nameSearch.open,onBack=onBack)
    Surface(Modifier.fillMaxSize(),color=com.r0ybt.arachn0de.ui.theme.Arachn0deColors.Background) {
        Column(Modifier.fillMaxSize().padding(14.dp).testTag("drafts-screen")) {
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                GlobalChromeActions(leading=true)
                IconButton(onClick=onBack) {LucideIcon(Icons.Default.ArrowBack,"Volver")}
                NavigationHeading("Borradores",Modifier.weight(1f))
                GlobalChromeActions()
            }
            Text("Captura ahora, organiza después.",style=MaterialTheme.typography.bodySmall)
            Box(Modifier.weight(1f).fillMaxWidth().padding(top=8.dp)) {
                if(rows==null) Text("Cargando…")
                else if(rows.isNullOrEmpty()) Text("No hay borradores")
                else LazyColumn(Modifier.fillMaxSize(),state=if(nameSearch.active)filteredSearchListState else originalSearchListState,verticalArrangement=Arrangement.spacedBy(8.dp)) {
                    items(rows.orEmpty().filter { !nameSearch.active || nameMatches(it.title,nameSearch.query) },key={it.id}) {node->
                        Card(Modifier.fillMaxWidth(),colors=CardDefaults.cardColors(containerColor=com.r0ybt.arachn0de.ui.theme.Arachn0deColors.Surface)) {
                            Row(Modifier.fillMaxWidth().clickable {editingId=node.id;editing=true}.padding(12.dp),verticalAlignment=Alignment.CenterVertically) {
                                LucideIcon(if(AttachmentReferences.matches(node.description).any {AttachmentReferences.isAudio(it)}) Icons.Default.Mic else if(node.purpose==NodePurpose.NOTE) Icons.Default.Description else Icons.Default.CheckBoxOutlineBlank,null,Modifier.size(24.dp))
                                Column(Modifier.weight(1f).padding(horizontal=8.dp)) {
                                    Text(node.title,style=MaterialTheme.typography.titleSmall,maxLines=2)
                                    Text((if(node.purpose==NodePurpose.NOTE) "Nota" else "Tarea")+" · "+java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.SHORT,java.text.DateFormat.SHORT).format(java.util.Date(node.updatedAt)),style=MaterialTheme.typography.bodySmall)
                                }
                                FavoriteToggle("node:${node.id}")
                            }
                            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.End) {
                                TextButton(enabled=!op.busy,onClick={moving=node.id}) {Text("Mover a…")}
                                TextButton(enabled=!op.busy,onClick={deleting=node.id}) {Text("Eliminar")}
                            }
                        }
                    }
                }
            }
            ContextualBottomPanel {
            AlternatingBottomBar {
            Row(Modifier.fillMaxWidth().testTag("drafts-navigation-actions"),verticalAlignment=Alignment.CenterVertically) {
                Button(enabled=!op.busy,onClick={createStructure=true},modifier=Modifier.weight(1f).testTag("new-draft")) {
                    Icon(androidx.compose.ui.res.painterResource(com.r0ybt.arachn0de.R.drawable.custom_crear_borrador),null);Text("Crear borrador")
                }
                if(!editing && moving==null && deleting==null) GlobalMenuAccess()
            }
            }
            }
        }
    }
    if(editing) DraftElementEditor(editingId,repository,peopleRepository,store) {editing=false;editingId=null}
    moving?.let {id-> DraftMoveDialog(id,index,repository) {moving=null} }
    deleting?.let {id-> AlertDialog(onDismissRequest={if(!op.busy) deleting=null},title={Text("¿Eliminar borrador?")},
        text={Text("Se eliminará el elemento y se liberarán sus adjuntos cuando no tengan otras referencias.")},
        confirmButton={DestructiveAction("Eliminar",{op.submit("No se pudo eliminar el borrador.",{repository.deleteNode(id)},{deleting=null})},enabled=!op.busy)},
        dismissButton={TextButton(enabled=!op.busy,onClick={deleting=null}) {Text("Cancelar")}}) }
    LoadErrorDialog(load)
    OperationErrorDialog(op)
}

/** Same editor, save path, attachment reservations and audio controller as project content. */
@Composable internal fun DraftElementEditor(id:String?,repository:NodeRepository,peopleRepository:PersonRepository,store:EditorDraftStore = rememberSaveable(saver=EditorDraftStore.Saver) {EditorDraftStore()},quickCapture:Boolean=false,onDismiss:()->Unit) {
    val application=LocalContext.current.applicationContext as Arachn0deApplication
    val attachments=application.attachmentRepository
    val scope=rememberCoroutineScope();val actions=remember(repository,scope,attachments) {NodeActions(repository,scope,attachments)}
    val draft=store.active?.takeIf {it.id==id}
    var templateOptions by remember {mutableStateOf(false)}
    var saveTemplate by remember {mutableStateOf(false)}
    if(saveTemplate && id!=null) SaveStructurePanel(null,id,draft?.title ?: "Borrador") {saveTemplate=false}
    var referencesReady by remember(draft?.attachmentDraftId) {mutableStateOf(false)}
    var people by remember {mutableStateOf<List<Person>?>(null)}
    val load=remember(id,repository) {LoadState()}
    LaunchedEffect(id,repository,load.attempt) {
        load.collect(peopleRepository.observePeople()) {people=it}
    }
    LaunchedEffect(id,repository,people,load.attempt) {
        if(people==null || draft!=null) return@LaunchedEffect
        try {
            val node=withContext(Dispatchers.IO) {id?.let {requireNotNull(repository.getNode(it))}}
            val assignments=withContext(Dispatchers.IO) {peopleRepository.observeAllAssignments().first()}
            val tags=withContext(Dispatchers.IO) {repository.tags.observe().first()}
            store.open(null,id) {EditorDraft(node?.id,null,node?.title.orEmpty(),node?.description.orEmpty(),startAt=node?.startAt,dueAt=node?.dueAt,purpose=node?.purpose ?: NodePurpose.ACTION,obligation=node?.obligation,priority=node?.priority ?: Priority.NONE).apply {
                tagIds=tags.nodeIds[id].orEmpty().toList();responsibleIds=assignments[id].orEmpty().map {it.id}
            }}
        } catch(c:kotlinx.coroutines.CancellationException) {throw c}
        catch(_:Exception) {actions.operation.submit("No se pudo abrir el borrador. Se conserva el contenido.",{false})}
    }
    LaunchedEffect(draft?.attachmentDraftId,load.attempt) {
        val editor=draft ?: return@LaunchedEffect
        try {
            attachments.retainGallery(editor.attachmentDraftId,AttachmentReferences.ids(editor.description))
            referencesReady=true
        } catch(c:kotlinx.coroutines.CancellationException) {throw c}
        catch(_:Exception) {editor.attachmentError="No se pudieron reservar los adjuntos. Cierra y reabre el editor para reintentar."}
    }
    draft?.let {editor->
        val audioCreationValid=referencesReady && !editor.batchEnabled && editor.recurrenceFrequency=="NONE" &&
            editor.tagIds.size<=1 && TitleLimits.count(editor.title)<=TitleLimits.NODE && runCatching {editor.obligation()}.isSuccess &&
            (!editor.startEnabled || editor.startAt!=null) && (!editor.dueEnabled || editor.dueAt!=null) &&
            (editor.activeStart==null || editor.activeDue==null || editor.activeDue!!>=editor.activeStart!!)
        fun closeEditor() = actions.operation.submit("No se pudieron conservar los adjuntos del editor. Puedes reintentar o descartar sus cambios.",{
            attachments.retainGallery(editor.attachmentDraftId,AttachmentReferences.ids(editor.description));true
        },{store.close();onDismiss()})
        fun save(audio:Boolean=false) {
            if(audio) {editor.purpose=NodePurpose.ACTION;editor.audioCreationRequested=true
                if(editor.title.isBlank()) {editor.title="Grabación";editor.audioProvisionalTitle=editor.title}}
            actions.saveDraft(null,editor,editor.purpose==NodePurpose.ACTION) {
                if(audio) application.security.current.value?.audio?.startRecording(editor.id ?: editor.creationId)
                store.clear();onDismiss()
            }
        }
        NodeDialog(editor,actions.operation.busy,{closeEditor()},{_,_->save()},people=people.orEmpty(),peopleLoaded=people!=null,
            compactCapture=quickCapture && editor.id==null,tagRepository=repository.tags,onDiscard={actions.discardDraft(editor) {store.clear();onDismiss()}},projectless=true,extraValid=referencesReady,
            extraFields={
                if(editor.id==null) AudioMicButton(enabled=audioCreationValid && !actions.operation.busy && !editor.attachmentBusy && editor.attachmentsLoaded) {save(audio=true)}
                else Box {
                    TextButton(onClick={templateOptions=true},enabled=!actions.operation.busy) {Text("Opciones")}
                    DropdownMenu(templateOptions,{templateOptions=false}) {
                        DropdownMenuItem(text={Text("Guardar como plantilla")},onClick={templateOptions=false;saveTemplate=true})
                    }
                }
            })
    }
    OperationErrorDialog(actions.operation)
    LoadErrorDialog(load)
}

@Composable internal fun DraftMoveDialog(id:String,index:List<ElementResult>,repository:NodeRepository,onDismiss:()->Unit) {
    NodeDestinationDialog(setOf(id),index,repository,onDismiss=onDismiss)
}
