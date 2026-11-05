package com.r0ybt.arachn0de.wireframe

import com.r0ybt.arachn0de.visual.*
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.saveable.Saver
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.r0ybt.arachn0de.Arachn0deApplication
import com.r0ybt.arachn0de.ui.ModularPanelDialog
import com.r0ybt.arachn0de.ui.theme.LocalAppearancePalette
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Input
import com.r0ybt.arachn0de.ui.ModularPanelSurface
import com.r0ybt.arachn0de.ui.ModularFieldMenu
import com.r0ybt.arachn0de.ui.ModularPanelOption
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import com.r0ybt.arachn0de.ui.GlobalChromeActions
import com.r0ybt.arachn0de.ui.NavigationHeading
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.stateIn
import java.util.UUID

private val DocumentSaver=Saver<WireDocument?,String>(save={it?.let(WireCodec::encode) ?: ""},restore={if(it.isEmpty()) null else WireCodec.decode(it)})

/** Independent static interface sketch library and direct manipulation editor. */
@Composable internal fun WireframeScreen(onBack:()->Unit) {
    val context=LocalContext.current
    val app=context.applicationContext as Arachn0deApplication
    val repository=remember(app.privateContext) {WireframeRepository(app.privateContext.getSharedPreferences(WireCodec.NAME,0))}
    val scope=rememberCoroutineScope()
    var message by remember {mutableStateOf<String?>(null)}
    val library by remember(repository) {repository.observe().catch {message=it.message}.stateIn(scope,kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(5000),emptyList())}.collectAsState()
    var document by rememberSaveable(stateSaver=DocumentSaver) {mutableStateOf<WireDocument?>(null)}
    var baseline by rememberSaveable {mutableStateOf<String?>(null)}
    var selected by rememberSaveable {mutableStateOf<String?>(null)}
    var busy by remember {mutableStateOf(false)}
    var naming by remember {mutableStateOf(false)}
    var rename by remember {mutableStateOf<String?>(null)}
    var name by remember {mutableStateOf("")}
    var format by remember {mutableStateOf(WireFormat.MOBILE)}
    var deletion by remember {mutableStateOf<Pair<WireDocument,String>?>(null)}
    var leave by remember {mutableStateOf(false)}
    var creatingFromScreen by rememberSaveable {mutableStateOf(false)}
    var pendingScreen by rememberSaveable {mutableStateOf<String?>(null)}
    var pendingScreenDocument by rememberSaveable {mutableStateOf<String?>(null)}
    var pendingScreenFormat by rememberSaveable {mutableStateOf<WireFormat?>(null)}
    var pendingScreenModified by rememberSaveable {mutableStateOf<Long?>(null)}
    var pendingImageTemplate by rememberSaveable {mutableStateOf("image")}
    var annotationId by rememberSaveable(document?.id) {mutableStateOf<String?>(null)}
    var placingAnnotation by rememberSaveable(document?.id) {mutableStateOf(false)}
    var showAnnotations by rememberSaveable {mutableStateOf(true)}
    var observedExport by rememberSaveable {mutableStateOf(false)}
    var sharedOutputs by rememberSaveable {mutableStateOf<ArrayList<String>?>(null)}
    var signature by rememberSaveable {mutableStateOf(false)}
    var exportOptions by rememberSaveable(stateSaver=DocumentSaver) {mutableStateOf<WireDocument?>(null)}
    var exportSignature by rememberSaveable {mutableStateOf(false)}
    var export by rememberSaveable(stateSaver=DocumentSaver) {mutableStateOf<WireDocument?>(null)}
    var templateLibrary by rememberSaveable {mutableStateOf(false)}
    var initialScreenChoice by rememberSaveable {mutableStateOf<String?>(null)}
    var templateGroup by rememberSaveable {mutableStateOf<String?>(null)}
    val galleryMode=rememberToolGalleryMode("wireframe")
    val history=remember(document?.id) {WireHistory()}
    fun operation(block:suspend ()->Unit) {if(!busy) scope.launch {busy=true;try {block()} catch(e:CancellationException) {throw e} catch(e:Exception) {message=e.message ?: "No se pudo completar la operación."} finally {busy=false}}}
    fun change(next:WireDocument) {val old=document ?: return;require(next.prune().assets.size<=16) {"Máximo 16 imágenes por dibujo."};creatingFromScreen=false;document=history.record(old,next.prune().preserveAnnotations(old)).copy(modified=maxOf(System.currentTimeMillis(),old.modified))}
    var route by rememberSaveable(document?.id) {mutableStateOf("Herramientas")}
    var propertyReturn by rememberSaveable(document?.id) {mutableStateOf("Herramientas")}
    var minimized by rememberSaveable(document?.id) {mutableStateOf(false)}
    var guides by rememberSaveable {mutableStateOf(true)}
    var magnetic by rememberSaveable {mutableStateOf(true)}
    var margins by rememberSaveable {mutableStateOf(false)}
    var device by rememberSaveable {mutableStateOf(false)}
    var composition by rememberSaveable {mutableStateOf(WireComposition.NONE)}
    var visualBar by rememberSaveable {mutableStateOf(true)}
    LaunchedEffect(document?.id) {if(creatingFromScreen && baseline==null && document?.elements?.isEmpty()==true) route="Pantallas"}
    fun insertScreen(screen:WireScreen) {
        val current=document ?: return
        val target=if(creatingFromScreen && baseline==null && current.elements.isEmpty()) current.copy(name=screen.label,format=screen.format) else current
        try {val next=target.insert(screen);change(next);selected=next.elements.firstOrNull {e->current.elements.none {it.id==e.id}}?.id;creatingFromScreen=false}
        catch(e:IllegalArgumentException) {message=e.message}
    }
    fun requestScreen(screen:WireScreen) {
        val current=document ?: return
        if(creatingFromScreen && baseline==null && current.elements.isEmpty()) insertScreen(screen)
        else if(current.format!=screen.format || current.elements.isNotEmpty()) {
            pendingScreen=screen.id;pendingScreenDocument=current.id;pendingScreenFormat=current.format;pendingScreenModified=current.modified
        } else insertScreen(screen)
    }
    LaunchedEffect(document?.id,initialScreenChoice) {
        initialScreenChoice?.let {id->WireScreens.items.firstOrNull {it.id==id}?.let {requestScreen(it);route="Herramientas"};initialScreenChoice=null}
    }
    val panelStates=rememberSaveableStateHolder()
    fun panelBack():Boolean {
        if(route=="Herramientas") return false
        route=when {route.startsWith("icon:")->"Propiedades";route=="Propiedades"->propertyReturn;route.startsWith("screen-detail:")->"screen-group:${WireScreens.items.first {it.id==route.substringAfter(':')}.group}";route.startsWith("screen-group:")->"Pantallas";route=="Pantallas"->"Añadir";route.startsWith("category:")->"Añadir";else->"Herramientas"}
        return true
    }
    fun selectForEditing(id:String?) {
        selected=id
        if(id!=null) {if(route!="Propiedades") propertyReturn=route;route="Propiedades";minimized=false}
    }
    fun back() {if(busy) return;if(templateLibrary) {templateLibrary=false;return};if(document!=null && WireCodec.encode(document!!)!=baseline) leave=true else if(document!=null) {document=null;selected=null} else onBack()}
    BackHandler {if(document==null || !panelBack()) back()}
    val imagePicker=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) {uri->if(uri!=null) operation {
        val original=document ?: return@operation
        val data=importWireImage(context,uri);val asset=UUID.randomUUID().toString()
        val next=original.insert(WireTemplates.items.first {it.id==pendingImageTemplate})
        change(next.copy(elements=next.elements.dropLast(1)+next.elements.last().copy(asset=asset,lockAspect=true),assets=original.assets+(asset to data)));selected=document?.elements?.lastOrNull()?.id
    }}
    val pngPicker=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("image/png")) {uri->val target=export;export=null;if(uri!=null && target!=null) operation {
        withContext(Dispatchers.IO) {try {context.contentResolver.openOutputStream(uri,"wt")!!.use {out->WireframeRenderer(context,target.assets).use {it.png(target,exportSignature,out)}}} catch(e:Exception) {runCatching {android.provider.DocumentsContract.deleteDocument(context.contentResolver,uri)};throw e}}
        message="PNG exportado"
    }}
    val observedPicker=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) {uri->
        val target=export;export=null
        if(uri!=null && target!=null) operation {sharedOutputs=ArrayList(wireExportObserved(context,target,exportSignature,uri).map {it.toString()});message="PNG con observaciones exportados"}
    }
    LaunchedEffect(route) {if(route!="Anotaciones") placingAnnotation=false}
    val d=document
    val renderer=remember(d?.assets) {WireframeRenderer(context,d?.assets.orEmpty())}
    DisposableEffect(renderer) {onDispose {renderer.close()}}
    BoxWithConstraints(Modifier.fillMaxSize().imePadding()) {
    val bodyLimit=(maxHeight*.43f).coerceAtMost(280.dp)
    Column(Modifier.fillMaxSize()) {
        if(d==null) ToolLibraryHeader("Wireframe",onBack={back()})
        else Row(Modifier.fillMaxWidth().padding(horizontal=12.dp),horizontalArrangement=Arrangement.SpaceBetween) {
            GlobalChromeActions(enabled=!busy,leading=true)
            NavigationHeading(d?.name ?: "Wireframe",Modifier.weight(1f).padding(vertical=14.dp))
            GlobalChromeActions(enabled=!busy)
            if(d!=null) WireVisualBarButton(visualBar,!busy) {visualBar=!visualBar}
            OutlinedButton(onClick={back()},enabled=!busy) {Icon(Icons.AutoMirrored.Filled.ArrowBack,null,Modifier.size(20.dp));Spacer(Modifier.width(4.dp));Text("Volver")}
        }
        if(d==null) {
            if(templateLibrary) {
                val screens=WireScreens.items.filter {templateGroup==null || it.group==templateGroup}
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal=12.dp),horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                    (listOf<String?>(null)+WireScreens.items.map {it.group}.distinct()).forEach {group->FilterChip(templateGroup==group,{templateGroup=group},label={Text(group ?: "Todas")})}
                }
                ToolDocumentGallery(screens.map {screen->
                    ToolLibraryItem(screen.id,screen.label,screen.group,open={
                        templateLibrary=false;creatingFromScreen=true;baseline=null;selected=null
                        document=WireDocument(name="Nuevo dibujo");initialScreenChoice=screen.id
                    },preview={modifier->ToolThumbnail("wire-template:${screen.id}",screen.label,modifier) {
                        WireframeRenderer(context,emptyMap()).use {it.bitmap(screen.preview(),minOf(240,(240f*screen.format.width/screen.format.height).toInt()).coerceAtLeast(1))}
                    }})
                },true,{},Modifier.weight(1f),title="Plantillas",onBack={templateLibrary=false},layoutToggle=false)
            } else {
                ToolDocumentGallery(library.map {entry->
                    ToolLibraryItem(entry.id,entry.name,"${entry.format.label} · ${entry.elements.size} elementos",open={if(!busy)operation {val (loaded,raw)=repository.load(entry.id);creatingFromScreen=false;document=loaded;baseline=raw;selected=null}},
                        preview={modifier->ToolThumbnail("wire:${entry.id}:${entry.modified}:${entry.hashCode()}",entry.name,modifier) {
                            WireframeRenderer(context,entry.assets).use {it.bitmap(entry,minOf(240,(240f*entry.format.width/entry.format.height).toInt()).coerceAtLeast(1))}
                        }},actions=listOf(
                            "Renombrar" to {if(!busy) {name=entry.name;rename=entry.id;naming=true}},
                            "Duplicar" to {operation {repository.duplicate(entry.id)}},
                            "Exportar PNG" to {if(!busy) {observedExport=false;exportOptions=entry}},
                            "Eliminar" to {operation {deletion=repository.load(entry.id)}}))
                },galleryMode.first,galleryMode.second,Modifier.weight(1f))
                ToolCreationActions(!busy,onBlank={name="Nuevo dibujo";rename=null;naming=true},onTemplate={templateLibrary=true})
            }
        } else {
            Box(Modifier.weight(1f).fillMaxWidth()) {WireCanvas(d,renderer,selected,!busy,{selectForEditing(it)},{change(it)},guides,magnetic,margins,device,visualBar,composition=composition,onCompositionChange={composition=it},showAnnotations=showAnnotations,annotationMode=route=="Anotaciones",placingAnnotation=placingAnnotation,selectedAnnotation=annotationId,
                onAnnotationSelect={annotationId=it},onAnnotationPlace={point->
                    val a=WireAnnotation(x=point.x.coerceIn(0f,1f),y=point.y.coerceIn(0f,1f))
                    change(d.copy(annotations=d.annotations+a));annotationId=a.id;placingAnnotation=false
                }) {tool->
                when(tool) {WireVisualTool.GUIDES->guides=!guides;WireVisualTool.MARGINS->margins=!margins;WireVisualTool.DEVICE->device=!device;WireVisualTool.MAGNET->magnetic=!magnetic}
            }}
            val palette=LocalAppearancePalette.current
            ModularPanelSurface(Modifier.testTag("wireframe-panel"),shadowElevation=0.dp) {
                Column(Modifier.fillMaxWidth()) {
                    val foldThreshold=with(androidx.compose.ui.platform.LocalDensity.current) {24.dp.toPx()}
                    Row(Modifier.fillMaxWidth().pointerInput(minimized) {
                        var distance=0f
                        detectVerticalDragGestures(onDragStart={distance=0f},onDragEnd={if(distance>foldThreshold) minimized=true else if(distance < -foldThreshold) minimized=false},onVerticalDrag={event,amount->event.consume();distance+=amount})
                    },verticalAlignment=Alignment.CenterVertically) {
                        if(route!="Herramientas") IconButton(onClick={panelBack()},enabled=!busy) {Icon(Icons.AutoMirrored.Filled.ArrowBack,"Volver al nivel anterior",Modifier.size(20.dp))}
                        else Spacer(Modifier.width(12.dp))
                        Text(if(route.startsWith("icon:")) "Iconos" else if(route.startsWith("screen-detail:")) "Molde" else if(route.startsWith("category:") || route.startsWith("screen-group:")) route.substringAfter(':') else route,Modifier.weight(1f),style=MaterialTheme.typography.labelLarge,maxLines=2,overflow=TextOverflow.Ellipsis)
                        IconButton(onClick={document=history.undo(d)},enabled=!busy && history.canUndo) {Icon(Icons.AutoMirrored.Filled.Undo,"Deshacer",Modifier.size(20.dp))}
                        IconButton(onClick={document=history.redo(d)},enabled=!busy && history.canRedo) {Icon(Icons.AutoMirrored.Filled.Redo,"Rehacer",Modifier.size(20.dp))}
                        IconButton(onClick={minimized=!minimized},modifier=Modifier.testTag("wireframe-panel-fold")) {Icon(if(minimized) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,if(minimized) "Expandir panel de Wireframe" else "Minimizar panel de Wireframe")}
                    }
                    if(!minimized) HorizontalDivider(color=palette.outline,thickness=.5.dp)
                    if(!minimized) panelStates.SaveableStateProvider("${d.id}:$route") {
                        if(route=="Archivo") Column(Modifier.heightIn(max=bodyLimit).verticalScroll(rememberScrollState()).padding(horizontal=8.dp)) {
                            ActionRow {
                                ToolActionButton(if(WireCodec.encode(d)==baseline) "Guardado" else "Guardar",Icons.Default.Save,enabled=!busy) {operation {baseline=repository.save(document!!,baseline);message="Dibujo guardado"}}
                                ToolActionButton("Guardar copia",Icons.Default.ContentCopy,enabled=!busy) {operation {val now=System.currentTimeMillis();val copy=document!!.copy(id=UUID.randomUUID().toString(),name=document!!.name.take(90)+" · copia",created=now,modified=now);baseline=repository.save(copy,null);document=copy;message="Copia guardada"}}
                                ToolActionButton("Exportar PNG",Icons.Default.IosShare,enabled=!busy) {observedExport=false;exportOptions=d}
                            }
                        } else if(route=="Anotaciones") WireAnnotationControls(d,annotationId,selected,showAnnotations,placingAnnotation,!busy,bodyLimit,
                            {annotationId=it},{showAnnotations=it},{placingAnnotation=!placingAnnotation;showAnnotations=true},{change(it)})
                        else WireControls(d,selected,{selectForEditing(it)},{change(it)},{template->pendingImageTemplate=template.id;imagePicker.launch(arrayOf("image/*"))},route,{next->if(next=="Propiedades") propertyReturn=route;route=next},bodyLimit,!busy,{requestScreen(it)}) {selected=it}
                    }
                }
            }

        }
        if(busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        message?.let {text->LaunchedEffect(text) {delay(5000);if(message==text) message=null};Text(text,Modifier.fillMaxWidth().padding(10.dp),color=MaterialTheme.colorScheme.primary)}
    }
    }
    pendingScreen?.let {id->WireScreens.items.find {it.id==id}?.let {screen->
        val current=document
        val incompatible=current?.format!=screen.format
        ModularPanelDialog(onDismissRequest={pendingScreen=null},title={Text(if(incompatible) "Formato de la plantilla" else "Insertar pantalla")},text={
            Text(if(incompatible) "«${screen.label}» requiere formato ${screen.format.label}. Elige ese formato en Lienzo y vuelve al catálogo. El dibujo no se modifica automáticamente."
            else "Se añadirán ${screen.elements().size} elementos sobre el contenido actual. No se eliminará ningún elemento. La inserción se puede deshacer de una vez.")
        },confirmButton={TextButton(onClick={
            pendingScreen=null
            if(incompatible) route="Lienzo"
            else if(current.id==pendingScreenDocument && current.format==pendingScreenFormat && current.modified==pendingScreenModified) insertScreen(screen)
            else message="El dibujo cambió. Vuelve a elegir el molde."
        },enabled=!busy) {Text(if(incompatible) "Elegir formato" else "Insertar de todos modos")}},dismissButton={TextButton(onClick={pendingScreen=null}) {Text("Cancelar")}})
    }}
    exportOptions?.let {target->
        ModularPanelDialog(onDismissRequest={exportOptions=null},compact=true,title={Text("Exportar PNG")},text={
            Column {
                if(target.annotations.isNotEmpty()) {
                    Row(Modifier.clickable {observedExport=false},verticalAlignment=Alignment.CenterVertically) {RadioButton(!observedExport,null);Text("Exportar normal")}
                    Row(Modifier.clickable {observedExport=true},verticalAlignment=Alignment.CenterVertically) {RadioButton(observedExport,null);Text("Exportar con observaciones")}
                    if(observedExport) Text("Elige una carpeta para guardar el diseño con marcadores y las hojas de observaciones. Después podrás compartirlos.",style=MaterialTheme.typography.bodySmall)
                }
                Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                    Checkbox(signature,{signature=it},modifier=Modifier.testTag("wire-export-signature"))
                    Text("Firma fuera del lienzo")
                }
            }
        },confirmButton={TextButton(onClick={export=target;exportSignature=signature;exportOptions=null;if(observedExport && target.annotations.isNotEmpty()) observedPicker.launch(null) else pngPicker.launch("wireframe.png")},enabled=!busy) {Text("Exportar")}},dismissButton={TextButton(onClick={exportOptions=null}) {Text("Cancelar")}})
    }
    sharedOutputs?.let {files->ModularPanelDialog(onDismissRequest={sharedOutputs=null},title={Text("PNG exportados")},text={Text("Se guardaron ${files.size} imágenes en la carpeta elegida.")},confirmButton={TextButton(onClick={operation {context.startActivity(android.content.Intent.createChooser(wireShareIntent(files.map(android.net.Uri::parse)),"Compartir Wireframe"))}},enabled=!busy) {Text("Compartir")}},dismissButton={TextButton(onClick={sharedOutputs=null}) {Text("Cerrar")}})}
    if(naming) ModularPanelDialog(onDismissRequest={naming=false},title={Text(if(rename==null) "Crear dibujo" else "Renombrar")},text={Column {
        OutlinedTextField(name,{name=it.take(100)},label={Text("Nombre")},singleLine=true)
        if(rename==null) ActionRow {WireFormat.entries.forEach {f->FilterChip(format==f,{format=f},label={Text(f.label)})}}
    }},confirmButton={TextButton(onClick={operation {if(rename!=null) repository.rename(rename!!,name) else {val new=repository.create(name,format);creatingFromScreen=false;document=new;baseline=WireCodec.encode(new);selected=null};naming=false}},enabled=name.isNotBlank() && !busy) {Text("Aceptar")}},dismissButton={TextButton(onClick={naming=false}) {Text("Cancelar")}})
    deletion?.let {(target,raw)->ModularPanelDialog(onDismissRequest={deletion=null},title={Text("Eliminar dibujo")},text={Text("Se eliminará «${target.name}» y sus imágenes. Esta acción es definitiva.")},confirmButton={TextButton(onClick={operation {repository.delete(target.id,raw);deletion=null}},enabled=!busy) {Text("Eliminar")}},dismissButton={TextButton(onClick={deletion=null}) {Text("Cancelar")}})}
    if(leave) ModularPanelDialog(onDismissRequest={leave=false},title={Text("Cambios sin guardar")},text={Text("Guarda el dibujo antes de volver a la biblioteca.")},confirmButton={TextButton(onClick={operation {baseline=repository.save(document!!,baseline);leave=false;document=null}},enabled=!busy) {Text("Guardar")}},dismissButton={TextButton(onClick={leave=false;document=null;selected=null},enabled=!busy) {Text("Descartar")}})
}

@Composable private fun ActionRow(content:@Composable RowScope.()->Unit) {Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(4.dp),content=content)}

@Composable private fun WireControls(d:WireDocument,selected:String?,select:(String?)->Unit,change:(WireDocument)->Unit,import:(WireTemplate)->Unit,
    route:String,navigate:(String)->Unit,limit:androidx.compose.ui.unit.Dp,enabled:Boolean,insertScreen:(WireScreen)->Unit,selectInserted:(String?)->Unit) {
    val e=d.elements.find {it.id==selected}
    fun update(next:WireElement) {change(d.copy(elements=d.elements.map {if(it.id==next.id) next.bounded().copy(icons=next.icons.filterKeys {slot->slot in wireIconDefaults(next).indices}) else it}))}
    when {
        route=="Herramientas"->BoxWithConstraints(Modifier.fillMaxWidth().padding(horizontal=8.dp,vertical=4.dp)) {
            val fontScale=androidx.compose.ui.platform.LocalDensity.current.fontScale
            val width=maxOf(maxWidth,384.dp*fontScale)
            val tools=listOf("Añadir" to Icons.Default.Add,"Elementos" to Icons.AutoMirrored.Filled.List,"Propiedades" to Icons.Default.Tune,"Lienzo" to Icons.Default.Crop,"Archivo" to Icons.Default.Save,"Anotaciones" to Icons.Default.Comment)
            Row(Modifier.horizontalScroll(rememberScrollState())) {
                tools.forEach {(label,icon)->ToolLabeledButton(label,enabled=enabled,tag="wire-tool:$label",onClick={navigate(label)}) {Icon(icon,null,Modifier.size(22.dp))}}
            }
        }
        route=="Añadir"->Column(Modifier.heightIn(max=limit).verticalScroll(rememberScrollState()).padding(horizontal=8.dp,vertical=4.dp)) {
            ModularFieldMenu((listOf("Pantallas")+WireTemplates.items.map {it.category}.distinct()).map {category->
                ModularPanelOption(category,when(category) {"Pantallas"->Icons.Default.Dashboard;"Botones"->Icons.Default.SmartButton;"Entradas"->Icons.AutoMirrored.Filled.Input;"Navbar"->Icons.Default.Menu;"Contenedores"->Icons.Default.ViewAgenda;"Selección"->Icons.Default.CheckBox;"Ventanas y estados"->Icons.Default.WebAsset;"Recursos visuales"->Icons.Default.Image;else->Icons.Default.Category},"wire-category:$category",enabled) {navigate(if(category=="Pantallas") "Pantallas" else "category:$category")}
            },columns=2,compact=true)
        }
        route=="Pantallas"->Column(Modifier.heightIn(max=limit).verticalScroll(rememberScrollState()).padding(horizontal=8.dp,vertical=4.dp)) {
            ModularFieldMenu(WireScreens.items.map {it.group}.distinct().map {group->
                ModularPanelOption(group,Icons.Default.Dashboard,"wire-screen-group:$group",enabled) {navigate("screen-group:$group")}
            },columns=2,compact=true)
        }
        route.startsWith("screen-group:")->BoxWithConstraints(Modifier.fillMaxWidth().heightIn(max=limit)) {
            val cardWidth=maxOf((maxWidth-16.dp)/3,112.dp*androidx.compose.ui.platform.LocalDensity.current.fontScale)
            LazyRow(contentPadding=PaddingValues(4.dp),horizontalArrangement=Arrangement.spacedBy(4.dp),modifier=Modifier.testTag("wireframe-screens")) {
                items(WireScreens.items.filter {it.group==route.substringAfter(':')},key={it.id}) {screen->
                    OutlinedCard(onClick={navigate("screen-detail:${screen.id}")},enabled=enabled,modifier=Modifier.width(cardWidth).testTag("wire-screen:${screen.id}")) {
                        Column(Modifier.padding(4.dp),horizontalAlignment=Alignment.CenterHorizontally) {
                            WireScreenThumbnail(screen,92.dp)
                            Text(screen.label,Modifier.fillMaxWidth().heightIn(min=36.dp),style=MaterialTheme.typography.labelSmall,maxLines=2,overflow=TextOverflow.Ellipsis)
                            Text(screen.format.label,style=MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }
        route.startsWith("screen-detail:")->WireScreens.items.find {it.id==route.substringAfter(':')}?.let {screen->
            Row(Modifier.fillMaxWidth().heightIn(max=limit).padding(8.dp),verticalAlignment=Alignment.CenterVertically) {
                Box(Modifier.width(100.dp)) {WireScreenThumbnail(screen,120.dp)}
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                    Text(screen.label,style=MaterialTheme.typography.titleSmall)
                    Text("${screen.format.label} · ${screen.elements().size} elementos editables",style=MaterialTheme.typography.bodySmall)
                    TextButton(onClick={insertScreen(screen)},enabled=enabled) {Text("Insertar")}
                }
            }
        }
        route.startsWith("category:")-> {
            val resources=WireTemplates.items.filter {it.category==route.substringAfter(':')}
            Column(Modifier.heightIn(max=limit).verticalScroll(rememberScrollState())) {
                Text("${resources.size} recursos",Modifier.padding(horizontal=8.dp),style=MaterialTheme.typography.labelSmall)
                BoxWithConstraints(Modifier.fillMaxWidth()) {
                    val fontScale=androidx.compose.ui.platform.LocalDensity.current.fontScale
                    val cardWidth=maxOf((maxWidth-20.dp)/4,80.dp*fontScale)
                    LazyRow(contentPadding=PaddingValues(horizontal=4.dp),horizontalArrangement=Arrangement.spacedBy(4.dp),modifier=Modifier.testTag("wireframe-resources")) {
                        items(resources,key={it.id}) {template->
                            OutlinedCard(onClick={if(template.kind==WireKind.IMAGE) import(template) else {val next=d.insert(template);change(next);selectInserted(next.elements.last().id)}},enabled=enabled && d.elements.size<128,modifier=Modifier.width(cardWidth).testTag("wire-resource:${template.id}")) {
                                Column(Modifier.padding(4.dp),horizontalAlignment=Alignment.CenterHorizontally) {
                                    WireTemplateThumbnail(template)
                                    Text(template.label,Modifier.fillMaxWidth().heightIn(min=36.dp*fontScale),style=MaterialTheme.typography.labelSmall,maxLines=2,overflow=TextOverflow.Ellipsis,textAlign=androidx.compose.ui.text.style.TextAlign.Center)
                                }
                            }
                        }
                    }
                }
            }
        }
        route.startsWith("icon:")-> {
            val parts=route.split(':');val element=d.elements.find {it.id==parts.getOrNull(1)};val slot=parts.getOrNull(2)?.toIntOrNull()
            if(element!=null && slot!=null) WireIconPicker(element,slot,enabled) {update(it)}
        }
        route=="Elementos"->LazyColumn(Modifier.fillMaxWidth().heightIn(max=limit)) {
            if(d.elements.isEmpty()) item {Text("Sin elementos",Modifier.padding(12.dp))}
            items(d.elements.asReversed(),key={it.id}) {item->
                val label=WireTemplates.items.find {it.id==item.template}?.label ?: item.kind.label
                Surface(color=if(item.id==selected) MaterialTheme.colorScheme.secondaryContainer else androidx.compose.ui.graphics.Color.Transparent) {
                    TextButton(onClick={select(item.id)},enabled=enabled,modifier=Modifier.fillMaxWidth(),contentPadding=PaddingValues(horizontal=12.dp,vertical=4.dp)) {Text("$label · ${item.text.take(24)}",Modifier.fillMaxWidth(),maxLines=1,overflow=TextOverflow.Ellipsis)}
                }
            }
        }
        else->Column(Modifier.fillMaxWidth().heightIn(max=limit).verticalScroll(rememberScrollState()).padding(horizontal=8.dp),verticalArrangement=Arrangement.spacedBy(4.dp)) {
            if(route=="Lienzo") {
                ActionRow {WireFormat.entries.forEach {f->FilterChip(d.format==f,{if(enabled) change(d.copy(format=f))},enabled=enabled,label={Text(f.label)})}}
                WireColorControl("Fondo",d.background,enabled,presets=WireBackgroundPalette) {change(d.copy(background=it))}
                Text("${d.format.width} × ${d.format.height} px · dibujo estático",style=MaterialTheme.typography.bodySmall)
            } else if(e!=null) key(e.id) {
                Text(WireTemplates.items.find {it.id==e.template}?.label ?: e.kind.label,style=MaterialTheme.typography.titleSmall)
                if(e.editableText) OutlinedTextField(e.text,{update(e.copy(text=it.take(4000)))},enabled=enabled,label={Text("Texto")},modifier=Modifier.fillMaxWidth())
                WireIconSlots(e,enabled) {slot->navigate("icon:${e.id}:$slot")}
                TextButton(onClick={navigate("Anotaciones")},enabled=enabled) {Text("Anotar elemento")}
                if(e.fillEditable) WireColorControl("Relleno",e.fill,enabled) {update(e.copy(fill=it))}
                if(e.kind!=WireKind.IMAGE && (e.kind.textCapable || e.kind==WireKind.ICON || e.template!=null && !e.template.startsWith("shape_"))) WireColorControl("Texto / icono",e.ink,enabled) {update(e.copy(ink=it))}
                ActionRow {
                    TextButton(onClick={val copy=e.copy(id=UUID.randomUUID().toString(),x=e.x+.02f,y=e.y+.02f).bounded();change(d.copy(elements=d.elements+copy));selectInserted(copy.id)},enabled=enabled && d.elements.size<128) {Text("Duplicar")}
                    TextButton(onClick={change(d.copy(elements=d.elements.filterNot {it.id==e.id}+e))},enabled=enabled) {Text("Al frente")}
                    TextButton(onClick={change(d.copy(elements=listOf(e)+d.elements.filterNot {it.id==e.id}))},enabled=enabled) {Text("Al fondo")}
                    TextButton(onClick={change(d.copy(elements=d.elements.filterNot {it.id==e.id}));selectInserted(null)},enabled=enabled) {Text("Eliminar")}
                }
            }
        }
    }
}

/** Only offer fill where the shared renderer actually draws a fill channel. */
private val WireElement.fillEditable:Boolean get() {
    if(kind==WireKind.IMAGE || kind==WireKind.ICON || kind.textOnly) return false
    if(template==null || template.startsWith("shape_")) return true
    val category=WireTemplates.items.firstOrNull {it.id==template}?.category
    return when(category) {
        "Entradas"->template=="filled"
        "Selección"->template in setOf("radio_off","radio_on","switch_off","switch_on","slider","segmented")
        else->template !in setOf("spinner","divider","separator")
    }
}

@Composable private fun WireThumbnail(document:WireDocument) {
    val context=LocalContext.current
    val bitmap by produceState<android.graphics.Bitmap?>(null,document) {
        value=withContext(Dispatchers.IO) {WireframeRenderer(context,document.assets).use {it.bitmap(document,160)}}
    }
    bitmap?.let {Image(it.asImageBitmap(),"Vista previa",Modifier.fillMaxWidth().height(96.dp))}
}

@Composable private fun WireTemplateThumbnail(template:WireTemplate) {
    val context=LocalContext.current
    val bitmap=remember(template) {
        val e=if(template.kind==WireKind.IMAGE) WireTemplates.items.first {it.id=="placeholder"}.element(WireFormat.MOBILE).copy(w=template.width/1080f,h=template.height/2340f) else template.element(WireFormat.MOBILE)
        val doc=WireDocument(elements=listOf(e.copy(x=0f,y=0f)))
        val bitmap=android.graphics.Bitmap.createBitmap(300,160,android.graphics.Bitmap.Config.ARGB_8888)
        val canvas=android.graphics.Canvas(bitmap)
        val scale=minOf(280f/template.width,140f/template.height)
        canvas.translate((300-template.width*scale)/2,(160-template.height*scale)/2)
        canvas.scale(scale,scale)
        WireframeRenderer(context,emptyMap()).use {it.draw(canvas,doc)}
        bitmap
    }
    Image(bitmap.asImageBitmap(),template.label,Modifier.fillMaxWidth().height(52.dp))
}

/** Uses the same editable document and renderer as the canvas and PNG export. */
@Composable private fun WireScreenThumbnail(screen:WireScreen,height:androidx.compose.ui.unit.Dp) {
    val context=LocalContext.current
    val bitmap=remember(screen.id) {WireframeRenderer(context,emptyMap()).use {it.bitmap(screen.preview(),320)}}
    Image(bitmap.asImageBitmap(),screen.label,Modifier.fillMaxWidth().height(height))
}
