package com.r0ybt.arachn0de.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.r0ybt.arachn0de.Arachn0deApplication
import com.r0ybt.arachn0de.audio.AudioController
import com.r0ybt.arachn0de.security.SecureFiles
import com.r0ybt.arachn0de.data.repository.AttachmentRepository
import com.r0ybt.arachn0de.data.local.AttachmentFileEntity
import com.r0ybt.arachn0de.ui.theme.Arachn0deColors
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.withLock

@Composable internal fun AudioMicButton(enabled:Boolean=true,onReady:()->Unit) {
    val context=LocalContext.current
    var denied by remember {mutableStateOf(false)}
    val ready by rememberUpdatedState(onReady)
    val notices=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {ready()}
    fun permitted() {
        if(android.os.Build.VERSION.SDK_INT>=33 && ContextCompat.checkSelfPermission(context,Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED) notices.launch(Manifest.permission.POST_NOTIFICATIONS)
        else ready()
    }
    val permission=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {allowed->if(allowed) permitted() else denied=true}
    IconButton(enabled=enabled,modifier=Modifier.size(48.dp).testTag("audio-microphone"),onClick={
        if(ContextCompat.checkSelfPermission(context,Manifest.permission.RECORD_AUDIO)==PackageManager.PERMISSION_GRANTED) permitted()
        else permission.launch(Manifest.permission.RECORD_AUDIO)
    }) {Icon(Icons.Default.Mic,"Grabar audio",tint=Arachn0deColors.TextPrimary)}
    if(denied) AlertDialog(onDismissRequest={denied=false},text={Text("Permite el micrófono para grabar. No se creó una tarea ni se inició la captura.")},confirmButton={TextButton(onClick={denied=false}) {Text("Cerrar")}})
}
internal fun audioTime(ms:Long)="%d:%02d".format(ms/60000,ms/1000%60)

/** Original byte export through SAF and a temporary read grant; no cleartext cache. */
@Composable internal fun AttachmentExportActions(row:AttachmentFileEntity?,repository:AttachmentRepository,compact:Boolean=false) {
    val context=LocalContext.current;val app=context.applicationContext as? Arachn0deApplication
    val scope=rememberCoroutineScope();var error by remember {mutableStateOf<String?>(null)}
    val session=app?.security?.current?.value
    val current by rememberUpdatedState(row)
    val export=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(row?.mimeType ?: "application/octet-stream")) {uri->
        val file=current
        if(uri!=null && file!=null && session!=null) scope.launch {
            try {
                check(app.security.current.value===session)
                SecureFiles.requireActive(repository.localFile(file))
                val bytes=com.r0ybt.arachn0de.audio.originalAttachmentBytes(repository,file)
                val reservation=AutoCloseable {bytes.fill(0)}
                try {session.access.retain(reservation);withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri,"wt").use {output->
                        requireNotNull(output);var at=0
                        while(at<bytes.size) {
                            currentCoroutineContext().ensureActive();check(app.security.current.value===session);session.access.checkOpen()
                            val n=minOf(8192,bytes.size-at);output.write(bytes,at,n);at+=n
                        }
                    }
                }} finally {bytes.fill(0);session.access.release(reservation)}
            } catch(c:CancellationException) {throw c} catch(e:Exception) {error="No se pudo guardar la copia. El original se conserva."}
        }
    }
    fun share() {
        val file=row ?: return;val owner=session ?: return
        try {
            check(app?.security?.current?.value===owner)
            val uri=Uri.parse("content://${context.packageName}.attachments/${owner.access.id}/${file.id}")
            val intent=Intent(Intent.ACTION_SEND).setType(file.mimeType).putExtra(Intent.EXTRA_STREAM,uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION).apply {clipData=android.content.ClipData.newRawUri(file.originalName,uri)}
            context.startActivity(Intent.createChooser(intent,"Compartir adjunto"))
        } catch(e:Exception) {error="No se pudo compartir el adjunto."}
    }
    if(compact) {
        IconButton(enabled=row!=null,onClick={row?.let {export.launch(it.originalName)}},modifier=Modifier.size(48.dp)) {Icon(Icons.Default.Download,"Guardar copia")}
        IconButton(enabled=row!=null,onClick={share()},modifier=Modifier.size(48.dp)) {Icon(Icons.Default.Share,"Compartir")}
    } else {
        TextButton(enabled=row!=null,onClick={row?.let {export.launch(it.originalName)}}) {Text("Guardar copia")}
        TextButton(enabled=row!=null,onClick={share()}) {Text("Compartir")}
    }
    if(error!=null) AlertDialog(onDismissRequest={error=null},text={Text(error!!)},confirmButton={TextButton(onClick={error=null}) {Text("Cerrar")}})
}

@Composable internal fun AudioViewer(id:String,repository:AttachmentRepository,onRemove:(()->Unit)?=null,onClose:()->Unit) {
    val session=(LocalContext.current.applicationContext as Arachn0deApplication).security.current.value ?: return
    val audio=session.audio;val state by audio.state.collectAsState();val files by repository.observeFiles().collectAsState(emptyList())
    val row=files.firstOrNull {it.id==id};val scope=rememberCoroutineScope {CoroutineExceptionHandler {_,_->audio.state.value=audio.state.value.copy(error="No se pudo completar la acción de audio. El archivo se conserva.")}}
    var confirm by remember {mutableStateOf(false)}
    val position=if(state.fileId==id) state.positionMs else row?.playbackMs ?: 0
    val playing=state.fileId==id && !state.paused
    AlertDialog(onDismissRequest=onClose,title={Text(row?.originalName ?: "Audio",maxLines=2,overflow=TextOverflow.Ellipsis)},
        text={Column {
            Text("${audioTime(position)} / ${audioTime(row?.durationMs ?: 0)}")
            Slider(value=position.toFloat(),onValueChange={ms->scope.launch {audio.seekFile(id,ms.toLong())}},valueRange=0f..(row?.durationMs ?: 1).coerceAtLeast(1).toFloat(),enabled=row!=null)
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceEvenly) {
                IconButton(onClick={scope.launch {audio.seekFile(id,position-10000)}},enabled=row!=null) {Icon(Icons.Default.Replay10,"Retroceder 10 segundos")}
                IconButton(onClick={if(state.fileId!=id) audio.play(id) else scope.launch {audio.toggle()}},enabled=row!=null && (state.fileId!=id || audio.canToggle)) {Icon(if(playing) Icons.Default.Pause else Icons.Default.PlayArrow,if(playing) "Pausar" else "Reproducir")}
                IconButton(onClick={scope.launch {audio.seekFile(id,position+10000)}},enabled=row!=null) {Icon(Icons.Default.Forward10,"Adelantar 10 segundos")}
                IconButton(onClick={scope.launch {audio.bookmarkFile(id,position)}},enabled=row!=null) {Icon(if(row?.bookmarkMs==null) Icons.Default.BookmarkBorder else Icons.Default.Bookmark,if(row?.bookmarkMs==null) "Guardar marcador" else "Quitar marcador")}
            }
            if(row?.bookmarkMs!=null) TextButton(onClick={scope.launch {audio.seekFile(id,row.bookmarkMs)}} ,enabled=row!=null) {Text("Volver al marcador")}
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceEvenly) {
                AttachmentExportActions(row,repository,compact=true)
                if(onRemove!=null) IconButton(onClick={confirm=true}) {Icon(Icons.Default.Delete,"Eliminar audio")}
            }
        }},confirmButton={TextButton(onClick=onClose) {Text("Cerrar")}})
    if(confirm) AlertDialog(onDismissRequest={confirm=false},text={Text("¿Eliminar este audio de la descripción? La tarea y los demás adjuntos se conservan.")},
        confirmButton={TextButton(onClick={scope.launch {audio.beforeDelete(id);onRemove?.invoke();confirm=false;onClose()}}) {Text("Eliminar")}},dismissButton={TextButton(onClick={confirm=false}) {Text("Cancelar")}})
}

/** One 48-dp row, hosted by the existing footer or the global navigation container. */
internal val LocalAudioFooterVisible=staticCompositionLocalOf {true}

@Composable internal fun AudioBar(inEditor:Boolean=false) {
    if(!LocalAudioFooterVisible.current) return
    val session=(LocalContext.current.applicationContext as? Arachn0deApplication)?.security?.current?.value ?: return
    val hosts by session.audio.editorBarHosts.collectAsState()
    DisposableEffect(session,inEditor) {
        if(inEditor) session.audio.editorBarHosts.value++
        onDispose {if(inEditor) session.audio.editorBarHosts.value=(session.audio.editorBarHosts.value-1).coerceAtLeast(0)}
    }
    if(!inEditor && hosts>0) return
    val audio=session.audio;val state by audio.state.collectAsState();val scope=rememberCoroutineScope {CoroutineExceptionHandler {_,_->audio.state.value=audio.state.value.copy(error="No se pudo completar la acción de audio. El archivo se conserva.")}}
    val files by session.attachments.observeFiles().collectAsState(emptyList())
    val currentFile=files.firstOrNull {it.id==state.fileId}
    var closing by remember {mutableStateOf(false)};var menu by remember {mutableStateOf(false)};var controls by remember {mutableStateOf(false)}
    if(state.visible) CompositionLocalProvider(LocalContentColor provides Arachn0deColors.TextPrimary) {
      Row(Modifier.fillMaxWidth().heightIn(min=48.dp).testTag("audio-bar"),verticalAlignment=Alignment.CenterVertically) {
        Icon(if(state.recording) Icons.Default.Mic else Icons.Default.VolumeUp,null,Modifier.size(20.dp),tint=Arachn0deColors.TextSecondary)
        Row(Modifier.weight(1f).heightIn(min=48.dp).clickable(enabled=!state.recording) {controls=true},verticalAlignment=Alignment.CenterVertically) {
            Text(if(state.recording) (if(state.paused) "Pausa" else "Grabando") else state.title,Modifier.weight(1f),maxLines=1,overflow=TextOverflow.Ellipsis,style=MaterialTheme.typography.bodySmall)
            Text(audioTime(if(state.recording) state.durationMs else state.positionMs),maxLines=1,style=MaterialTheme.typography.bodySmall)
        }
        IconButton(enabled=audio.canToggle,onClick={scope.launch {audio.toggle()}},modifier=Modifier.size(48.dp)) {Icon(if(state.paused) Icons.Default.PlayArrow else Icons.Default.Pause,if(state.paused) "Continuar" else "Pausar")}
        if(state.recording) IconButton(onClick={scope.launch {audio.finish()}},modifier=Modifier.size(48.dp)) {Icon(Icons.Default.Stop,"Finalizar grabación")}
        else IconButton(onClick={scope.launch {audio.bookmark()}},modifier=Modifier.size(48.dp)) {Icon(if(currentFile?.bookmarkMs==null) Icons.Default.BookmarkBorder else Icons.Default.Bookmark,if(currentFile?.bookmarkMs==null) "Guardar marcador" else "Quitar marcador")}
        IconButton(onClick={if(state.recording) closing=true else scope.launch {audio.closePlayback()}},modifier=Modifier.size(48.dp)) {Icon(Icons.Default.Close,"Cerrar audio")}
        Box {
            IconButton(onClick={menu=true},modifier=Modifier.size(48.dp)) {Icon(Icons.Default.MoreHoriz,"Opciones de audio")}
            DropdownMenu(expanded=menu,onDismissRequest={menu=false}) {
                if(state.recording) DropdownMenuItem(text={Text("Descartar grabación")},onClick={menu=false;closing=true})
                else {
                    DropdownMenuItem(text={Text("Retroceder 10 segundos")},onClick={menu=false;scope.launch {audio.seek(state.positionMs-10000)}})
                    DropdownMenuItem(text={Text("Adelantar 10 segundos")},onClick={menu=false;scope.launch {audio.seek(state.positionMs+10000)}})
                    DropdownMenuItem(text={Text("Volver al marcador")},enabled=currentFile?.bookmarkMs!=null,onClick={menu=false;scope.launch {audio.returnToBookmark()}})
                }
            }
        }
    }
    }
    if(controls) state.fileId?.let {AudioViewer(it,session.attachments,onClose={controls=false})}
    if(closing) AlertDialog(onDismissRequest={closing=false},text={Text("Finaliza y conserva el audio, o descarta la grabación.")},confirmButton={TextButton(onClick={scope.launch {audio.finish();closing=false}}) {Text("Finalizar y conservar")}},dismissButton={TextButton(onClick={scope.launch {audio.finish(discard=true);closing=false}}) {Text("Descartar")}})
    state.error?.let {message->AlertDialog(onDismissRequest={audio.state.value=audio.state.value.copy(error=null)},text={Text(message)},confirmButton={TextButton(onClick={audio.state.value=audio.state.value.copy(error=null)}) {Text("Cerrar")}})}
}
