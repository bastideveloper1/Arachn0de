package com.r0ybt.arachn0de.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.IntOffset
import kotlin.math.roundToInt
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.r0ybt.arachn0de.Arachn0deApplication
import com.r0ybt.arachn0de.data.local.*
import com.r0ybt.arachn0de.data.repository.*
import com.r0ybt.arachn0de.ui.theme.Arachn0deColors
import kotlinx.coroutines.*
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.UUID

private data class MemoryEditRequest(val entry:PersonMemoryEntity,val images:List<PersonMemoryImageEntity>,val draft:String,val image:String?,val creating:Boolean=false)

private fun imageValues(image:PersonMemoryImageEntity)=listOf(image.id,image.memoryId,image.attachmentId.orEmpty(),image.legacyPhotoId.orEmpty(),image.position.toString(),image.date.orEmpty(),image.description)
private fun imageFrom(values:List<String>)=PersonMemoryImageEntity(values[0],values[1],values[2].ifEmpty {null},values[3].ifEmpty {null},values[4].toInt(),values[5].ifEmpty {null},values[6])
private val MemoryImagesSaver=listSaver<List<PersonMemoryImageEntity>,String>(save={it.flatMap(::imageValues)},restore={it.chunked(7).map(::imageFrom)})
private val MemoryImageSaver=listSaver<PersonMemoryImageEntity?,String>(save={it?.let(::imageValues).orEmpty()},restore={if(it.isEmpty()) null else imageFrom(it)})
private val MemoryRequestSaver=listSaver<MemoryEditRequest?,String>(save={request->
    if(request==null) emptyList() else listOf(request.entry.id,request.entry.personId,request.entry.date.orEmpty(),request.entry.description,request.entry.position.toString(),request.draft,request.image.orEmpty(),request.creating.toString(),request.entry.isPinned.toString())+request.images.flatMap(::imageValues)
},restore={
    if(it.isEmpty()) null else {
        val header=if((it.size-8)%7==0) 8 else 9
        MemoryEditRequest(PersonMemoryEntity(it[0],it[1],it[2].ifEmpty {null},it[3],it[4].toInt(),header==9 && it[8].toBoolean()),it.drop(header).chunked(7).map(::imageFrom),it[5],it[6].ifEmpty {null},it[7].toBoolean())
    }
})

@Composable internal fun PersonMemoryGallery(person:String,people:PersonRepository,beforeGallery:LazyListScope.()->Unit) {
    val context=LocalContext.current
    val app=context.applicationContext as Arachn0deApplication
    val attachments=app.attachmentRepository
    val repository=remember(app.database,people) {PersonMemoryRepository(app.database,privateStorageContext(context),attachments,people)}
    val state by remember(repository) {repository.observe()}.collectAsState(initial=null)
    var viewing by rememberSaveable(person) {mutableStateOf<String?>(null)}
    var editing by rememberSaveable(person,stateSaver=MemoryRequestSaver) {mutableStateOf<MemoryEditRequest?>(null)}
    var historical by rememberSaveable(person) {mutableStateOf<String?>(null)}
    val listState=rememberLazyListState()
    val drag=remember(person) {MemoryGridDrag()}
    var viewport by remember {mutableStateOf(Rect.Zero)}
    var busy by remember {mutableStateOf(false)}
    var error by remember {mutableStateOf<String?>(null)}
    val scope=rememberCoroutineScope()
    val picker=rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia()) {uris->
        if(uris.isNotEmpty()) {
            busy=true
            scope.launch {
                val draft=UUID.randomUUID().toString()
                try {
                    val files=uris.map {attachments.importGallery(it,draft)}.distinctBy {it.id}
                    val id=UUID.randomUUID().toString()
                    editing=MemoryEditRequest(PersonMemoryEntity(id,person,LocalDate.now().toString(),"",0),
                        files.mapIndexed {i,file->PersonMemoryImageEntity(UUID.randomUUID().toString(),id,file.id,null,i)},draft,null,true)
                } catch(c:CancellationException) {withContext(NonCancellable) {attachments.discardDraft(draft)};throw c}
                catch(e:Exception) {withContext(NonCancellable) {attachments.discardDraft(draft)};error=e.message ?: "No se pudieron agregar las fotografías."}
                finally {busy=false}
            }
        }
    }
    val entries=state?.entries.orEmpty().filter {it.personId==person}
    LaunchedEffect(state,viewing) {if(state!=null && viewing!=null && entries.none {it.id==viewing}) viewing=null}
    drag.groups=entries.associate {it.id to it.isPinned};drag.enabled=!busy && editing==null && viewing==null
    LaunchedEffect(entries,drag.enabled) {if(!drag.enabled || drag.dragged !in entries.map {it.id}) drag.cancel()}
    MemoryGridAutoScroll(drag,listState,viewport)
    Box(Modifier.fillMaxSize().onGloballyPositioned {viewport=it.boundsInRoot()}) {
    LazyColumn(Modifier.fillMaxSize().testTag("person-general").memoryGridGestures(drag,viewport.topLeft) {source,target->
        busy=true;scope.launch {try {repository.reorder(person,source,target)} catch(c:CancellationException) {throw c} catch(e:Exception) {error=e.message ?: "No se pudo guardar el orden."} finally {busy=false}}
    },state=listState,userScrollEnabled=drag.dragged==null,verticalArrangement=Arrangement.spacedBy(8.dp)) {
        beforeGallery()
        item(key="gallery-header") {Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
            Text("Fotografías",Modifier.weight(1f),style=MaterialTheme.typography.titleMedium)
            IconButton(enabled=!busy && state!=null,onClick={picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))},modifier=Modifier.size(48.dp)) {Icon(Icons.Default.Add,"Agregar fotografías")}
        }}
        if(state==null) item {Text("Cargando fotografías…")}
        else if(entries.isEmpty()) item {Text("Sin fotografías.",color=Arachn0deColors.TextSecondary)}
        items(entries.chunked(3),key={it.first().id}) {row->
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(3.dp)) {
                row.forEach {entry->
                    val images=state!!.photos(entry.id)
                    DisposableEffect(entry.id) {onDispose {drag.bounds.remove(entry.id)}}
                    var menu by remember {mutableStateOf(false)}
                    Box(Modifier.weight(1f).aspectRatio(1f).onGloballyPositioned {drag.bounds[entry.id]=it.boundsInRoot()}
                        .graphicsLayer {alpha=if(drag.dragged==entry.id) .35f else 1f}
                        .testTag("memory:${entry.id}").clickable(enabled=!busy && images.isNotEmpty()) {viewing=entry.id}) {
                        images.firstOrNull()?.let {MemoryThumbnail(it,state!!,repository,Modifier.fillMaxSize())}
                        if(images.size>1) Surface(Modifier.align(Alignment.BottomEnd).padding(4.dp),color=Arachn0deColors.ControlSurface,shape=MaterialTheme.shapes.small) {
                            Row(Modifier.padding(4.dp),horizontalArrangement=Arrangement.spacedBy(3.dp),verticalAlignment=Alignment.CenterVertically) {
                                Icon(Icons.Default.Collections,"Carrusel de ${images.size} fotografías",Modifier.size(16.dp));Text(images.size.toString(),style=MaterialTheme.typography.labelSmall)
                            }
                        }
                        if(entry.isPinned) Surface(Modifier.align(Alignment.TopStart).padding(4.dp),shape=MaterialTheme.shapes.small,color=Arachn0deColors.ControlSurface) {
                            Icon(Icons.Default.PushPin,"Entrada fijada",Modifier.padding(3.dp).size(14.dp),tint=Arachn0deColors.TextPrimary)
                        }
                        Box(Modifier.align(Alignment.TopEnd)) {
                            IconButton(enabled=!busy,onClick={menu=true},modifier=Modifier.size(48.dp).testTag("memory-options:${entry.id}")) {
                                Surface(color=Arachn0deColors.ControlSurface,shape=MaterialTheme.shapes.small) {Icon(Icons.Default.MoreVert,"Opciones de la entrada",Modifier.padding(4.dp).size(20.dp))}
                            }
                            if(menu) ContextActionMenu("Entrada fotográfica",{menu=false}) {
                                action(if(entry.isPinned) "Desfijar" else "Fijar",Icons.Default.PushPin,{
                                    menu=false;busy=true;scope.launch {try {repository.pin(entry.id,!entry.isPinned)} catch(c:CancellationException) {throw c} catch(e:Exception) {error=e.message ?: "No se pudo fijar la entrada."} finally {busy=false}}
                                },enabled=!busy)
                                if(images.any {it.description.isNotBlank()}) action("Textos históricos",Icons.Default.Description,{menu=false;historical=entry.id},enabled=!busy)
                            }
                        }
                    }
                }
                repeat(3-row.size) {Spacer(Modifier.weight(1f))}
            }
        }
    }
    val draggedEntry=entries.firstOrNull {it.id==drag.dragged}
    val draggedSize=drag.size
    if(draggedEntry!=null && draggedSize.width>0 && state!=null) {
        val density=androidx.compose.ui.platform.LocalDensity.current
        state!!.photos(draggedEntry.id).firstOrNull()?.let {image->
            MemoryThumbnail(image,state!!,repository,Modifier.offset {IntOffset((drag.point.x-drag.grab.x-viewport.left).roundToInt(),(drag.point.y-drag.grab.y-viewport.top).roundToInt())}
                .size(with(density) {draggedSize.width.toDp()},with(density) {draggedSize.height.toDp()}))
        }
    }
    }
    historical?.let {entryId->
        val texts=state?.photos(entryId).orEmpty().mapIndexedNotNull {index,image->image.description.takeIf {it.isNotBlank()}?.let {index+1 to it}}
        if(texts.isNotEmpty()) Dialog(onDismissRequest={historical=null},properties=DialogProperties(usePlatformDefaultWidth=false)) {
            Surface(Modifier.fillMaxSize(),color=Arachn0deColors.Background) {
                Column(Modifier.safeDrawingPadding().padding(12.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                    Text("Textos históricos",style=MaterialTheme.typography.titleLarge)
                    Column(Modifier.weight(1f).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                        texts.forEach {(index,text)->Text("Fotografía $index",style=MaterialTheme.typography.labelLarge);androidx.compose.foundation.text.selection.SelectionContainer {Text(text)}}
                    }
                    TextButton(onClick={historical=null},modifier=Modifier.align(Alignment.End).heightIn(min=48.dp)) {Text("Cerrar")}
                }
            }
        }
    }
    val selected=entries.firstOrNull {it.id==viewing}
    if(selected!=null && state!=null) MemoryViewer(selected,state!!,repository,people,attachments,onEdit={image->
        editing=MemoryEditRequest(selected,state!!.photos(selected.id),UUID.randomUUID().toString(),image)
    },onClose={viewing=null},onError={error=it})
    editing?.let {request->if(state!=null) MemoryEditor(request,state!!,repository,people,attachments,
        onClose={editing=null},onError={error=it})}
    error?.let {AlertDialog(onDismissRequest={error=null},text={Text(it)},confirmButton={TextButton(onClick={error=null}) {Text("Cerrar")}})}
}

@Composable private fun MemoryThumbnail(image:PersonMemoryImageEntity,state:PersonMemoryState,repository:PersonMemoryRepository,modifier:Modifier=Modifier) {
    val file=runCatching {repository.file(image,state)}.getOrNull()
    val bitmap by produceState<android.graphics.Bitmap?>(null,file?.absolutePath) {
        value=withContext(Dispatchers.IO) {if(file==null) null else runCatching {repository.thumbnail(image,state,256)}.getOrNull()}
    }
    Box(modifier.background(Arachn0deColors.ControlSurface),contentAlignment=Alignment.Center) {
        if(bitmap!=null) Image(bitmap!!.asImageBitmap(),"Fotografía",Modifier.fillMaxSize(),contentScale=ContentScale.Crop)
        else Icon(Icons.Default.Image,"Fotografía no disponible",Modifier.size(24.dp))
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable private fun MemoryViewer(entry:PersonMemoryEntity,state:PersonMemoryState,repository:PersonMemoryRepository,people:PersonRepository,attachments:AttachmentRepository,
    onEdit:(String)->Unit,onClose:()->Unit,onError:(String)->Unit) {
    val images=state.photos(entry.id)
    val pager=rememberPagerState(pageCount={images.size})
    val scope=rememberCoroutineScope()
    var busy by remember {mutableStateOf(false)}
    var deletion by remember {mutableStateOf(false)}
    var wholeEntry by remember {mutableStateOf(false)}
    val current=images.getOrNull(pager.currentPage)
    val exportDraft=rememberSaveable(entry.id) {UUID.randomUUID().toString()}
    var exportImage by rememberSaveable(entry.id,stateSaver=MemoryImageSaver) {mutableStateOf<PersonMemoryImageEntity?>(null)}
    val exportState by rememberUpdatedState(state)
    val download=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("image/*")) {uri->
        scope.launch {
            try {if(uri!=null) exportImage?.let {repository.export(it,exportState,uri)}}
            catch(c:CancellationException) {throw c} catch(e:Exception) {onError("No se pudo descargar la fotografía original: ${e.message.orEmpty()}")}
            finally {withContext(NonCancellable) {runCatching {attachments.discardDraft(exportDraft)};exportImage?.legacyPhotoId?.let {id->state.legacy.firstOrNull {it.id==id}?.let {runCatching {people.discardAvatar(it.file,exportDraft)}}}};exportImage=null;busy=false}
        }
    }
    Dialog(onDismissRequest={if(!busy) onClose()},properties=DialogProperties(usePlatformDefaultWidth=false,decorFitsSystemWindows=false)) {
        Column(Modifier.fillMaxSize().background(Arachn0deColors.Background).safeDrawingPadding().padding(12.dp).testTag("memory-viewer")) {
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                IconButton(enabled=!busy,onClick=onClose,modifier=Modifier.size(48.dp)) {Icon(Icons.Default.ArrowBack,"Volver")}
                Spacer(Modifier.weight(1f));Text("${if(images.isEmpty()) 0 else pager.currentPage+1}/${images.size}")
            }
            VerticalPager(pager,Modifier.fillMaxWidth().weight(1f).testTag("memory-carousel")) {page->
                val image=images[page]
                val file=runCatching {repository.file(image,state)}.getOrNull()
                val loaded by produceState<Pair<android.graphics.Bitmap?,Boolean>>(null to false,file?.absolutePath) {
                    value=withContext(Dispatchers.IO) {(if(file==null) null else runCatching {decodeAttachment(file)}.getOrNull()) to true}
                }
                ZoomableImage(loaded.first,loaded.second,"Fotografía ${page+1}",Modifier.fillMaxSize(),carousel=true)
            }
            Column(Modifier.fillMaxWidth().heightIn(max=160.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                Text(current?.date ?: entry.date ?: "Sin fecha",style=MaterialTheme.typography.bodySmall)
                if(entry.description.isNotBlank()) Text(entry.description)
            }
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                TextButton(enabled=!busy && current!=null,onClick={
                    val image=current ?: return@TextButton
                    busy=true;scope.launch {
                        try {
                            image.attachmentId?.let {attachments.retainGallery(exportDraft,setOf(it))}
                            image.legacyPhotoId?.let {id->state.legacy.first {it.id==id}.let {people.retainAvatar(it.file,exportDraft)}}
                            exportImage=image
                            val name=image.attachmentId?.let {id->state.files.first {it.id==id}.originalName} ?: "fotografia.png"
                            download.launch(name)
                        } catch(c:CancellationException) {withContext(NonCancellable) {runCatching {attachments.discardDraft(exportDraft)};image.legacyPhotoId?.let {id->state.legacy.firstOrNull {it.id==id}?.let {runCatching {people.discardAvatar(it.file,exportDraft)}}}};throw c}
                        catch(e:Exception) {withContext(NonCancellable) {runCatching {attachments.discardDraft(exportDraft)};image.legacyPhotoId?.let {id->state.legacy.firstOrNull {it.id==id}?.let {runCatching {people.discardAvatar(it.file,exportDraft)}}}};exportImage=null;busy=false;onError(e.message ?: "No se pudo abrir Descargar.")}
                    }
                },modifier=Modifier.weight(1f).heightIn(min=48.dp)) {Column(horizontalAlignment=Alignment.CenterHorizontally) {Icon(Icons.Default.Download,null,Modifier.size(20.dp));Text("Descargar")}}
                TextButton(enabled=!busy && current!=null,onClick={current?.let {onEdit(it.id)}},modifier=Modifier.weight(1f).heightIn(min=48.dp)) {Column(horizontalAlignment=Alignment.CenterHorizontally) {Icon(Icons.Default.Edit,null,Modifier.size(20.dp));Text("Editar")}}
                TextButton(enabled=!busy && current!=null,onClick={wholeEntry=false;deletion=true},modifier=Modifier.weight(1f).heightIn(min=48.dp)) {Column(horizontalAlignment=Alignment.CenterHorizontally) {Icon(Icons.Default.Delete,null,Modifier.size(20.dp));Text("Eliminar")}}
            }
        }
    }
    if(deletion) AlertDialog(onDismissRequest={if(!busy) deletion=false},title={Text("Eliminar fotografía")},text={Column {
        Text("Las imágenes utilizadas por el avatar u otros elementos se conservan.")
        if(images.size>1) Row(verticalAlignment=Alignment.CenterVertically) {Checkbox(wholeEntry,{wholeEntry=it},enabled=!busy);Text("Eliminar la entrada completa (${images.size} fotografías)")}
        Text(if(wholeEntry || images.size==1) "Se eliminará esta entrada." else "Se quitará la fotografía actual del carrusel.")
    }},confirmButton={TextButton(enabled=!busy,onClick={busy=true;scope.launch {
        try {if(wholeEntry || images.size==1) {repository.deleteEntry(entry.id);onClose()} else current?.let {repository.removeImage(it.id)};deletion=false}
        catch(c:CancellationException) {throw c} catch(e:Exception) {onError(e.message ?: "No se pudo eliminar.")}
        finally {busy=false}
    }}) {Text("Eliminar")}},dismissButton={TextButton(enabled=!busy,onClick={deletion=false}) {Text("Cancelar")}})
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun MemoryEditor(request:MemoryEditRequest,state:PersonMemoryState,repository:PersonMemoryRepository,people:PersonRepository,attachments:AttachmentRepository,
    onClose:()->Unit,onError:(String)->Unit) {
    var date by rememberSaveable(request.draft) {mutableStateOf(request.entry.date)}
    var description by rememberSaveable(request.draft) {mutableStateOf(request.entry.description)}
    var images by rememberSaveable(request.draft,stateSaver=MemoryImagesSaver) {mutableStateOf(request.images)}
    var selected by rememberSaveable(request.draft) {mutableStateOf(request.image ?: request.images.firstOrNull()?.id)}
    var ready by remember {mutableStateOf(false)}
    var busy by remember {mutableStateOf(false)}
    var dateTarget by remember {mutableStateOf<String?>(null)}
    var removing by remember {mutableStateOf<PersonMemoryImageEntity?>(null)}
    var framing by remember {mutableStateOf<PersonPhotoEntity?>(null)}
    val legacyFiles=remember(request.draft) {request.images.mapNotNull {image->state.legacy.firstOrNull {it.id==image.legacyPhotoId}?.file}.toSet()}
    val scope=rememberCoroutineScope()
    suspend fun release() {
        attachments.discardDraft(request.draft)
        legacyFiles.forEach {people.discardAvatar(it,request.draft)}
    }
    LaunchedEffect(request.draft) {
        try {
            attachments.retainGallery(request.draft,images.mapNotNullTo(hashSetOf()) {it.attachmentId})
            legacyFiles.forEach {people.retainAvatar(it,request.draft)}
            ready=true
        } catch(c:CancellationException) {throw c} catch(e:Exception) {onError(e.message ?: "No se pudieron preparar las fotografías.")}
    }
    fun cancel() {if(!busy) {busy=true;scope.launch {try {release();onClose()} catch(c:CancellationException) {throw c} catch(e:Exception) {onError(e.message ?: "No se pudo cancelar.")} finally {busy=false}}}}
    val picker=rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia()) {uris->if(uris.isNotEmpty()) {
        busy=true;scope.launch {
            val batch=UUID.randomUUID().toString()
            try {
                val files=uris.map {attachments.importGallery(it,batch)}.distinctBy {it.id}
                attachments.retainGallery(request.draft,files.mapTo(hashSetOf()) {it.id})
                attachments.discardDraft(batch)
                val additions=files.filter {file->images.none {it.attachmentId==file.id}}.mapIndexed {i,file->PersonMemoryImageEntity(UUID.randomUUID().toString(),request.entry.id,file.id,null,images.size+i)}
                images=images+additions
                if(selected==null) selected=images.firstOrNull()?.id
            } catch(c:CancellationException) {withContext(NonCancellable) {attachments.discardDraft(batch)};throw c}
            catch(e:Exception) {withContext(NonCancellable) {attachments.discardDraft(batch)};onError(e.message ?: "No se pudieron agregar las fotografías.")}
            finally {busy=false}
        }
    }}
    val current=images.firstOrNull {it.id==selected} ?: images.firstOrNull()
    Dialog(onDismissRequest=::cancel,properties=DialogProperties(usePlatformDefaultWidth=false,decorFitsSystemWindows=false)) {
        Surface(Modifier.fillMaxSize(),color=Arachn0deColors.Background) {
            Column(Modifier.fillMaxSize().safeDrawingPadding().imePadding().padding(12.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                Text(if(request.creating) "Nueva entrada" else "Editar entrada",style=MaterialTheme.typography.titleLarge)
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                        TextButton(enabled=!busy,onClick={dateTarget="entry"},modifier=Modifier.weight(1f).heightIn(min=48.dp)) {Icon(Icons.Default.CalendarMonth,null,Modifier.size(20.dp));Spacer(Modifier.width(6.dp));Text(date ?: "Seleccionar fecha")}
                        if(date!=null) IconButton(enabled=!busy,onClick={date=null},modifier=Modifier.size(48.dp)) {Icon(Icons.Default.Close,"Quitar fecha de la entrada")}
                    }
                    OutlinedTextField(description,{if(it.length<=16000) description=it},label={Text("Descripción de la entrada")},modifier=Modifier.fillMaxWidth(),minLines=2,maxLines=5,enabled=!busy)
                    Text("Fotografías",style=MaterialTheme.typography.labelLarge)
                    images.forEachIndexed {index,image->
                        Card(Modifier.fillMaxWidth(),shape=MaterialTheme.shapes.medium,colors=CardDefaults.cardColors(containerColor=if(current?.id==image.id) Arachn0deColors.ControlSurface else Arachn0deColors.Surface)) {
                            Row(Modifier.fillMaxWidth().padding(8.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                                MemoryThumbnail(image,state,repository,Modifier.size(88.dp).clickable(onClickLabel="Seleccionar fotografía ${index+1}") {selected=image.id})
                                Column(Modifier.weight(1f)) {
                                    TextButton(enabled=!busy,onClick={selected=image.id},modifier=Modifier.fillMaxWidth().heightIn(min=48.dp),contentPadding=PaddingValues(0.dp)) {Text("${index+1}${if(current?.id==image.id) " · Seleccionada" else ""}")}
                                    Row {
                                        IconButton(enabled=!busy && index>0,onClick={images=images.toMutableList().also {java.util.Collections.swap(it,index,index-1)}.mapIndexed {i,row->row.copy(position=i)}},modifier=Modifier.size(48.dp)) {Icon(Icons.Default.ArrowUpward,"Mover antes")}
                                        IconButton(enabled=!busy && index<images.lastIndex,onClick={images=images.toMutableList().also {java.util.Collections.swap(it,index,index+1)}.mapIndexed {i,row->row.copy(position=i)}},modifier=Modifier.size(48.dp)) {Icon(Icons.Default.ArrowDownward,"Mover después")}
                                        IconButton(enabled=!busy,onClick={removing=image},modifier=Modifier.size(48.dp)) {Icon(Icons.Default.Delete,"Quitar fotografía")}
                                    }
                                }
                            }
                        }
                    }
                    TextButton(enabled=!busy && ready,onClick={picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))}) {Text("Agregar fotografías")}
                    if(current!=null) {
                        val legacy=state.legacy.firstOrNull {it.id==current.legacyPhotoId}
                        if(legacy!=null) TextButton(enabled=!busy && framing==null,onClick={if(!busy && framing==null) framing=legacy}) {Text("Ajustar encuadre")}
                        TextButton(enabled=!busy,onClick={busy=true;scope.launch {
                            try {repository.setAvatar(request.entry.personId,current,state)} catch(c:CancellationException) {throw c} catch(e:Exception) {onError(e.message ?: "No se pudo cambiar el avatar.")} finally {busy=false}
                        }}) {Text("Establecer como avatar principal")}
                    }
                }
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.End) {
                    TextButton(enabled=!busy,onClick=::cancel) {Text("Cancelar")}
                    Button(enabled=ready && !busy && images.isNotEmpty(),onClick={busy=true;scope.launch {
                        try {repository.save(request.entry.copy(date=date,description=description),images,request.draft);withContext(NonCancellable) {runCatching {release()}};onClose()}
                        catch(c:CancellationException) {throw c} catch(e:Exception) {onError(e.message ?: "No se pudo guardar.")} finally {busy=false}
                    }}) {Text("Guardar")}
                }
            }
        }
    }
    removing?.let {image->AlertDialog(onDismissRequest={removing=null},title={Text(if(images.size==1) "Eliminar entrada" else "Quitar fotografía")},text={Text(if(images.size==1) "Se eliminará la entrada completa. El avatar y los archivos usados por otros elementos se conservan." else "Se quitará esta fotografía del grupo al guardar. Los archivos compartidos se conservan.")},
        confirmButton={TextButton(enabled=!busy,onClick={
            if(images.size>1) {images=images.filter {it.id!=image.id}.mapIndexed {i,row->row.copy(position=i)};selected=images.firstOrNull()?.id;removing=null}
            else {busy=true;scope.launch {try {if(!request.creating) repository.deleteEntry(request.entry.id);withContext(NonCancellable) {runCatching {release()}};onClose()} catch(c:CancellationException) {throw c} catch(e:Exception) {onError(e.message ?: "No se pudo eliminar.")} finally {busy=false}}}
        }) {Text("Eliminar")}},dismissButton={TextButton(onClick={removing=null}) {Text("Cancelar")}})}
    framing?.let {photo->AvatarEditor(photo.file,com.r0ybt.arachn0de.domain.model.AvatarFraming(photo.zoom,photo.x,photo.y),enabled=!busy,onConfirm={frame->if(busy || framing?.id!=photo.id) return@AvatarEditor;busy=true;scope.launch {
        try {people.savePhoto(photo.copy(zoom=frame.zoom,x=frame.x,y=frame.y),request.draft);framing=null} catch(c:CancellationException) {throw c} catch(e:Exception) {onError(e.message ?: "No se pudo guardar el encuadre.")} finally {busy=false}
    }},onCancel={if(!busy) framing=null})}
    if(dateTarget!=null) {
        val old=date
        val initial=runCatching {LocalDate.parse(old).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()}.getOrDefault(LocalDate.now().atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
        val pickerState=rememberDatePickerState(initialSelectedDateMillis=initial,yearRange=1..9999)
        DatePickerDialog(onDismissRequest={dateTarget=null},confirmButton={TextButton(enabled=pickerState.selectedDateMillis!=null,onClick={
            val chosen=pickerState.selectedDateMillis?.let {java.time.Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate().toString()}
            date=chosen;dateTarget=null
        }) {Text("Seleccionar")}},dismissButton={TextButton(onClick={dateTarget=null}) {Text("Cancelar")}}) {DatePicker(pickerState,title={Text("Fecha")})}
    }
}
