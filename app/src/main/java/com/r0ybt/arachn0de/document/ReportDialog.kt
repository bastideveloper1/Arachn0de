package com.r0ybt.arachn0de.document

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.r0ybt.arachn0de.Arachn0deApplication
import com.r0ybt.arachn0de.diagrams.DiagramExportJournal
import com.r0ybt.arachn0de.ui.ModularPanelSurface
import com.r0ybt.arachn0de.ui.PrimaryControlButton
import com.r0ybt.arachn0de.ui.theme.Arachn0deColors
import kotlinx.coroutines.*

@Composable internal fun DocumentReportDialog(projectId:String,layerId:String?,initialFormat:ReportFormat=ReportFormat.PDF,onClose:()->Unit) {
    val context=LocalContext.current;val app=context.applicationContext as? Arachn0deApplication
    val scope=rememberCoroutineScope()
    var content by remember(projectId,layerId) {mutableStateOf<ReportContent?>(null)}
    var title by rememberSaveable(projectId,layerId) {mutableStateOf("")}
    var subtitle by rememberSaveable {mutableStateOf("")};var date by rememberSaveable {mutableStateOf(java.time.LocalDate.now().toString())}
    var index by rememberSaveable {mutableStateOf(true)};var cover by rememberSaveable {mutableStateOf(false)}
    var formatName by rememberSaveable {mutableStateOf(initialFormat.name)}
    var imageName by rememberSaveable {mutableStateOf(ReportImageLayout.AUTO.name)}
    var module by rememberSaveable {mutableStateOf("Documento")};var folded by rememberSaveable {mutableStateOf(false)}
    var expanded by rememberSaveable(projectId,layerId) {mutableStateOf(false)}
    var page by rememberSaveable {mutableIntStateOf(0)};var busy by remember {mutableStateOf(false)}
    var status by remember {mutableStateOf<String?>(null)};var problem by remember {mutableStateOf<String?>(null)}
    var rendered by remember {mutableStateOf<Pair<ReportOptions,ReportPlan>?>(null)}
    val options=ReportOptions(title,subtitle,date,index,cover,ReportFormat.valueOf(formatName),ReportImageLayout.valueOf(imageName))
    LaunchedEffect(projectId,layerId) {
        try {
            val owner=requireNotNull(app) {"Se necesita un almacén desbloqueado."}
            val recovery=DiagramExportJournal(context,owner.privateContext,report=true).recover()
            if(recovery.isNotEmpty())status="Hay una exportación parcial pendiente de limpieza: ${recovery.first()}"
            val value=captureReport(owner,projectId,layerId);content=value
            if(title.isEmpty())title=value.sections.first().title
        } catch(c:CancellationException) {throw c} catch(t:Exception) {problem=t.message ?: "No se pudo leer el contenido."}
    }
    DisposableEffect(content) {val current=content;onDispose {current?.release()}}
    LaunchedEffect(content,options) {
        val data=content ?: return@LaunchedEffect
        try {
            options.validate();problem=null
            val plan=withContext(Dispatchers.Default) {val job=currentCoroutineContext();ReportLayout.measure(data,options) {job.ensureActive()}}
            rendered=options to plan;page=page.coerceIn(0,plan.pages.lastIndex)
        } catch(c:CancellationException) {throw c} catch(t:Exception) {problem=if(runCatching {java.time.LocalDate.parse(date)}.isFailure)"Fecha válida: AAAA-MM-DD." else t.message ?: "Revisa las opciones del informe."}
    }
    val currentExport by rememberUpdatedState(rendered)
    val currentOptions by rememberUpdatedState(options)
    val picker=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) {tree->
        val data=content;val prepared=currentExport
        if(tree!=null&&data!=null&&prepared!=null&&prepared.first==currentOptions&&!busy) {
            busy=true;scope.launch {try {exportReport(context,requireNotNull(app).privateContext,tree,data,prepared.first,prepared.second);status="Documento exportado correctamente."}
                catch(c:CancellationException) {throw c} catch(t:Exception) {problem=t.message ?: "No se pudo exportar el documento."} finally {busy=false}}
        }
    }
    Dialog(onDismissRequest={if(!busy) {if(expanded)expanded=false else onClose()}},properties=DialogProperties(usePlatformDefaultWidth=false,decorFitsSystemWindows=false)) {
        BackHandler {if(!busy) {if(expanded)expanded=false else onClose()}}
        Surface(Modifier.fillMaxSize(),color=Arachn0deColors.Background) {
            if(expanded && rendered!=null && content!=null) {
                Column(Modifier.fillMaxSize().safeDrawingPadding().testTag("report-fullscreen")) {
                    Row(Modifier.fillMaxWidth().padding(horizontal=8.dp),verticalAlignment=androidx.compose.ui.Alignment.CenterVertically) {
                        TextButton(onClick={expanded=false},modifier=Modifier.heightIn(min=48.dp)) {Text("Volver al configurador")}
                        Text("${page+1} / ${rendered!!.second.pages.size}",Modifier.weight(1f),textAlign=androidx.compose.ui.text.style.TextAlign.End)
                    }
                    ReportPreview(content!!,rendered!!.second,page,{page=it},Modifier.weight(1f).fillMaxWidth())
                }
            } else BoxWithConstraints(Modifier.fillMaxSize().safeDrawingPadding().imePadding().testTag("document-report")) {
                val panelMax=maxHeight*.60f
                Column(Modifier.fillMaxSize()) {
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {TextButton(enabled=!busy,onClick=onClose) {Text("Volver")};Text("Exportar PDF / Word",Modifier.padding(12.dp))}
                    val prepared=rendered
                    Box(Modifier.weight(1f).fillMaxWidth().padding(8.dp),contentAlignment=androidx.compose.ui.Alignment.Center) {
                        if(prepared!=null&&content!=null) {
                            key(prepared.second) {
                                ReportPreview(content!!,prepared.second,page, {page=it},Modifier.fillMaxSize(),onPageClick=if(busy)null else ({expanded=true}))
                            }
                        } else if(problem==null)CircularProgressIndicator()
                    }
                    Row(Modifier.fillMaxWidth().padding(horizontal=8.dp,vertical=4.dp),horizontalArrangement=Arrangement.spacedBy(8.dp),verticalAlignment=androidx.compose.ui.Alignment.CenterVertically) {
                        TextButton(enabled=page>0,onClick={page--}) {Text("Anterior")}
                        Text(if(prepared==null)"Preparando…" else "${page.coerceAtMost(prepared.second.pages.lastIndex)+1} / ${prepared.second.pages.size}",Modifier.weight(1f),textAlign=androidx.compose.ui.text.style.TextAlign.Center)
                        TextButton(enabled=prepared!=null&&page<prepared.second.pages.lastIndex,onClick={page++}) {Text("Siguiente")}
                    }
                    ModularPanelSurface(Modifier.heightIn(max=panelMax)) {Column {
                        Row(verticalAlignment=androidx.compose.ui.Alignment.CenterVertically) {
                            Text(module,Modifier.weight(1f).padding(8.dp),maxLines=1)
                            TextButton(onClick={folded=!folded}) {Text(if(folded)"Abrir controles" else "Minimizar")}
                        }
                        if(!folded)Column(Modifier.weight(1f,fill=false).heightIn(max=220.dp).verticalScroll(remember(module) {ScrollState(0)}).padding(horizontal=8.dp,vertical=8.dp).testTag("report-panel"),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                            when(module) {
                                "Documento"->{
                                    OutlinedTextField(title,{if(it.length<=500)title=it},label={Text("Título")},modifier=Modifier.fillMaxWidth(),enabled=!busy)
                                    OutlinedTextField(subtitle,{if(it.length<=1000)subtitle=it},label={Text("Subtítulo opcional")},modifier=Modifier.fillMaxWidth(),enabled=!busy)
                                    OutlinedTextField(date,{date=it.take(10)},label={Text("Fecha del informe · AAAA-MM-DD")},singleLine=true,modifier=Modifier.fillMaxWidth(),enabled=!busy)
                                    Row(Modifier.fillMaxWidth().heightIn(min=48.dp),horizontalArrangement=Arrangement.spacedBy(8.dp),verticalAlignment=androidx.compose.ui.Alignment.CenterVertically) {Switch(index,{index=it},enabled=!busy,modifier=Modifier.heightIn(min=48.dp).testTag("report-index-switch"));Text("Índice",Modifier.weight(1f).testTag("report-index-label"))}
                                    Row(Modifier.fillMaxWidth().heightIn(min=48.dp),horizontalArrangement=Arrangement.spacedBy(8.dp),verticalAlignment=androidx.compose.ui.Alignment.CenterVertically) {Switch(cover,{cover=it},enabled=!busy,modifier=Modifier.heightIn(min=48.dp));Text("Portada separada",Modifier.weight(1f))}
                                }
                                "Imágenes"->{
                                    Text("Las imágenes se incluyen junto al texto. Los audios y sus referencias automáticas se excluyen.")
                                    ReportImageLayout.entries.forEach {mode->FilterChip(imageName==mode.name,{imageName=mode.name},enabled=!busy,label={Text(mode.label)})}
                                }
                                "Salida"->{
                                    ReportFormat.entries.forEach {format->FilterChip(formatName==format.name,{formatName=format.name},enabled=!busy,label={Text(format.label)})}
                                    Text("Vista previa de la maquetación PDF. Word ajusta el texto alrededor de imágenes flotantes y puede paginar de otra forma. Actualiza el índice/campos al abrirlo para obtener sus páginas.")
                                    if(content?.sections?.any {it.depth>9}==true)Text("Word admite nueve niveles de esquema. Se conserva la numeración completa; niveles más profundos usan el estilo del nivel 9.")
                                    Text("El archivo exportado será legible fuera del almacén cifrado y contendrá únicamente el Núcleo o Capa seleccionado y sus descendientes.")
                                }
                            }
                            problem?.let {Text(it,color=MaterialTheme.colorScheme.error)}
                            status?.let {Text(it)}
                        }
                        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal=8.dp,vertical=4.dp),horizontalArrangement=Arrangement.spacedBy(8.dp)) {listOf("Documento","Imágenes","Salida").forEach {name->TextButton(enabled=!busy,onClick={module=name;folded=false},modifier=Modifier.heightIn(min=48.dp).testTag("report-tab:$name")) {Text(name)}}}
                        PrimaryControlButton(onClick={problem=null;picker.launch(null)},enabled=!busy&&content!=null&&prepared?.first==options,modifier=Modifier.fillMaxWidth().padding(horizontal=8.dp,vertical=8.dp).heightIn(min=48.dp).testTag("report-export")) {Text(if(busy)"Exportando…" else "Exportar ${options.format.label}")}
                    }}
                }
            }
        }
    }
    if(busy)AlertDialog(onDismissRequest={},text={Row {CircularProgressIndicator(Modifier.size(24.dp));Text(" Exportando documento…")}},confirmButton={})
}

/** Same transformable/pager primitives as the attachment viewer and Antisocial.
 * Text is drawn from the PDF plan at the current scale, rather than enlarging a screenshot. */
@Composable internal fun ReportPreview(content:ReportContent,plan:ReportPlan,page:Int,onPage:(Int)->Unit,modifier:Modifier=Modifier,onPageClick:(()->Unit)?=null) {
    val selected=page.coerceIn(0,plan.pages.lastIndex)
    val pager=rememberPagerState(initialPage=selected,pageCount={plan.pages.size})
    var zoom by rememberSaveable(plan,selected) {mutableFloatStateOf(1f)}
    var x by rememberSaveable(plan,selected) {mutableFloatStateOf(0f)}
    var y by rememberSaveable(plan,selected) {mutableFloatStateOf(0f)}
    var viewport by remember {mutableStateOf(IntSize.Zero)}
    val notify by rememberUpdatedState(onPage)
    LaunchedEffect(selected) {if(pager.currentPage!=selected)pager.scrollToPage(selected)}
    LaunchedEffect(pager) {snapshotFlow {pager.settledPage}.collect {notify(it)}}
    val transform=rememberTransformableState {factor,pan,_->
        zoom=(zoom*factor).coerceIn(1f,5f)
        val fit=minOf(viewport.width.toFloat()/ReportLayout.WIDTH,viewport.height.toFloat()/ReportLayout.HEIGHT)
        val boundX=((ReportLayout.WIDTH*fit*zoom-viewport.width)/2).coerceAtLeast(0f)
        val boundY=((ReportLayout.HEIGHT*fit*zoom-viewport.height)/2).coerceAtLeast(0f)
        x=(x+pan.x).coerceIn(-boundX,boundX);y=(y+pan.y).coerceIn(-boundY,boundY)
    }
    VerticalPager(pager,modifier.testTag("document-preview"),userScrollEnabled=zoom<=1f) {index->
        val active=index==pager.currentPage
        Canvas(Modifier.fillMaxSize().clipToBounds().onSizeChanged {viewport=it}
            .then(if(active)Modifier.transformable(transform,canPan={zoom>1f}) else Modifier)
            .then(if(active&&onPageClick!=null)Modifier.clickable(onClickLabel="Ampliar página",onClick=onPageClick) else Modifier)
            .testTag("document-preview-page:$index").semantics {stateDescription="Página ${index+1}; zoom ${((if(active)zoom else 1f)*100).toInt()} %; desplazamiento ${if(active)x.toInt() else 0},${if(active)y.toInt() else 0}"}) {
            val scale=minOf(size.width/ReportLayout.WIDTH,size.height/ReportLayout.HEIGHT)*(if(active)zoom else 1f)
            val canvas=drawContext.canvas.nativeCanvas;canvas.save()
            try {
                canvas.translate((size.width-ReportLayout.WIDTH*scale)/2+(if(active)x else 0f),(size.height-ReportLayout.HEIGHT*scale)/2+(if(active)y else 0f))
                canvas.scale(scale,scale);ReportLayout.draw(canvas,plan.pages[index],content,index+1,plan.pages.size)
            } finally {canvas.restore()}
        }
    }
}
