package com.r0ybt.arachn0de.diagrams

import com.r0ybt.arachn0de.visual.*
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.Grid4x4
import androidx.compose.material.icons.filled.FitScreen
import androidx.compose.material.icons.filled.OpenWith
import androidx.compose.material.icons.filled.Add
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.Saver
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.*
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.asImageBitmap
import com.r0ybt.arachn0de.Arachn0deApplication
import com.r0ybt.arachn0de.ui.theme.LocalAppearancePalette
import com.r0ybt.arachn0de.ui.theme.appearancePaint
import com.r0ybt.arachn0de.ui.theme.role
import com.r0ybt.arachn0de.ui.ModularPanelSurface
import com.r0ybt.arachn0de.ui.GlobalChromeActions
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.catch

private val DiagramSaver=Saver<DiagramDocument?,String>(save={it?.let(DiagramCodec::encode) ?: ""},restore={if(it.isEmpty())null else DiagramCodec.decode(it)})
@Composable internal fun DiagramScreen(onBack:()->Unit) {
    val configuration=LocalConfiguration.current
    val portrait=configuration.screenHeightDp>=configuration.screenWidthDp
    val context=LocalContext.current;val app=context.applicationContext as Arachn0deApplication
    val repository=remember(app.privateContext) {DiagramRepository(app.privateContext.getSharedPreferences(DiagramCodec.NAME,0))}
    var error by remember {mutableStateOf<String?>(null)}
    LaunchedEffect(app.privateContext) {
        try {
            val pending=DiagramExportJournal(context,app.privateContext).recover()
            if(pending.isNotEmpty())error="No se pudo limpiar una exportación interrumpida. Revisa el acceso a la carpeta elegida. ${pending.first()}"
        } catch(c:CancellationException) {throw c} catch(e:Exception) {error="No se pudo revisar la limpieza de exportaciones: ${e.message}"}
    }
    val library by remember(repository) {repository.observe().catch {error=it.message}}.collectAsState(DiagramLibrary())
    var doc by rememberSaveable(stateSaver=DiagramSaver) {mutableStateOf<DiagramDocument?>(null)}
    var baseline by rememberSaveable {mutableStateOf<String?>(null)}
    val scope=rememberCoroutineScope();var busy by remember {mutableStateOf(false)}
    fun perform(action:suspend ()->Unit) {if(busy)return;busy=true;scope.launch {try {action()} catch(c:CancellationException) {throw c} catch(e:Exception) {error=e.message ?: "No se pudo completar la operación."} finally {busy=false}}}
    var templateLibrary by rememberSaveable {mutableStateOf(false)}
    var modelCategory by rememberSaveable {mutableStateOf<String?>(null)}
    var assetLibrary by remember {mutableStateOf(false)}
    val galleryMode=rememberToolGalleryMode("diagrams")
    var deleting by remember {mutableStateOf<Pair<DiagramDocument,String>?>(null)}
    if(doc==null) {
        val palette=com.r0ybt.arachn0de.ui.theme.LocalAppearancePalette.current
        Surface(Modifier.fillMaxSize(),color=palette.background,contentColor=palette.text) {
        Column(Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal=12.dp).testTag("diagram-library")) {
            ToolLibraryHeader("Diagramas",onBack={if(templateLibrary)templateLibrary=false else onBack()})
            if(templateLibrary) {
                val models=remember(portrait) {DiagramModels.items+listOf("Flujo básico","Arquitectura de software","Red de dispositivos","Mapa conceptual","Explicación libre").mapIndexed {i,name->
                    DiagramModel("basic:${i+1}",name,"Desarrollo","Ejemplo básico anterior") {diagramTemplate(i+1,portrait)}
                }}
                val previews=remember(models) {models.associate {it.id to it.create()}}
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                    (listOf<String?>(null)+DiagramModels.categories).forEach {category->FilterChip(modelCategory==category,{modelCategory=category},label={Text(category ?: "Todos")},modifier=Modifier.testTag("diagram-model-category:${category ?: "all"}"))}
                }
                ToolDocumentGallery(models.filter {modelCategory==null||it.category==modelCategory}.map {model->
                    ToolLibraryItem("model:${model.id}",model.name,model.description,open={if(!busy) {doc=openDiagramModel(model,(configuration.screenWidthDp-56).coerceAtLeast(240).toFloat(),(configuration.screenHeightDp-250).coerceAtLeast(200).toFloat());baseline=null;templateLibrary=false}},
                        preview={modifier->ToolThumbnail("diagram-model-v2:${model.id}:$portrait",model.name,modifier) {diagramLibraryBitmap(previews.getValue(model.id),emptyMap())}})
                },true,{},Modifier.weight(1f),title="Plantillas",onBack={templateLibrary=false},layoutToggle=false,subtitleLines=2,titleLines=2)
                OutlinedButton(onClick={doc=diagramTemplate(0,portrait=portrait);baseline=null;templateLibrary=false},enabled=!busy,modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)) {Icon(androidx.compose.material.icons.Icons.Default.Add,null,Modifier.size(20.dp));Spacer(Modifier.width(6.dp));Text("Lienzo vacío")}
            } else {
                ToolDocumentGallery(library.documents.map {entry->
                    ToolLibraryItem(entry.id,entry.name,"${entry.nodes.size} nodos · ${entry.edges.size} conexiones",open={if(!busy)perform {val loaded=repository.load(entry.id);doc=loaded.first;baseline=loaded.second}},
                        preview={modifier->ToolThumbnail("diagram:${entry.id}:${entry.hashCode()}:${library.assets.hashCode()}",entry.name,modifier) {diagramLibraryBitmap(entry,library.assets)}},
                        actions=listOf("Guardar copia" to {perform {val loaded=repository.load(entry.id).first;repository.save(loaded.copy(id=diagramId(),name=loaded.name.take(90)+" · copia"),null)}},
                            "Eliminar" to {perform {deleting=repository.load(entry.id)}}))
                },galleryMode.first,galleryMode.second,Modifier.weight(1f))
                OutlinedButton(onClick={assetLibrary=true},enabled=!busy,modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)) {Icon(androidx.compose.material.icons.Icons.Default.Image,null,Modifier.size(20.dp));Spacer(Modifier.width(6.dp));Text("Biblioteca de iconos (${library.assets.size}/48)")}
                ToolCreationActions(!busy,onBlank={doc=diagramTemplate(0,portrait=portrait);baseline=null},onTemplate={templateLibrary=true})
            }
        }
        }

    } else DiagramEditor(requireNotNull(doc),baseline,library.assets,library.colors,repository,busy,{doc=it},{baseline=it},{doc=null;baseline=null},::perform)
    if(doc==null) BackHandler {if(templateLibrary)templateLibrary=false else onBack()}
    if(assetLibrary) com.r0ybt.arachn0de.ui.ModularPanelDialog(onDismissRequest={assetLibrary=false},title={Text("Biblioteca de iconos")},text={
        Column {
            if(library.assets.isEmpty())Text("Importa imágenes desde el editor para reutilizarlas.")
            library.assets.entries.forEachIndexed {index,(id,raw)->Row(verticalAlignment=androidx.compose.ui.Alignment.CenterVertically) {AssetPreview(raw);Text("Icono ${index+1}",Modifier.weight(1f));TextButton(enabled=!busy,onClick={perform {repository.removeAsset(id)}}) {Text("Quitar icono")}}}
        }
    },confirmButton={TextButton(onClick={assetLibrary=false}) {Text("Cerrar")}})
    deleting?.let {(document,expected)->AlertDialog(onDismissRequest={if(!busy)deleting=null},title={Text("Eliminar diagrama")},text={Text("¿Eliminar «${document.name}»? Esta acción no se puede deshacer.")},confirmButton={TextButton(enabled=!busy,onClick={perform {repository.delete(document.id,expected);deleting=null}}) {Text("Eliminar")}},dismissButton={TextButton(enabled=!busy,onClick={deleting=null}) {Text("Cancelar")}})}
    error?.let {message->AlertDialog(onDismissRequest={error=null},text={Text(message)},confirmButton={TextButton(onClick={error=null}) {Text("Cerrar")}})}
}

@Composable private fun DiagramEditor(initial:DiagramDocument,baseline:String?,assets:Map<String,String>,favorites:List<Int>,repository:DiagramRepository,busy:Boolean,
    onDocument:(DiagramDocument)->Unit,onBaseline:(String)->Unit,onClose:()->Unit,perform:(suspend ()->Unit)->Unit) {
    val context=LocalContext.current
    var direction by rememberSaveable(initial.id) {mutableStateOf("AUTO")}
    var lastConnection by rememberSaveable(initial.id) {mutableStateOf<String?>(null)}
    var noSpace by remember {mutableStateOf<Boolean?>(null)}
    var placement by remember {mutableStateOf<Boolean?>(null)}
    var pagePending by remember {mutableStateOf<DiagramPage?>(null)}
    var fitPage by remember {mutableStateOf(false)}
    var boundary by remember {mutableIntStateOf(0)}
    var boundaryVisible by remember {mutableStateOf(false)}
    var palette by remember {mutableStateOf<String?>(null)}
    var gradient by remember {mutableStateOf<String?>(null)}
    var exportProfile by rememberSaveable {mutableStateOf("SHARE")}
    LaunchedEffect(boundary) {if(boundary>0) {boundaryVisible=true;delay(3000);boundaryVisible=false}}
    val history=remember(initial.id) {DiagramHistory(initial)}
    var d by remember(initial.id) {mutableStateOf(initial)}
    var lastFlow by remember(initial.id) {mutableStateOf(initial.edges.lastOrNull {it.flow.enabled}?.flow)}
    var selected by rememberSaveable(initial.id) {mutableStateOf<String?>(null)}
    var edge by rememberSaveable(initial.id) {mutableStateOf<String?>(null)}
    var selection by rememberSaveable(initial.id) {mutableStateOf(emptyList<String>())}
    var edgeOrder by rememberSaveable(initial.id) {mutableStateOf(initial.edges.map {it.id})}
    var panel by rememberSaveable(initial.id) {mutableStateOf("Elementos")}
    var detail by rememberSaveable(initial.id) {mutableStateOf<String?>(null)}
    var minimized by rememberSaveable(initial.id) {mutableStateOf(true)}
    var presenting by rememberSaveable(initial.id) {mutableStateOf(false)}
    var live by rememberSaveable(initial.id) {mutableStateOf(false)}
    var playing by rememberSaveable(initial.id) {mutableStateOf(true)}
    var playbackToken by rememberSaveable(initial.id) {mutableIntStateOf(0)}
    var previewEdge by rememberSaveable(initial.id) {mutableStateOf<String?>(null)}
    var snap by rememberSaveable(initial.id) {mutableStateOf(false)}
    var grid by rememberSaveable(initial.id) {mutableStateOf(false)}
    var gridStep by rememberSaveable(initial.id) {mutableFloatStateOf(.025f)}
    var guides by rememberSaveable(initial.id) {mutableStateOf(false)}
    var magnetic by rememberSaveable(initial.id) {mutableStateOf(false)}
    var mode by rememberSaveable(initial.id) {mutableStateOf("MOVE")}
    var fitRequest by rememberSaveable(initial.id) {mutableIntStateOf(0)}
    var nameForCopy by rememberSaveable(initial.id) {mutableStateOf(false)}
    var naming by rememberSaveable(initial.id) {mutableStateOf(false)}
    var proposedName by rememberSaveable(initial.id) {mutableStateOf(initial.name)}
    var confirmExit by remember {mutableStateOf(false)}
    var exportUri by remember {mutableStateOf<Uri?>(null)}
    var exportedFormat by remember {mutableStateOf(DiagramFormat.PNG)}
    var validationError by remember {mutableStateOf<String?>(null)}
    var help by remember {mutableStateOf<String?>(null)}
    var noAnimation by remember {mutableStateOf(false)}
    var replaceId by rememberSaveable(initial.id) {mutableStateOf<String?>(null)}
    var importingFlow by rememberSaveable(initial.id) {mutableStateOf<String?>(null)}
    var importingBackground by rememberSaveable(initial.id) {mutableStateOf(false)}
    var exportFormat by rememberSaveable {mutableStateOf("PNG")}
    var exportWidth by rememberSaveable {mutableIntStateOf(640)}
    var exportSeconds by rememberSaveable {mutableIntStateOf(8)}
    var exportLoop by rememberSaveable {mutableStateOf(true)}
    var pendingExport by rememberSaveable {mutableStateOf<String?>(null)}
    val exportScope=rememberCoroutineScope()
    var exportJob by remember {mutableStateOf<Job?>(null)}
    var progress by remember {mutableFloatStateOf(0f)}
    val exporting=exportJob!=null
    val locked=busy||exporting
    val node=d.nodes.firstOrNull {it.id==selected}
    val connection=d.edges.firstOrNull {it.id==edge}
    val validSelection=selection.filter {id->d.nodes.any {it.id==id}}.toSet()
    val renderer=remember(assets,d.assets()) {DiagramRenderer(assets.filterKeys {it in d.assets()})}
    DisposableEffect(renderer) {onDispose {renderer.close()}}
    LaunchedEffect(d.edges.map {it.id}) {edgeOrder=(edgeOrder+d.edges.map {it.id}).distinct()}
    fun change(next:DiagramDocument) {try {DiagramCodec.encode(next);history.commit(next);val flow=next.edges.firstOrNull {it.id==edge}?.flow;if(flow!=null&&flow.enabled&&flow!=d.edges.firstOrNull {it.id==edge}?.flow)lastFlow=flow;d=next;onDocument(next)} catch(e:IllegalArgumentException) {validationError=e.message ?: "Se alcanzó un límite del diagrama."}}
    fun duplicate(connected:Boolean,manual:androidx.compose.ui.geometry.Offset?=null) {
        val id=selected ?: return
        val result=try {duplicateDiagramNode(d,id,connected,DiagramDirection.valueOf(direction),lastConnection,manual)} catch(e:IllegalArgumentException) {validationError=e.message ?: "No se puede duplicar este elemento.";return}
        if(result==null) {noSpace=connected;return}
        change(result.document);selected=result.selected;selection=listOf(result.selected);edge=null;minimized=true;detail=null
        if(connected)lastConnection=result.document.edges.last().id
        placement=null
    }
    fun enlarge() {
        val width=minOf(4096,(d.width*1.4).toInt());val height=minOf(4096,(d.height*1.4).toInt())
        if(width.toLong()*height>8_000_000||width==d.width&&height==d.height) {validationError="El documento alcanzó el máximo seguro de 8 millones de píxeles y 4096 por lado.";return}
        pagePending=DiagramPage(DiagramPageFormat.FREE,width,height)
    }
    fun panelBack() {naming=false;when {
        detail?.startsWith("flow:")==true->detail="edge:flow"
        detail?.startsWith("edge:")==true&&detail!="edge:menu"->detail="edge:menu"
        detail?.startsWith("export:")==true->detail="document:export"
        detail?.startsWith("document:")==true&&detail!="document:menu"->detail="document:menu"
        detail=="import"->{detail=if(importingBackground)"background" else if(importingFlow!=null)"edge:flow" else null;panel=if(importingBackground)"Opciones" else if(importingFlow!=null)"Conexiones" else if(replaceId!=null)"Contexto" else "Elementos"}
        detail!=null->{detail=null;if(panel=="Contexto")minimized=true}
        else->minimized=true
    }}
    fun close() {when {exporting->exportJob?.cancel();presenting->presenting=false;!minimized->panelBack();baseline==null||d!=DiagramCodec.decode(baseline)->confirmExit=true;else->onClose()}}
    fun open(key:String) {
        naming=false
        panel=when {key.startsWith("node:")->"Contexto";key.startsWith("edge:")||key.startsWith("flow:")->"Conexiones";key=="import"->"Elementos";else->"Opciones"}
        if(panel=="Conexiones")mode="CONNECT"
        detail=key.takeUnless {it=="node:properties"};minimized=false
    }
    fun toggle(label:String) {
        naming=false
        if(label=="Conexiones") {mode=if(mode=="CONNECT")"MOVE" else "CONNECT";panel=label;detail=null;minimized=mode!="CONNECT"}
        else {if(mode=="CONNECT")mode="MOVE";if(panel==label)minimized=!minimized else {panel=label;detail=null;minimized=false}}
    }
    fun requestName(copy:Boolean) {
        nameForCopy=copy;proposedName=if(copy)d.name.take(90)+" · copia" else d.name
        naming=true;panel="Opciones";detail="document:name";minimized=false
    }
    fun saveNamed() {
        val named=d.copy(id=if(nameForCopy)diagramId() else d.id,name=proposedName.trim())
        naming=false;detail=null;minimized=true
        if(nameForCopy)perform {val raw=repository.save(named,null);onDocument(named);onBaseline(raw)}
        else {change(named);perform {onBaseline(repository.save(named,baseline))}}
    }
    fun removeSelection() {
        val removing=validSelection+listOfNotNull(selected)
        val removedEdges=d.edges.filter {it.id==edge||it.from in removing||it.to in removing}.map {it.id}.toSet()
        change(d.copy(nodes=d.nodes.filterNot {it.id in removing},edges=d.edges.filterNot {it.id in removedEdges},playback=if(removedEdges.isEmpty())d.playback else d.playback.copy(route=emptyList())))
        selected=null;selection=emptyList();edge=null;detail=null
    }
    fun preview(id:String?=null,presentation:Boolean=false) {
        if(id==null&&(d.playback.mode==DiagramAnimationMode.OFF||d.playback.mode==DiagramAnimationMode.SEGMENT&&d.playback.route.isEmpty())) {open("animation");return}
        if(id==null&&d.playback.mode==DiagramAnimationMode.SEQUENTIAL&&diagramRoute(d).choices.isNotEmpty()) {open("animation");return}
        if(!presentation&&d.edges.none {it.flow.enabled&&(id==null||it.id==id)}) {noAnimation=true;return}
        previewEdge=id;playing=true;playbackToken++
        if(presentation) {presenting=true;live=false} else live=true
    }
    fun select(n:String?,e:String?) {
        if(mode=="MULTI"&&n!=null) {selection=if(n in selection)selection-n else selection+n;selected=null;edge=null;return}
        selected=n;edge=e;selection=listOfNotNull(n)
        if(mode=="CONNECT") {if(e!=null)lastConnection=e;panel="Conexiones";detail=null;minimized=true;return}
        if(e!=null) {lastConnection=e;if(live&&previewEdge!=null)previewEdge=e;panel="Conexiones";detail="edge:menu";minimized=false}
        else {minimized=true;detail=null;panel=if(mode=="CONNECT")"Conexiones" else "Contexto"}
    }
    fun useAsset(id:String) {
        when {
            importingBackground->change(d.copy(backgroundAsset=id))
            importingFlow!=null->change(d.copy(edges=d.edges.map {if(it.id==importingFlow)it.copy(flow=it.flow.copy(objectType=FlowObject.IMAGE,asset=id,enabled=true)) else it}))
            replaceId!=null->change(d.copy(nodes=d.nodes.map {if(it.id==replaceId)it.copy(asset=id) else it}))
            else->{val n=DiagramNode(shape=DiagramShape.IMAGE,asset=id,text="",fill=0);change(d.copy(nodes=d.nodes+n));selected=n.id;edge=null;selection=listOf(n.id)}
        }
        val destination=when {importingBackground->"background";importingFlow!=null->"edge:flow";else->null}
        panel=if(importingBackground)"Opciones" else if(importingFlow!=null)"Conexiones" else "Contexto"
        detail=destination;minimized=destination==null
        replaceId=null;importingFlow=null;importingBackground=false
    }
    fun importMenu(background:Boolean=false,flow:String?=null,replace:String?=null) {
        importingBackground=background;importingFlow=flow;replaceId=replace;open("import")
    }
    BackHandler {close()}
    val importer=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) {uri->if(uri!=null)perform {
        val raw=com.r0ybt.arachn0de.visual.importCanvasImage(context,uri);useAsset(repository.importImage(raw))
    }}
    val exporter=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) {tree->
        val raw=pendingExport
        if(tree!=null&&raw!=null&&!exporting) {
            val snapshot=DiagramCodec.decode(raw)
            val options=DiagramExportOptions(DiagramFormat.valueOf(exportFormat),exportWidth,exportSeconds,exportLoop,DiagramExportProfile.valueOf(exportProfile))
            progress=0f
            exportJob=exportScope.launch {
                try {
                    exportUri=exportDiagram(context,tree,snapshot,assets,options) {value->withContext(Dispatchers.Main) {progress=value}}
                    exportedFormat=options.format
                } catch(c:CancellationException) {throw c}
                catch(e:Exception) {validationError=e.message ?: "No se pudo exportar el diagrama."}
                finally {exportJob=null;pendingExport=null}
            }
        } else pendingExport=null
    }
    BoxWithConstraints(Modifier.fillMaxSize().background(com.r0ybt.arachn0de.ui.theme.Arachn0deColors.Background).safeDrawingPadding().imePadding().testTag("diagram-editor")) {
        val bodyLimit=minOf(176.dp,maxHeight*.24f)
        val chromePalette=LocalAppearancePalette.current
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().appearancePaint("header",chromePalette.role("header")).padding(horizontal=8.dp,vertical=4.dp).testTag("diagram-top-controls"),horizontalArrangement=Arrangement.spacedBy(4.dp),verticalAlignment=androidx.compose.ui.Alignment.CenterVertically) {
                DiagramTool("Volver","back",enabled=!locked) {close()}
                Text(d.name,Modifier.weight(1f),style=MaterialTheme.typography.titleMedium,color=chromePalette.text,maxLines=1,overflow=androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                GlobalChromeActions()
            }
            Row(Modifier.weight(1f).fillMaxWidth()) {
                Surface(color=chromePalette.surface,modifier=Modifier.width(56.dp).fillMaxHeight().testTag("diagram-visual-toolbar")) {
                    Column(Modifier.verticalScroll(rememberScrollState()),horizontalAlignment=androidx.compose.ui.Alignment.CenterHorizontally,verticalArrangement=Arrangement.Center) {
                        IconToggleButton(checked=grid,onCheckedChange={grid=it},enabled=!locked&&!presenting,modifier=Modifier.size(48.dp).testTag("diagram-visual:grid")) {Icon(androidx.compose.material.icons.Icons.Default.GridOn,"Mostrar cuadrícula",Modifier.size(22.dp))}
                        IconToggleButton(checked=magnetic,onCheckedChange={magnetic=it},enabled=!locked&&!presenting,modifier=Modifier.size(48.dp).testTag("diagram-visual:magnet").semantics {contentDescription="Imán"}) {ToolMagnetIcon(if(magnetic)chromePalette.primary else chromePalette.muted)}
                        IconToggleButton(checked=snap,onCheckedChange={snap=it},enabled=!locked&&!presenting,modifier=Modifier.size(48.dp).testTag("diagram-visual:snap")) {Icon(androidx.compose.material.icons.Icons.Default.Grid4x4,"Ajustar a cuadrícula",Modifier.size(22.dp))}
                        IconToggleButton(checked=mode=="PAN",onCheckedChange={mode=if(it)"PAN" else "MOVE"},enabled=!locked&&!presenting,modifier=Modifier.size(48.dp).testTag("diagram-visual:zoom")) {Icon(androidx.compose.material.icons.Icons.Default.OpenWith,"Zoom y desplazamiento",Modifier.size(22.dp))}
                        IconButton(onClick={fitPage=false;fitRequest++},enabled=!locked,modifier=Modifier.size(48.dp).testTag("diagram-visual:fit")) {Icon(androidx.compose.material.icons.Icons.Default.FitScreen,"Ajustar a pantalla",Modifier.size(22.dp))}
                    }
                }
                Box(Modifier.weight(1f).fillMaxHeight()) {
            DiagramCanvas(d,renderer,selected,edge,DiagramGesture.valueOf(mode),presenting,playing,snap,::select,::change,Modifier.fillMaxSize(),fitRequest,previewEdge,live,playbackToken,validSelection,
                showNumbers=mode=="CONNECT"||!minimized&&panel=="Conexiones",edgeOrder=(edgeOrder+d.edges.map {it.id}).distinct(),grid=grid,gridStep=gridStep,guides=guides,magnetic=magnetic,fitPage=fitPage,onBoundary={boundary++},placing=placement!=null,onPlace={duplicate(placement==true,it)})
                }
            }
            if(placement!=null)Row {Text("Toca el destino de la copia",Modifier.weight(1f));TextButton(onClick={placement=null}) {Text("Cancelar")}}

            ModularPanelSurface(Modifier.fillMaxWidth().testTag("diagram-panel"),shadowElevation=0.dp) {
                if(!presenting) {
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal=8.dp,vertical=4.dp).testTag("diagram-quick-controls"),horizontalArrangement=Arrangement.spacedBy(4.dp)) {
                DiagramTool("Deshacer","undo",enabled=!locked&&!presenting&&history.canUndo) {d=history.undo();onDocument(d)}
                DiagramTool("Rehacer","redo",enabled=!locked&&!presenting&&history.canRedo) {d=history.redo();onDocument(d)}
                DiagramTool("Exportar","export",enabled=!locked&&!presenting) {open("document:export")}
                DiagramTool("Guardar","save",enabled=!locked&&!presenting) {if(baseline==null)requestName(false) else perform {onBaseline(repository.save(d,baseline))}}
                DiagramTool(if(live&&playing)"Pausar vista previa" else if(live)"Reanudar vista previa" else "Vista previa en vivo",if(live&&playing)"pause" else "play",live,!locked&&!presenting) {if(live)playing=!playing else preview()}
                if(live)DiagramTool("Detener vista previa","stop",enabled=!locked&&!presenting) {live=false;playbackToken++;previewEdge=null}
            }
                }
                if(presenting)DiagramToolRow {
                    DiagramTool(if(playing)"Pausar" else "Reanudar",if(playing)"pause" else "play") {playing=!playing}
                    DiagramTool("Edición","back") {presenting=false}
                } else Column {
                    if(!minimized) {
                        Row(Modifier.fillMaxWidth().pointerInput(minimized) {
                            var dy=0f
                            detectVerticalDragGestures(onDragStart={dy=0f},onDragEnd={if(dy>24.dp.toPx())minimized=true}) {event,amount->event.consume();dy+=amount}
                        },verticalAlignment=androidx.compose.ui.Alignment.CenterVertically) {
                            if(detail!=null)IconButton(onClick={panelBack()},enabled=!locked) {Icon(androidx.compose.material.icons.Icons.AutoMirrored.Filled.ArrowBack,"Volver al nivel anterior",Modifier.size(22.dp))}
                            if(detail==null)Spacer(Modifier.width(8.dp))
                            Text(if(mode=="CONNECT"&&detail==null)"Conexiones · arrastra entre caras" else diagramPanelTitle(detail,panel),Modifier.weight(1f),maxLines=2,overflow=androidx.compose.ui.text.style.TextOverflow.Ellipsis,color=chromePalette.text,style=MaterialTheme.typography.labelLarge)
                            IconButton(onClick={help=diagramPanelHelp(detail)}) {Icon(androidx.compose.material.icons.Icons.AutoMirrored.Filled.HelpOutline,"Ayuda",Modifier.size(22.dp))}
                            if(detail!=null&&!naming)IconButton(onClick={panelBack()},enabled=!locked) {Icon(androidx.compose.material.icons.Icons.Default.Check,"Confirmar",Modifier.size(22.dp))}
                            IconButton(onClick={minimized=true}) {Icon(androidx.compose.material.icons.Icons.Default.KeyboardArrowDown,"Minimizar",Modifier.size(22.dp))}
                        }
                        HorizontalDivider(color=chromePalette.outline,thickness=.5.dp)
                        Column(Modifier.fillMaxWidth().heightIn(max=bodyLimit).verticalScroll(remember(panel,detail,selected,edge) {ScrollState(0)}).padding(horizontal=8.dp,vertical=4.dp).testTag("diagram-panel-body")) {
                            val colorTarget=when(detail) {"node:fill"->"fill";"node:border"->"border";"node:ink"->"ink";"edge:color"->"edge";"background"->"background";else->null}
                            if(colorTarget!=null)DiagramToolRow {
                                DiagramTool("Color personalizado","color",enabled=!locked) {palette=colorTarget}
                                if(colorTarget in listOf("fill","background"))DiagramTool("Degradado lineal","fill",enabled=!locked) {gradient=colorTarget}
                            }
                            if(detail=="flow:appearance")DiagramToolRow {DiagramTool("Color de partícula","color") {palette="particle"};DiagramTool("Color de halo","color") {palette="halo"};TextButton(onClick={connection?.let {e->change(d.copy(edges=d.edges.map {if(it.id==e.id)it.copy(flow=it.flow.copy(haloColor=null)) else it}))}}) {Text("Halo automático")}}
                            when {
                                detail=="import"->{
                                    DiagramToolRow {
                                        DiagramTool("Importar imagen nueva","import",enabled=!locked) {importer.launch(arrayOf("image/png","image/jpeg"))}
                                        assets.entries.forEachIndexed {index,(id,raw)->AssetButton(raw,"Usar icono ${index+1}",!locked) {useAsset(id)}}
                                    }
                                }
                                detail?.startsWith("node:")==true&&node!=null->DiagramNodeDetail(detail!!,node,!locked) {value->change(d.copy(nodes=d.nodes.map {if(it.id==value.id)value else it}))}
                                (detail?.startsWith("edge:")==true||detail?.startsWith("flow:")==true)&&connection!=null->{
                                    if(detail=="edge:menu")DiagramEdgeTools(!locked,::open) {change(d.copy(edges=d.edges.filterNot {it.id==connection.id},playback=d.playback.copy(route=emptyList())));edge=null;detail=null}
                                    else DiagramEdgeDetail(detail!!,connection,!locked,{value->change(d.copy(edges=d.edges.map {if(it.id==value.id)value else it}))},::open,{importMenu(flow=connection.id)},{preview(connection.id)},lastFlow?.takeIf {it.asset==null||it.asset in assets})
                                }
                                detail=="duplicate:direction"->DiagramToolRow {DiagramDirection.entries.forEach {value->TextButton(onClick={direction=value.name}) {Text((if(direction==value.name)"✓ " else "")+value.label)}}}
                                detail=="format"->{
                                    Text("${d.page.format.label} · ${d.width} × ${d.height} lógicos")
                                    DiagramToolRow {DiagramPageFormat.entries.forEach {f->DiagramTool(f.label,"ratio:${f.width}:${f.height}",d.page.format==f,!locked) {pagePending=DiagramPage(f,f.width,f.height)}}}
                                    DiagramToolRow {DiagramTool("Ampliar lienzo","size",enabled=!locked) {enlarge()};DiagramTool("Ajustar al formato","fit") {fitPage=true;fitRequest++};DiagramTool("Vista previa de encuadre","present") {fitPage=true;fitRequest++;presenting=true;playing=false}}
                                }
                                detail=="animation"->{
                                    DiagramToolRow {DiagramAnimationMode.entries.forEach {m->TextButton(onClick={if(m==DiagramAnimationMode.OFF) {live=false;previewEdge=null;playing=false};change(d.copy(playback=d.playback.copy(mode=m)))}) {Text((if(d.playback.mode==m)"✓ " else "")+when(m) {DiagramAnimationMode.OFF->"Desactivado";DiagramAnimationMode.SEGMENT->"Por tramo";DiagramAnimationMode.SEQUENTIAL->"Secuencial";DiagramAnimationMode.SIMULTANEOUS->"Simultáneo"})}}}
                                    Row {Switch(d.playback.loop,{change(d.copy(playback=d.playback.copy(loop=it)))});Text("Repetir recorrido")}
                                    DiagramToolRow {
                                        TextButton(onClick={change(d.copy(edges=d.edges.map {it.copy(flow=it.flow.copy(enabled=true))}))}) {Text("Habilitar conexiones")}
                                        TextButton(onClick={change(d.copy(playback=d.playback.copy(route=emptyList())))}) {Text("Reiniciar recorrido")}
                                        DiagramTool("Reiniciar reproducción","play") {playbackToken++;preview()}
                                    }
                                    if(d.playback.mode==DiagramAnimationMode.SEGMENT)DiagramToolRow {d.edges.forEach {e->val number=edgeOrder.indexOf(e.id)+1;DiagramTool("Animar conexión $number","number:$number",d.playback.route.firstOrNull()==e.id) {change(d.copy(playback=d.playback.copy(route=listOf(e.id)),edges=d.edges.map {if(it.id==e.id)it.copy(flow=it.flow.copy(enabled=true)) else it}))}}}
                                    if(d.playback.mode==DiagramAnimationMode.SEQUENTIAL) {
                                        val route=diagramRoute(d)
                                        Text("Recorrido: "+route.edges.joinToString(" → ") {"${edgeOrder.indexOf(it)+1}"})
                                        if(route.choices.isNotEmpty())Text("Elegir siguiente tramo")
                                        DiagramToolRow {route.choices.forEach {id->val number=edgeOrder.indexOf(id)+1;DiagramTool("Continuar por conexión $number","number:$number") {change(d.copy(playback=d.playback.copy(route=route.edges+id)))}}}
                                    }
                                }
                                detail=="grid"->{
                                    DiagramToolRow {listOf(.025f,.05f,.1f).forEach {step->TextButton(enabled=!locked,onClick={gridStep=step}) {Text((if(gridStep==step)"✓ " else "")+"${(step*1000).toInt()/10f} %")}}}
                                }
                                detail=="align"->{
                                    DiagramToolRow {DiagramTool("Selección múltiple","multi",mode=="MULTI",!locked) {mode=if(mode=="MULTI")"MOVE" else "MULTI"};Text("${validSelection.size}",Modifier.padding(12.dp));DiagramTool("Limpiar selección","select",enabled=validSelection.isNotEmpty()) {selection=emptyList();selected=null}}
                                    DiagramToolRow {DiagramArrange.entries.forEach {action->DiagramTool(diagramArrangeLabel(action),"align:${action.name}",enabled=!locked&&canArrange(d,validSelection,action)) {change(arrangeDiagram(d,validSelection,action))}}}
                                }
                                detail=="view"->DiagramToolRow {
                                    DiagramTool("Zoom y desplazamiento","pan",mode=="PAN",!locked) {mode=if(mode=="PAN")"MOVE" else "PAN"}
                                    DiagramTool("Presentación","present",enabled=!locked) {preview(presentation=true)}
                                }
                                detail=="document:menu"->DiagramToolRow {
                                    DiagramTool("Nombre del documento","label",enabled=!locked) {open("document:name")}
                                    DiagramTool("Guardar copia","copy",enabled=!locked) {if(baseline==null)requestName(true) else perform {val copy=d.copy(id=diagramId(),name=d.name.take(90)+" · copia");val raw=repository.save(copy,null);onDocument(copy);onBaseline(raw)}}
                                    DiagramTool("Exportar documento","export",enabled=!locked) {open("document:export")}
                                }
                                detail=="document:name"->if(naming) {
                                    Text("Nombre del diagrama")
                                    OutlinedTextField(proposedName,{if(it.length<=100)proposedName=it},label={Text("Nombre")},enabled=!locked,singleLine=true,modifier=Modifier.fillMaxWidth())
                                    Row {
                                        TextButton(enabled=proposedName.isNotBlank()&&!locked,onClick={saveNamed()}) {Text("Guardar diagrama")}
                                        TextButton(enabled=!locked,onClick={naming=false;detail=null;minimized=true}) {Text("Cancelar")}
                                    }
                                } else OutlinedTextField(d.name,{if(it.isNotBlank()&&it.length<=100)change(d.copy(name=it))},label={Text("Nombre")},enabled=!locked,modifier=Modifier.fillMaxWidth())
                                detail=="document:export"->DiagramToolRow {DiagramFormat.entries.forEach {format->TextButton(enabled=!locked&&(format!=DiagramFormat.MP4||android.os.Build.VERSION.SDK_INT>=26),onClick={exportFormat=format.name;if(format==DiagramFormat.GIF)exportWidth=320;open("export:${format.name}")}) {Text(format.name)}}}
                                detail?.startsWith("export:")==true->{
                                    val format=DiagramFormat.valueOf(exportFormat)
                                    DiagramToolRow {DiagramExportProfile.entries.forEach {p->TextButton(onClick={exportProfile=p.name}) {Text((if(exportProfile==p.name)"✓ " else "")+when(p) {DiagramExportProfile.SHARE->"Compartir";DiagramExportProfile.HIGH->"Alta calidad";DiagramExportProfile.CUSTOM->"Personalizado"})}}}
                                    if(exportProfile=="CUSTOM")GeometryStep("Lado mayor",exportWidth.toFloat(),64f,if(format==DiagramFormat.GIF)640f else 1920f,64f) {exportWidth=it.toInt()}
                                    val dimensions=DiagramExportOptions(format,exportWidth,exportSeconds,exportLoop,DiagramExportProfile.valueOf(exportProfile)).dimensions(d)
                                    Text("${dimensions.first} × ${dimensions.second} px · ${d.page.format.label}")
                                    if(format!=DiagramFormat.PNG) {
                                        DiagramToolRow {listOf(4,8,12,24).forEach {seconds->TextButton(onClick={exportSeconds=seconds}) {Text((if(exportSeconds==seconds)"✓ " else "")+"$seconds s")}}}
                                        if(format==DiagramFormat.GIF)Row {Switch(exportLoop,{exportLoop=it},modifier=Modifier.semantics {contentDescription="Repetir GIF"});Text("Repetir GIF")}
                                    }
                                    if(format!=DiagramFormat.PNG)Text(runCatching {"Duración final: %.1f s".format(DiagramExportOptions(format,exportWidth,exportSeconds,exportLoop,DiagramExportProfile.valueOf(exportProfile)).duration(d))}.getOrElse {"El recorrido supera 60 s."},style=MaterialTheme.typography.labelSmall)
                                    if(d.edges.none {it.flow.enabled}&&format!=DiagramFormat.PNG)Text("Sin flujos activos",style=MaterialTheme.typography.labelSmall)
                                    TextButton(enabled=!locked,onClick={try {DiagramExportOptions(format,exportWidth,exportSeconds,exportLoop,DiagramExportProfile.valueOf(exportProfile)).validateDocument(d);pendingExport=DiagramCodec.encode(d);exporter.launch(null)} catch(e:IllegalArgumentException) {validationError=e.message}}) {Text("Elegir carpeta y exportar")}
                                }
                                detail=="background"->{
                                    DiagramToolRow {
                                        DiagramTool("Fondo blanco","color:ffffffff",d.background==-1&&d.backgroundAsset==null,!locked) {change(d.copy(background=-1,backgroundAsset=null,backgroundGradient=null))}
                                        DiagramTool("Fondo negro","color:ff000000",d.background==android.graphics.Color.BLACK&&d.backgroundAsset==null,!locked) {change(d.copy(background=android.graphics.Color.BLACK,backgroundAsset=null,backgroundGradient=null))}
                                        DiagramTool("Fondo transparente","transparent",d.background==0&&d.backgroundAsset==null,!locked) {change(d.copy(background=0,backgroundAsset=null,backgroundGradient=null))}
                                        DiagramTool(if(d.backgroundAsset==null)"Imagen de fondo" else "Reemplazar fondo","image",enabled=!locked) {importMenu(background=true)}
                                        if(d.backgroundAsset!=null)DiagramTool("Quitar imagen de fondo","delete",enabled=!locked) {change(d.copy(backgroundAsset=null))}
                                    }
                                    if(d.backgroundAsset!=null)DiagramToolRow {DiagramBackgroundFit.entries.forEach {fit->TextButton(enabled=!locked,onClick={change(d.copy(backgroundFit=fit))}) {Text((if(d.backgroundFit==fit)"✓ " else "")+if(fit==DiagramBackgroundFit.FIT)"Ajustar" else "Rellenar")}}}
                                    if(d.background==android.graphics.Color.BLACK)TextButton(enabled=!locked,onClick={change(adaptDiagramContrast(d))}) {Text("Adaptar contraste")}
                                }
                                panel=="Elementos"->DiagramToolRow {
                                    DiagramShape.entries.filter {it!=DiagramShape.IMAGE}.forEach {shape->DiagramTool(shape.label,shape.name.lowercase(),active=node?.shape==shape,enabled=!locked) {
                                        val n=if(shape==DiagramShape.TABLE)diagramTableNode() else DiagramNode(shape=shape,text=if(shape==DiagramShape.TEXT)"Texto" else "",fill=if(shape==DiagramShape.TEXT)0 else 0xff1d2b3b.toInt());change(d.copy(nodes=d.nodes+n));selected=n.id;edge=null;selection=listOf(n.id);panel="Contexto";detail=null;minimized=true
                                    }}
                                    DiagramTool("Importar","import",enabled=!locked) {importMenu()}
                                }
                                panel=="Conexiones"->DiagramToolRow {
                                    DiagramTool("Animación global","play",enabled=!locked) {open("animation")}
                                    d.edges.forEach {e->val number=(edgeOrder+d.edges.map {it.id}).distinct().indexOf(e.id)+1;DiagramTool("Conexión $number","number:$number",edge==e.id,!locked) {select(null,e.id);open("edge:menu")}}
                                }
                                panel=="Contexto"&&node!=null->{
                                    DiagramNodeTools(node,!locked,::open,{duplicate(false)},{change(d.removeNode(node.id));selected=null;selection=emptyList()},{importMenu(replace=node.id)},{duplicate(true)},expanded=true)
                                    DiagramNodeProperties(node,!locked) {value->change(d.copy(nodes=d.nodes.map {if(it.id==value.id)value else it}))}
                                }
                                panel=="Opciones"->{
                                    DiagramToolRow {
                                        DiagramTool("Mover conjunto","pan",mode=="GROUP",!locked) {mode=if(mode=="GROUP")"MOVE" else "GROUP";placement=null;detail=null;minimized=true}
                                        DiagramTool("Centrar contenido","fit",enabled=!locked&&d.nodes.isNotEmpty()) {change(centerDiagramContent(d))}
                                        DiagramTool("Espaciado de cuadrícula","grid",enabled=!locked) {open("grid")}
                                    }
                                    DiagramToolRow {
                                        DiagramTool("Alineación","guides",enabled=!locked) {open("align")}
                                        DiagramTool("Vista","present",enabled=!locked) {open("view")}
                                        DiagramTool("Conexiones del documento","connect",enabled=!locked) {panel="Conexiones";detail=null;minimized=false}
                                    }
                                    DiagramToolRow {
                                        DiagramTool("Formato del documento","size",enabled=!locked) {open("format")}
                                        DiagramTool("Documento","document",enabled=!locked) {open("document:menu")}
                                        DiagramTool("Fondo","image",enabled=!locked) {open("background")}
                                    }
                                }
                            }
                        }
                    } else if(mode=="GROUP")Row(verticalAlignment=androidx.compose.ui.Alignment.CenterVertically) {Text("Mover conjunto · arrastra el contenido",Modifier.weight(1f).padding(horizontal=8.dp),style=MaterialTheme.typography.labelMedium);TextButton(onClick={mode="MOVE"}) {Text("Terminar")}} else when {
                        node!=null->DiagramNodeTools(node,!locked,::open,{duplicate(false)},{change(d.removeNode(node.id));selected=null;selection=emptyList()},{importMenu(replace=node.id)},{duplicate(true)})
                        connection!=null->DiagramEdgeTools(!locked,::open) {change(d.copy(edges=d.edges.filterNot {it.id==connection.id},playback=d.playback.copy(route=emptyList())));edge=null}
                    }
                    HorizontalDivider(color=chromePalette.outline,thickness=.5.dp)
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal=8.dp,vertical=4.dp).testTag("diagram-bottom-controls"),horizontalArrangement=Arrangement.spacedBy(2.dp,androidx.compose.ui.Alignment.CenterHorizontally),verticalAlignment=androidx.compose.ui.Alignment.CenterVertically) {
                        DiagramTool("Mover","pan",mode=="MOVE",!locked) {mode="MOVE"}
                        DiagramTool("Redimensionar","size",mode=="SELECT",!locked) {mode="SELECT"}
                        DiagramTool("Conectar","connect",mode=="CONNECT",!locked) {mode=if(mode=="CONNECT")"MOVE" else "CONNECT";minimized=true;detail=null;panel="Conexiones"}
                        DiagramTool("Elementos","elements",panel=="Elementos"&&!minimized,!locked) {toggle("Elementos")}
                        DiagramTool("Opciones","settings",panel=="Opciones"&&!minimized,!locked) {toggle("Opciones")}
                        DiagramTool("Eliminar","delete",enabled=!locked&&(node!=null||connection!=null||validSelection.isNotEmpty())) {removeSelection()}
                    }
                }
            }
        }
        if(boundaryVisible)Surface(Modifier.align(androidx.compose.ui.Alignment.TopCenter).padding(top=52.dp),tonalElevation=8.dp) {Row {Text("Límite del lienzo",Modifier.padding(12.dp));TextButton(onClick={enlarge()}) {Text("Ampliar")};TextButton(onClick={open("format")}) {Text("Formato")}}}
    }
    fun colorValue(target:String)=when(target) {"fill"->node?.fill;"border"->node?.border;"ink"->node?.ink;"edge"->connection?.color;"particle"->connection?.flow?.color;"halo"->connection?.flow?.haloColor;else->d.background} ?: 0xff43e8ff.toInt()
    fun setColor(target:String,c:Int) {when(target) {
        "fill","border","ink"->node?.let {n->val v=when(target) {"fill"->n.copy(fill=c,gradient=null);"border"->n.copy(border=c);else->n.copy(ink=c)};change(d.copy(nodes=d.nodes.map {if(it.id==n.id)v else it}))}
        "edge","particle","halo"->connection?.let {e->val v=when(target) {"edge"->e.copy(color=c);"particle"->e.copy(flow=e.flow.copy(color=c));else->e.copy(flow=e.flow.copy(haloColor=c))};change(d.copy(edges=d.edges.map {if(it.id==e.id)v else it}))}
        else->change(d.copy(background=c,backgroundGradient=null))
    }}
    palette?.let {target->DiagramPalette(colorValue(target),favorites,{c->perform {repository.favorites((favorites+c).distinct())}},{palette=null}) {c->setColor(target,c);palette=null}}
    gradient?.let {target->DiagramGradientDialog(if(target=="fill")node?.gradient else d.backgroundGradient,favorites,{c->perform {repository.favorites((favorites+c).distinct())}},{gradient=null}) {g->if(target=="fill")node?.let {n->change(d.copy(nodes=d.nodes.map {if(it.id==n.id)it.copy(gradient=g) else it}))} else change(d.copy(backgroundGradient=g));gradient=null}}
    noSpace?.let {connected->AlertDialog(onDismissRequest={noSpace=null},title={Text("No hay espacio disponible")},text={Column {Text("Los nodos existentes conservarán su posición.");TextButton(onClick={noSpace=null;enlarge()}) {Text("Ampliar documento")};TextButton(onClick={noSpace=null;open("duplicate:direction")}) {Text("Cambiar dirección")};TextButton(onClick={noSpace=null;placement=connected}) {Text("Colocar manualmente")}}},confirmButton={TextButton(onClick={noSpace=null}) {Text("Cerrar")}})}
    pagePending?.let {page->AlertDialog(onDismissRequest={pagePending=null},title={Text("Cambiar formato")},text={Text("${page.format.label} · ${page.width} × ${page.height} lógicos. Conservar posiciones o ajustar uniformemente el contenido sin deformarlo.")},confirmButton={TextButton(onClick={try {change(changeDiagramPage(d,page));pagePending=null;fitPage=true;fitRequest++} catch(e:IllegalArgumentException) {validationError="El contenido no cabe. Utiliza Ajustar contenido o elige un formato mayor."}}) {Text("Conservar posiciones")}},dismissButton={Row {TextButton(onClick={pagePending=null}) {Text("Cancelar")};TextButton(onClick={try {change(changeDiagramPage(d,page,true));pagePending=null;fitPage=true;fitRequest++} catch(e:IllegalArgumentException) {validationError=e.message}}) {Text("Ajustar contenido")}}})}
    if(exporting) AlertDialog(onDismissRequest={exportJob?.cancel()},title={Text("Exportando diagrama")},text={Column {LinearProgressIndicator(progress={progress},modifier=Modifier.fillMaxWidth());Text("${(progress*100).toInt()} %")}},confirmButton={TextButton(onClick={exportJob?.cancel()}) {Text("Cancelar exportación")}})
    if(noAnimation)AlertDialog(onDismissRequest={noAnimation=false},text={Text("No hay flujos animados. Selecciona una conexión y activa Flujo animado.")},confirmButton={TextButton(onClick={noAnimation=false;panel="Conexiones";detail=if(edge!=null)"edge:flow" else null;minimized=false}) {Text("Configurar conexión")}},dismissButton={TextButton(onClick={noAnimation=false}) {Text("Cerrar")}})
    help?.let {message->AlertDialog(onDismissRequest={help=null},text={Text(message)},confirmButton={TextButton(onClick={help=null}) {Text("Cerrar")}})}
    if(confirmExit) AlertDialog(onDismissRequest={confirmExit=false},text={Text("Hay cambios sin guardar.")},confirmButton={TextButton(onClick={confirmExit=false}) {Text("Seguir editando")}},dismissButton={TextButton(onClick=onClose) {Text("Descartar cambios")}})
    validationError?.let {message->AlertDialog(onDismissRequest={validationError=null},text={Text(message)},confirmButton={TextButton(onClick={validationError=null}) {Text("Cerrar")}})}
    exportUri?.let {uri->AlertDialog(onDismissRequest={exportUri=null},text={Text("${exportedFormat.label} exportado.")},confirmButton={TextButton(onClick={
        try {val intent=Intent(Intent.ACTION_SEND).setType(exportedFormat.mime).putExtra(Intent.EXTRA_STREAM,uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION).apply {clipData=android.content.ClipData.newRawUri("Diagrama",uri)};context.startActivity(Intent.createChooser(intent,"Compartir diagrama"))} catch(e:Exception) {validationError="No se pudo abrir la aplicación para compartir."}
    }) {Text("Compartir")}},dismissButton={TextButton(onClick={exportUri=null}) {Text("Cerrar")}})}
}
@Composable private fun AssetPreview(raw:String) {
    val bitmap=remember(raw) {
        val bytes=android.util.Base64.decode(raw,android.util.Base64.NO_WRAP)
        val bounds=android.graphics.BitmapFactory.Options().apply {inJustDecodeBounds=true}
        android.graphics.BitmapFactory.decodeByteArray(bytes,0,bytes.size,bounds)
        var sample=1;while(maxOf(bounds.outWidth,bounds.outHeight)/sample>64)sample*=2
        android.graphics.BitmapFactory.decodeByteArray(bytes,0,bytes.size,android.graphics.BitmapFactory.Options().apply {inSampleSize=sample})
    }
    bitmap?.let {Image(it.asImageBitmap(),"Icono importado",Modifier.size(40.dp))}
}

@Composable private fun AssetButton(raw:String,label:String,enabled:Boolean,click:()->Unit) {
    OutlinedCard(onClick=click,enabled=enabled,modifier=Modifier.size(56.dp).semantics {contentDescription=label}) {AssetPreview(raw)}
}

/** Native renderer shared with the editor/exporter, bounded to a 240-pixel longest side. */
internal fun diagramLibraryBitmap(document:DiagramDocument,assets:Map<String,String>):android.graphics.Bitmap {
    val scale=240f/maxOf(document.width,document.height)
    val width=(document.width*scale).toInt().coerceAtLeast(1)
    val height=(document.height*scale).toInt().coerceAtLeast(1)
    val bitmap=android.graphics.Bitmap.createBitmap(width,height,android.graphics.Bitmap.Config.ARGB_8888)
    try {
        val canvas=android.graphics.Canvas(bitmap);canvas.scale(width.toFloat()/document.width,height.toFloat()/document.height)
        DiagramRenderer(assets.filterKeys {it in document.assets()}).use {it.draw(canvas,document)}
        return bitmap
    } catch(e:Exception) {bitmap.recycle();throw e}
}
