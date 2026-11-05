package com.r0ybt.arachn0de.ui

import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.r0ybt.arachn0de.annotations.*
import com.r0ybt.arachn0de.data.local.AttachmentFileEntity
import com.r0ybt.arachn0de.data.repository.AttachmentRepository
import com.r0ybt.arachn0de.ui.theme.Arachn0deColors
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.withLock

@Composable internal fun ImageAnnotationEditor(row: AttachmentFileEntity, repository: AttachmentRepository,
    draftId: String?, onSaved: (String) -> Unit, onClose: () -> Unit) {
    val storage=privateStorageContext(LocalContext.current)
    var image by remember(row.id,storage) {mutableStateOf<Bitmap?>(null)}
    var loadError by remember(row.id,storage) {mutableStateOf(false)}
    var stylePreferences by remember(row.id,storage) {mutableStateOf<AnnotationStylePreferences?>(null)}
    var initialStyle by remember(row.id,storage) {mutableStateOf(AnnotationStyle())}
    LaunchedEffect(row.id,storage) {
        try {
            val settings=withContext(Dispatchers.IO) {
                AnnotationStylePreferences.operations.withLock {
                    AnnotationStylePreferences(storage.getSharedPreferences(AnnotationStylePreferences.NAME,0)).let {it to it.read()}
                }
            }
            stylePreferences=settings.first;initialStyle=settings.second
            image=withContext(Dispatchers.IO) {
                val bytes=com.r0ybt.arachn0de.audio.originalAttachmentBytes(repository,row)
                // Historical editable PNGs already contain the rendered annotations.
                try {decodeAnnotationSource(bytes)} finally {bytes.fill(0)}
            }
        } catch(c:CancellationException) {throw c}
        catch(e:Exception) {loadError=true}
    }
    val loaded=image
    if(loaded==null) {
        BottomModulesDialog(onDismissRequest=onClose) {
            Column(Modifier.padding(24.dp)) {
                Text(if(loadError) "No se pudo abrir la imagen. El original se conserva." else "Abriendo imagen…")
                TextButton(onClick=onClose) {Text("Cancelar")}
            }
        }
    } else ImageAnnotationWorkspace(row,repository,draftId,loaded,requireNotNull(stylePreferences),initialStyle,onSaved,onClose)
}

@Composable private fun ImageAnnotationWorkspace(row:AttachmentFileEntity,repository:AttachmentRepository,
    draftId:String?,image:Bitmap,preferences:AnnotationStylePreferences,initialStyle:AnnotationStyle,onSaved:(String)->Unit,onClose:()->Unit) {
    val scope=rememberCoroutineScope()
    var encoded by rememberSaveable(row.id) {mutableStateOf("[]")}
    val history=remember(row.id) {MarkHistory(ImageMarkCodec.decode(encoded))}
    var marks by remember(row.id) {mutableStateOf(history.current)}
    var tool by rememberSaveable(row.id) {mutableStateOf(MarkTool.RECT)}
    var color by rememberSaveable(row.id) {mutableIntStateOf(initialStyle.color)}
    var width by rememberSaveable(row.id) {mutableFloatStateOf(initialStyle.width)}
    val styleWrites=AnnotationStylePreferences.operations
    val colorScroll=rememberScrollState()
    val widthScroll=rememberScrollState()
    var styleOpen by remember {mutableStateOf(false)}
    var busy by remember {mutableStateOf(false)}
    var error by remember {mutableStateOf<String?>(null)}
    fun rememberStyle() {
        val value=AnnotationStyle(color,width)
        // Queue in interaction order and finish these small preference writes even on close.
        scope.launch(start=CoroutineStart.UNDISPATCHED) {withContext(NonCancellable) {
            try {styleWrites.withLock {withContext(Dispatchers.IO) {preferences.save(value)}}}
            catch(_:Exception) {error="No se pudo recordar el color y grosor. Los trazos se conservan."}
        }}
    }
    fun commit(next:List<ImageMark>) {
        try {history.commit(next);marks=history.current;encoded=ImageMarkCodec.encode(marks)}
        catch(e:Exception) {marks=history.current;error="Se alcanzó el límite de anotaciones de esta imagen."}
    }
    BackHandler {if(!busy)onClose()}
    BottomModulesDialog(onDismissRequest={if(!busy)onClose()},properties=DialogProperties(usePlatformDefaultWidth=false,decorFitsSystemWindows=false)) {
        Column(Modifier.fillMaxSize().background(Arachn0deColors.Background).safeDrawingPadding().testTag("image-annotation-editor")) {
            Text("Anotar imagen",style=MaterialTheme.typography.titleMedium,modifier=Modifier.padding(horizontal=12.dp,vertical=4.dp))
            ImageAnnotationStage(image,marks,null,AnnotationMode.DRAW,tool,color,width,false,!busy,
                onSelect={},onCommit={commit(it)},modifier=Modifier.weight(1f).fillMaxWidth())
            Surface(color=Arachn0deColors.Surface) {Column {
                HorizontalDivider(color=Arachn0deColors.Outline,thickness=.5.dp)
                AnnotationStrip {
                    listOf(Triple(MarkTool.RECT,"Rectángulo",Icons.Default.CropSquare),
                        Triple(MarkTool.ELLIPSE,"Óvalo",Icons.Default.RadioButtonUnchecked),
                        Triple(MarkTool.ARROW,"Flecha",Icons.Default.ArrowForward),
                        Triple(MarkTool.PEN,"Libre",Icons.Default.Edit)).forEach {(value,label,icon)->
                        FilterChip(selected=tool==value,onClick={tool=value},enabled=!busy,
                            label={Text(label)},leadingIcon={Icon(icon,null,Modifier.size(16.dp))},
                            modifier=Modifier.heightIn(min=48.dp).testTag("annotation-tool:${value.name}"),
                            colors=FilterChipDefaults.filterChipColors(selectedContainerColor=Arachn0deColors.Selection,selectedLabelColor=Arachn0deColors.Accent))
                    }
                }
                Row(Modifier.fillMaxWidth().padding(horizontal=8.dp),verticalAlignment=Alignment.CenterVertically) {
                    TextButton(enabled=!busy,onClick={styleOpen=!styleOpen},modifier=Modifier.weight(1f).heightIn(min=48.dp).testTag("annotation-style-toggle")) {Text("Color y grosor")}
                    TextButton(enabled=!busy&&history.canUndo,onClick={marks=history.undo();encoded=ImageMarkCodec.encode(marks)},modifier=Modifier.heightIn(min=48.dp)) {
                        Icon(Icons.Default.Undo,null,Modifier.size(18.dp));Spacer(Modifier.width(4.dp));Text("Deshacer")
                    }
                }
                if(styleOpen) {
                    AnnotationStrip(colorScroll) {
                        listOf("Blanco" to -1,"Negro" to 0xff000000.toInt(),"Rojo" to 0xffe53935.toInt(),
                            "Amarillo" to 0xffffc107.toInt(),"Verde" to 0xff00c853.toInt(),"Azul" to 0xff2196f3.toInt()).forEach {(label,value)->
                            IconToggleButton(checked=color==value,onCheckedChange={color=value;rememberStyle()},enabled=!busy,
                                modifier=Modifier.size(48.dp).semantics {contentDescription=label}) {
                                Surface(shape=CircleShape,color=Color(value),border=androidx.compose.foundation.BorderStroke(if(color==value)3.dp else 1.dp,Arachn0deColors.TextSecondary),modifier=Modifier.size(24.dp)) {}
                            }
                        }
                    }
                    AnnotationStrip(widthScroll) {
                        listOf("Fino" to .003f,"Medio" to .006f,"Grueso" to .014f).forEach {(label,value)->
                            FilterChip(selected=width==value,onClick={width=value;rememberStyle()},enabled=!busy,label={Text(label)},modifier=Modifier.heightIn(min=48.dp))
                        }
                    }
                }
                Row(Modifier.fillMaxWidth().padding(horizontal=8.dp,vertical=4.dp),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(enabled=!busy,onClick=onClose,modifier=Modifier.weight(1f).heightIn(min=48.dp)) {Text("Cancelar")}
                    PrimaryControlButton(enabled=!busy,onClick={scope.launch {
                        busy=true
                        try {
                            val bytes=withContext(Dispatchers.IO) {
                                val source=com.r0ybt.arachn0de.audio.originalAttachmentBytes(repository,row)
                                try {
                                    val full=decodeAnnotationSource(source,fullSize=true)
                                    try {markedPng(full,marks)} finally {full.recycle()}
                                } finally {source.fill(0)}
                            }
                            try {onSaved(repository.saveAnnotated(row.id,bytes,draftId,replace=true).id)} finally {bytes.fill(0)}
                        } catch(c:CancellationException) {throw c}
                        catch(e:IllegalArgumentException) {error=e.message ?: "La imagen no es válida. La versión anterior se conserva."}
                        catch(e:OutOfMemoryError) {error="No hay memoria suficiente para editar esta imagen. La versión anterior se conserva."}
                        catch(e:Exception) {error="No se pudo guardar la imagen. La versión anterior se conserva."}
                        finally {busy=false}
                    }},modifier=Modifier.weight(1f).heightIn(min=48.dp)) {Text(if(busy) "Guardando…" else "Guardar")}
                }
                error?.let {Text(it,color=Arachn0deColors.TextSecondary,modifier=Modifier.padding(horizontal=12.dp))}
            }}
        }
    }
}

@Composable private fun AnnotationStrip(scroll:ScrollState=rememberScrollState(),content:@Composable RowScope.()->Unit) {
    Row(Modifier.fillMaxWidth().horizontalScroll(scroll).padding(horizontal=8.dp),
        horizontalArrangement=Arrangement.spacedBy(6.dp),verticalAlignment=Alignment.CenterVertically,content=content)
}
