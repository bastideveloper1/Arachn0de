package com.r0ybt.arachn0de.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.pager.HorizontalPager
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
    if(request==null) emptyList() else listOf(request.entry.id,request.entry.personId,request.entry.date.orEmpty(),request.entry.description,request.entry.position.toString(),request.draft,request.image.orEmpty(),request.creating.toString(),request.entry.isPinned.toString(),request.entry.peopleVisible.toString())+request.images.flatMap(::imageValues)
},restore={
    if(it.isEmpty()) null else {
        val header=if((it.size-10)%7==0) 10 else if((it.size-8)%7==0) 8 else 9
        MemoryEditRequest(PersonMemoryEntity(it[0],it[1],it[2].ifEmpty {null},it[3],it[4].toInt(),header>=9 && it[8].toBoolean(),if(header>=10) it[9].toBoolean() else true),it.drop(header).chunked(7).map(::imageFrom),it[5],it[6].ifEmpty {null},it[7].toBoolean())
    }
})

@Composable internal fun PersonMemoryGallery(person:String,people:PersonRepository,beforeGallery:LazyListScope.()->Unit) {
    val context=LocalContext.current
    val app=context.applicationContext as Arachn0deApplication
    val attachments=app.attachmentRepository
    val repository=remember(app.database,people) {PersonMemoryRepository(app.database,privateStorageContext(context),attachments,people)}
    val state by remember(repository) {repository.observe()}.collectAsState(initial=null)
    var visitingPerson by rememberSaveable(person) {mutableStateOf<String?>(null)}
    var viewing by rememberSaveable(person) {mutableStateOf<String?>(null)}
    var editing by rememberSaveable(person,stateSaver=MemoryRequestSaver) {mutableStateOf<MemoryEditRequest?>(null)}
    var historical by rememberSaveable(person) {mutableStateOf<String?>(null)}
    val listState=rememberLazyListState()
    val drag=remember(person) {MemoryGridDrag()}
    var viewport by remember {mutableStateOf(Rect.Zero)}
    var busy by remember {mutableStateOf(false)}
    var error by remember {mutableStateOf<String?>(null)}
    val scope=rememberCoroutineScope()
    fun createFromPhotos(uris:List<android.net.Uri>,done:(Boolean)->Unit={},cameraPhoto:com.r0ybt.arachn0de.photos.CameraPhoto?=null) {
        if(uris.isNotEmpty() || cameraPhoto!=null) {
            busy=true
            scope.launch {
                val draft=UUID.randomUUID().toString();var succeeded=false
                try {
                    val files=cameraPhoto?.let {attachments.retainGallery(draft,setOf(it.attachment.id));listOf(it.attachment)} ?: uris.map {attachments.importPrivatePhoto(it,draft)}.distinctBy {it.id}
                    val id=UUID.randomUUID().toString()
                    editing=MemoryEditRequest(PersonMemoryEntity(id,person,LocalDate.now().toString(),"",0),
                        files.mapIndexed {i,file->PersonMemoryImageEntity(UUID.randomUUID().toString(),id,file.id,null,i)},draft,null,true)
                    succeeded=true
                } catch(c:CancellationException) {withContext(NonCancellable) {attachments.discardDraft(draft)};throw c}
                catch(e:Exception) {withContext(NonCancellable) {attachments.discardDraft(draft)};error=e.message ?: "No se pudieron agregar las fotografías."}
                finally {busy=false;done(succeeded)}
            }
        }
    }
    var sourceOpen by remember {mutableStateOf(false)}
    val picker=rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia()) {uris->createFromPhotos(uris)}
    val camera=rememberPrivatePhotoCamera(!busy && editing==null,{error=it}) {photo,done->createFromPhotos(emptyList(),done,photo)}
    if(sourceOpen) ModularPanelDialog(onDismissRequest={sourceOpen=false},compact=true,plainBody=true,showActions=false,
        title={Text("Agregar fotografías")},text={ModularFieldMenu(listOf(
            ModularPanelOption("Cámara",Icons.Default.CameraAlt,"memory-camera",!busy) {sourceOpen=false;camera()},
            ModularPanelOption("Galería",Icons.Default.Image,"memory-gallery",!busy) {sourceOpen=false;picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))}
        ),compact=true)},confirmButton={})
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
            IconButton(enabled=!busy && state!=null,onClick={sourceOpen=true},modifier=Modifier.size(48.dp)) {Icon(Icons.Default.Add,"Agregar fotografías")}
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
                        .testTag("memory:${entry.id}").clickable(enabled=!busy) {viewing=entry.id}) {
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
        if(texts.isNotEmpty()) BottomModulesDialog(onDismissRequest={historical=null},properties=DialogProperties(usePlatformDefaultWidth=false)) {
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
    if(selected!=null && state!=null && visitingPerson==null) MemoryViewer(selected,entries,state!!,repository,people,attachments,onEntryChanged={viewing=it},onEdit={active,image->
        editing=MemoryEditRequest(active,state!!.photos(active.id),UUID.randomUUID().toString(),image)
    },onClose={viewing=null},onError={error=it},onOpenPerson={visitingPerson=it})
    visitingPerson?.let {id->PersonDetailDialog(id) {visitingPerson=null}}
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
        if(bitmap!=null) FramedAvatar(bitmap!!,com.r0ybt.arachn0de.domain.model.AvatarFraming(image.zoom,image.x,image.y),Modifier.fillMaxSize(),shape=androidx.compose.ui.graphics.RectangleShape,filterQuality=androidx.compose.ui.graphics.FilterQuality.High)
        else Icon(Icons.Default.Image,"Fotografía no disponible",Modifier.size(24.dp))
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable private fun MemoryViewer(initialEntry:PersonMemoryEntity,entries:List<PersonMemoryEntity>,state:PersonMemoryState,repository:PersonMemoryRepository,people:PersonRepository,attachments:AttachmentRepository,
    onEntryChanged:(String)->Unit,onEdit:(PersonMemoryEntity,String?)->Unit,onClose:()->Unit,onError:(String)->Unit,onOpenPerson:(String)->Unit) {
    val publications=rememberPagerState(initialPage=entries.indexOfFirst {it.id==initialEntry.id}.coerceAtLeast(0),pageCount={entries.size})
    val entry=entries.getOrNull(publications.currentPage) ?: return
    LaunchedEffect(entry.id) {onEntryChanged(entry.id)}
    val images=state.photos(entry.id)
    var photoIndex by remember(entry.id) {mutableIntStateOf(0)}
    val scope=rememberCoroutineScope()
    var busy by remember {mutableStateOf(false)}
    var deletion by remember {mutableStateOf(false)}
    var wholeEntry by remember {mutableStateOf(false)}
    val current=images.getOrNull(photoIndex)
    val exportDraft=rememberSaveable {UUID.randomUUID().toString()}
    var exportImage by rememberSaveable(entry.id,stateSaver=MemoryImageSaver) {mutableStateOf<PersonMemoryImageEntity?>(null)}
    val exportState by rememberUpdatedState(state)
    val download=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("image/*")) {uri->
        scope.launch {
            try {if(uri!=null) exportImage?.let {repository.export(it,exportState,uri)}}
            catch(c:CancellationException) {throw c} catch(e:Exception) {onError("No se pudo descargar la fotografía original: ${e.message.orEmpty()}")}
            finally {withContext(NonCancellable) {runCatching {attachments.discardDraft(exportDraft)};exportImage?.legacyPhotoId?.let {id->state.legacy.firstOrNull {it.id==id}?.let {runCatching {people.discardAvatar(it.file,exportDraft)}}}};exportImage=null;busy=false}
        }
    }
    BottomModulesDialog(onDismissRequest={if(!busy) onClose()},properties=DialogProperties(usePlatformDefaultWidth=false,decorFitsSystemWindows=false)) {
        Column(Modifier.fillMaxSize().background(Arachn0deColors.Background).safeDrawingPadding().padding(top=12.dp).testTag("memory-viewer")) {
            Row(Modifier.fillMaxWidth().padding(horizontal=12.dp),verticalAlignment=Alignment.CenterVertically) {
                IconButton(enabled=!busy,onClick=onClose,modifier=Modifier.size(48.dp)) {LucideIcon(Icons.Default.ArrowBack,"Volver",tint=androidx.compose.material3.LocalContentColor.current)}
                Spacer(Modifier.weight(1f));Text("${if(images.isEmpty()) 0 else photoIndex+1}/${images.size}")
            }
            VerticalPager(publications,Modifier.fillMaxWidth().padding(horizontal=12.dp).weight(1f).testTag("memory-publications"),userScrollEnabled=!busy,key={entries[it].id}) {publication->
                val item=entries[publication]
                val photos=state.photos(item.id)
                key(item.id,publications.currentPage) {
                    val photosPager=rememberPagerState(pageCount={maxOf(1,photos.size)})
                    val visiblePhoto=photosPager.currentPage
                    if(publication==publications.currentPage) SideEffect {photoIndex=visiblePhoto}
                    HorizontalPager(photosPager,Modifier.fillMaxSize().testTag(if(publication==publications.currentPage) "memory-carousel" else "memory-carousel:${item.id}"),userScrollEnabled=!busy,key={photos.getOrNull(it)?.id ?: "empty"}) {page->
                        val image=photos.getOrNull(page)
                        if(image==null) Box(Modifier.fillMaxSize(),contentAlignment=Alignment.Center) {Text("Sin fotografías")}
                        else {
                            val file=runCatching {repository.file(image,state)}.getOrNull()
                            val loaded by produceState<Pair<android.graphics.Bitmap?,Boolean>>(null to false,file?.absolutePath) {
                                value=withContext(Dispatchers.IO) {(if(file==null) null else runCatching {decodeAttachment(file)}.getOrNull()) to true}
                            }
                            ZoomableImage(loaded.first,loaded.second,"Fotografía ${page+1}",Modifier.fillMaxSize(),carousel=true)
                        }
                    }
                }
            }
            Column(Modifier.fillMaxWidth().padding(horizontal=12.dp).heightIn(max=160.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                Text(current?.date ?: entry.date ?: "Sin fecha",style=MaterialTheme.typography.bodySmall)
                if(entry.description.isNotBlank()) Text(entry.description)
                MemoryPeoplePresentation(entry,state,onOpenPerson)
            }
            ModularPanelSurface(Modifier.fillMaxWidth(),shadowElevation=0.dp) {
            Row(Modifier.fillMaxWidth().padding(4.dp),horizontalArrangement=Arrangement.spacedBy(6.dp)) {
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
                TextButton(enabled=!busy,onClick={onEdit(entry,current?.id)},modifier=Modifier.weight(1f).heightIn(min=48.dp)) {Column(horizontalAlignment=Alignment.CenterHorizontally) {Icon(Icons.Default.Edit,null,Modifier.size(20.dp));Text("Editar")}}
                TextButton(enabled=!busy,onClick={wholeEntry=images.isEmpty();deletion=true},modifier=Modifier.weight(1f).heightIn(min=48.dp)) {Column(horizontalAlignment=Alignment.CenterHorizontally) {Icon(Icons.Default.Delete,null,Modifier.size(20.dp));Text("Eliminar")}}
            }
        }
    }
    }
    if(deletion) AlertDialog(onDismissRequest={if(!busy) deletion=false},title={Text("Eliminar fotografía")},text={Column {
        Text("Las imágenes utilizadas por el avatar u otros elementos se conservan.")
        if(images.size>1) Row(verticalAlignment=Alignment.CenterVertically) {Checkbox(wholeEntry,{wholeEntry=it},enabled=!busy);Text("Eliminar la entrada completa (${images.size} fotografías)")}
        Text(if(wholeEntry || images.size<=1) "Se eliminará esta entrada." else "Se quitará la fotografía actual del carrusel.")
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
    var participants by rememberSaveable(request.draft,stateSaver=MemoryPeopleSaver) {mutableStateOf(state.people(request.entry.id))}
    var confirmedParticipants by rememberSaveable(request.draft,stateSaver=MemoryPeopleSaver) {mutableStateOf(state.people(request.entry.id))}
    var peopleVisible by rememberSaveable(request.draft) {mutableStateOf(request.entry.peopleVisible)}
    var confirmedPeopleVisible by rememberSaveable(request.draft) {mutableStateOf(request.entry.peopleVisible)}
    var confirmedDate by rememberSaveable(request.draft) {mutableStateOf(request.entry.date)}
    var confirmedDescription by rememberSaveable(request.draft) {mutableStateOf(request.entry.description)}
    var confirmedImages by rememberSaveable(request.draft,stateSaver=MemoryImagesSaver) {mutableStateOf(request.images)}
    var savedOnce by rememberSaveable(request.draft) {mutableStateOf(!request.creating)}
    var field by rememberSaveable(request.draft) {mutableStateOf<String?>(null)}
    var preview by rememberSaveable(request.draft) {mutableStateOf(false)}
    // Encadre remains part of the current field until its confirmation.
    var frames by rememberSaveable(request.draft) {mutableStateOf(emptyList<String>())}
    fun framed(photo:PersonPhotoEntity):PersonPhotoEntity {
        val frame=frames.chunked(4).firstOrNull {it[0]==photo.id} ?: return photo
        return photo.copy(zoom=frame[1].toFloat(),x=frame[2].toFloat(),y=frame[3].toFloat())
    }
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
    fun addPhotos(uris:List<android.net.Uri>,done:(Boolean)->Unit={},cameraPhoto:com.r0ybt.arachn0de.photos.CameraPhoto?=null) {if(uris.isNotEmpty() || cameraPhoto!=null) {
        busy=true;scope.launch {
            val batch=UUID.randomUUID().toString();var succeeded=false
            try {
                val files=cameraPhoto?.let {attachments.retainGallery(batch,setOf(it.attachment.id));listOf(it.attachment)} ?: uris.map {attachments.importPrivatePhoto(it,batch)}.distinctBy {it.id}
                attachments.retainGallery(request.draft,files.mapTo(hashSetOf()) {it.id})
                attachments.discardDraft(batch)
                val additions=files.filter {file->images.none {it.attachmentId==file.id}}.mapIndexed {i,file->PersonMemoryImageEntity(UUID.randomUUID().toString(),request.entry.id,file.id,null,images.size+i)}
                images=images+additions
                if(selected==null) selected=images.firstOrNull()?.id
                succeeded=true
            } catch(c:CancellationException) {withContext(NonCancellable) {attachments.discardDraft(batch)};throw c}
            catch(e:Exception) {withContext(NonCancellable) {attachments.discardDraft(batch)};onError(e.message ?: "No se pudieron agregar las fotografías.")}
            finally {busy=false;done(succeeded)}
        }
    }}
    val picker=rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia()) {uris->addPhotos(uris)}
    val camera=rememberPrivatePhotoCamera(!busy && ready,onError) {photo,done->addPhotos(emptyList(),done,photo)}
    val current=images.firstOrNull {it.id==selected} ?: images.firstOrNull()
    fun cancelField() {participants=confirmedParticipants;peopleVisible=confirmedPeopleVisible;date=confirmedDate;description=confirmedDescription;images=confirmedImages;frames=emptyList();field=null}
    BackHandler(enabled=field!=null) {if(!busy) cancelField()}
    ModularPanelDialog(showActions=field!=null,onDismissRequest={if(field!=null) {if(!busy) cancelField()} else cancel()},compact=true,plainBody=true,
        title={Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
            Text(field ?: if(savedOnce) "Editar entrada" else "Nueva entrada",Modifier.weight(1f))
            if(field==null) IconButton(enabled=!busy,onClick=::cancel) {LucideIcon(Icons.Default.Close,"Cerrar entrada",tint=androidx.compose.material3.LocalContentColor.current)}
        }},text={Column(Modifier.fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            if(field==null) ModularFieldMenu(listOfNotNull(
                ModularPanelOption("Fecha",Icons.Default.CalendarMonth,"memory-field-date",!busy) {field="Fecha"},
                ModularPanelOption("Descripción de la entrada",Icons.Default.Subject,"memory-field-description",!busy) {field="Descripción de la entrada"},
                ModularPanelOption("Personas",Icons.Default.Groups,"memory-field-people",!busy) {field="Personas"},
                ModularPanelOption("Fotografías",Icons.Default.Image,"memory-field-photos",!busy) {field="Fotografías"},
                current?.let {ModularPanelOption("Avatar principal",Icons.Default.Person,"memory-field-avatar",!busy) {field="Avatar principal"}}
            ),compact=true)
            if(field=="Personas") MemoryPeoplePanel(request.entry.id,participants,state.profiles,peopleVisible,!busy,{participants=it},{peopleVisible=it})
            if(field=="Fecha") {
                    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                        TextButton(enabled=!busy,onClick={dateTarget="entry"},modifier=Modifier.weight(1f).heightIn(min=48.dp)) {Icon(Icons.Default.CalendarMonth,null,Modifier.size(20.dp));Spacer(Modifier.width(6.dp));Text(date ?: "Seleccionar fecha")}
                        if(date!=null) IconButton(enabled=!busy,onClick={date=null},modifier=Modifier.size(48.dp)) {Icon(Icons.Default.Close,"Quitar fecha de la entrada")}
                    }
            }
            if(field=="Descripción de la entrada") ModularTextField(description,{if(it.length<=16000) description=it},"Descripción de la entrada",!busy)
            if(field=="Fotografías" || field=="Avatar principal") {
                    Text("Fotografías",style=MaterialTheme.typography.labelLarge)
                    images.forEachIndexed {index,image->
                        Card(Modifier.fillMaxWidth(),shape=MaterialTheme.shapes.medium,colors=CardDefaults.cardColors(containerColor=if(current?.id==image.id) Arachn0deColors.ControlSurface else Arachn0deColors.Surface)) {
                            Row(Modifier.fillMaxWidth().padding(8.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                                MemoryThumbnail(image,state,repository,Modifier.size(88.dp).clickable(onClickLabel="Seleccionar fotografía ${index+1}") {selected=image.id})
                                Column(Modifier.weight(1f)) {
                                    TextButton(enabled=!busy,onClick={selected=image.id;preview=true},modifier=Modifier.fillMaxWidth().heightIn(min=48.dp),contentPadding=PaddingValues(0.dp)) {Text("${index+1}${if(current?.id==image.id) " · Seleccionada" else ""}")}
                                    if(field=="Fotografías") Row {
                                        IconButton(enabled=!busy && index>0,onClick={images=images.toMutableList().also {java.util.Collections.swap(it,index,index-1)}.mapIndexed {i,row->row.copy(position=i)}},modifier=Modifier.size(48.dp)) {Icon(Icons.Default.ArrowUpward,"Mover antes")}
                                        IconButton(enabled=!busy && index<images.lastIndex,onClick={images=images.toMutableList().also {java.util.Collections.swap(it,index,index+1)}.mapIndexed {i,row->row.copy(position=i)}},modifier=Modifier.size(48.dp)) {Icon(Icons.Default.ArrowDownward,"Mover después")}
                                        IconButton(enabled=!busy,onClick={removing=image},modifier=Modifier.size(48.dp)) {Icon(Icons.Default.Delete,"Quitar fotografía")}
                                    }
                                }
                            }
                        }
                    }
                    if(field=="Fotografías") Row {
                        TextButton(enabled=!busy && ready,onClick={picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))}) {Text("Galería")}
                        TextButton(enabled=!busy && ready,onClick=camera) {Text("Cámara")}
                    }
                    if(current!=null) {
                        val legacy=state.legacy.firstOrNull {it.id==current.legacyPhotoId}
                        if(legacy!=null) TextButton(enabled=!busy && framing==null,onClick={if(!busy && framing==null) framing=framed(legacy)}) {Text("Ajustar encuadre")}
                        if(field=="Avatar principal") Text("Selecciona una fotografía y confirma para usarla como avatar.")
                    }
            }
        }},dismissButton={if(field!=null) PrimaryControlButton(enabled=!busy,onClick=::cancelField,modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)) {Text("Cancelar")}},
        confirmButton={if(field!=null) PrimaryControlButton(enabled=ready && !busy && (field!="Avatar principal" || current!=null),onClick={
            busy=true;scope.launch {
                try {
                    val framedState=state.copy(legacy=state.legacy.map(::framed))
                    if(field=="Fotografías" && images.isEmpty()) {
                        if(savedOnce) repository.deleteEntry(request.entry.id)
                        release();onClose()
                    } else if(field=="Avatar principal") {
                        state.legacy.filter {photo->frames.chunked(4).any {it[0]==photo.id}}.forEach {people.savePhoto(framed(it),request.draft)}
                        current?.let {repository.setAvatar(request.entry.personId,it,framedState)}
                    } else {
                        val latest=request.entry.copy(date=confirmedDate,description=confirmedDescription,peopleVisible=confirmedPeopleVisible)
                        val updated=when(field) {
                            "Personas"->latest.copy(peopleVisible=peopleVisible)
                            "Fecha"->latest.copy(date=date)
                            "Descripción de la entrada"->latest.copy(description=description)
                            else->latest
                        }
                        val savedParticipants=repository.save(updated,if(field=="Fotografías" || !savedOnce) images else confirmedImages,request.draft,participants=if(field=="Personas") participants else null)
                        if(field=="Fotografías") state.legacy.filter {photo->frames.chunked(4).any {it[0]==photo.id}}.forEach {people.savePhoto(framed(it),request.draft)}
                        confirmedParticipants=savedParticipants
                        if(field=="Personas") confirmedPeopleVisible=peopleVisible
                        savedOnce=true
                        confirmedDate=updated.date;confirmedDescription=updated.description;confirmedImages=images
                        attachments.retainGallery(request.draft,images.mapNotNullTo(hashSetOf()) {it.attachmentId})
                    }
                    participants=confirmedParticipants;peopleVisible=confirmedPeopleVisible;date=confirmedDate;description=confirmedDescription;images=confirmedImages;frames=emptyList();field=null
                } catch(c:CancellationException) {throw c} catch(e:Exception) {onError(e.message ?: "No se pudo guardar.")} finally {busy=false}
            }
        },modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)) {Text("Confirmar")}})
    if(preview && current!=null) BottomModulesDialog(onDismissRequest={preview=false},properties=DialogProperties(usePlatformDefaultWidth=false)) {
        Column(Modifier.fillMaxSize().background(Arachn0deColors.Background).safeDrawingPadding()) {
            IconButton(onClick={preview=false}) {LucideIcon(Icons.Default.Close,"Cerrar previsualización",tint=androidx.compose.material3.LocalContentColor.current)}
            val file=runCatching {repository.file(current,state)}.getOrNull()
            val loaded by produceState<Pair<android.graphics.Bitmap?,Boolean>>(null to false,file?.absolutePath) {
                value=withContext(Dispatchers.IO) {(if(file==null) null else runCatching {decodeAttachment(file)}.getOrNull()) to true}
            }
            ZoomableImage(loaded.first,loaded.second,"Previsualización",Modifier.fillMaxWidth().weight(1f))
        }
    }
    removing?.let {image->AlertDialog(onDismissRequest={removing=null},title={Text("Quitar fotografía")},text={Text(if(images.size==1) "Al confirmar se eliminará la entrada porque quedará sin fotografías. Los archivos compartidos se conservan." else "Se quitará esta fotografía del grupo al guardar. Los archivos compartidos se conservan.")},
        confirmButton={TextButton(modifier=Modifier.testTag("memory-confirm-remove"),enabled=!busy,onClick={
            images=images.filter {it.id!=image.id}.mapIndexed {i,row->row.copy(position=i)};selected=images.firstOrNull()?.id;removing=null
        }) {Text("Eliminar")}},dismissButton={TextButton(onClick={removing=null}) {Text("Cancelar")}})}
    framing?.let {photo->AvatarEditor(photo.file,com.r0ybt.arachn0de.domain.model.AvatarFraming(photo.zoom,photo.x,photo.y),enabled=!busy,onConfirm={frame->
        if(!busy && framing?.id==photo.id) {
            frames=frames.chunked(4).filterNot {it[0]==photo.id}.flatten()+listOf(photo.id,frame.zoom.toString(),frame.x.toString(),frame.y.toString())
            framing=null
        }
    },onCancel={if(!busy) framing=null})}
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
