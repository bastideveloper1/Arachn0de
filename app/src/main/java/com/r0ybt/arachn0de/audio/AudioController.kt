package com.r0ybt.arachn0de.audio

import androidx.room.withTransaction
import android.content.Context
import android.content.Intent
import android.media.*
import android.os.Build
import com.r0ybt.arachn0de.security.VaultSession
import com.r0ybt.arachn0de.security.SecureFiles
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.Mutex
import java.io.File

internal data class AudioState(val recording:Boolean=false,val paused:Boolean=false,val durationMs:Long=0,val fileId:String?=null,val owner:String?=null,val title:String="Audio",val positionMs:Long=0,val error:String?=null,val loading:Boolean=false) {
    val visible get()=recording || fileId!=null
}
internal class AudioController(private val base:Context,private val session:VaultSession) {
    val state=MutableStateFlow(AudioState())
    val editorBarHosts=MutableStateFlow(0)
    val drafts=AudioDraftStore(session.context,session.database)
    private val scope=CoroutineScope(SupervisorJob()+Dispatchers.Main.immediate+CoroutineExceptionHandler {_,_->state.value=state.value.copy(error="La operación de audio no pudo completarse. El material recuperable se conserva.")})
    private val operations=Mutex()
    private var capture:AudioCapture?=null
    private var captureJob:Job?=null
    private var player:MediaPlayer?=null
    private var prepareJob:Job?=null
    private var playGeneration=0L
    private var source:PrivateAudioSource?=null
    private var ticker:Job?=null
    private var saving=false
    @Volatile private var recovering=true
    @Volatile var isClosed=false
        private set
    @Volatile private var activeDraft:String?=null
    private val manager=base.getSystemService(AudioManager::class.java)
    private val focus=AudioManager.OnAudioFocusChangeListener {change->if(change<=0) scope.launch {pause()}}
    private var request:AudioFocusRequest?=null
    val active get()=state.value.let {it.recording || (it.fileId!=null && (!it.paused || it.loading))}
    val canToggle get()=!isClosed && if(state.value.recording) capture!=null else player!=null && prepareJob?.isActive!=true
    val canStartRecording get()=!isClosed && !state.value.visible && !saving && !recovering
    private fun attributes()=AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build()
    private fun acquireFocus():Boolean {
        val result=if(Build.VERSION.SDK_INT>=26) {
            val req=AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN).setAudioAttributes(attributes()).setOnAudioFocusChangeListener(focus).build();request=req;manager.requestAudioFocus(req)
        } else @Suppress("DEPRECATION") manager.requestAudioFocus(focus,AudioManager.STREAM_MUSIC,AudioManager.AUDIOFOCUS_GAIN)
        return result==AudioManager.AUDIOFOCUS_REQUEST_GRANTED
    }
    private fun abandonFocus() {if(Build.VERSION.SDK_INT>=26) request?.let {manager.abandonAudioFocusRequest(it)} else @Suppress("DEPRECATION") manager.abandonAudioFocus(focus);request=null}
    suspend fun recover() {
        try {val pending=drafts.pending();if(pending.isNotEmpty()) {
            val failed=drafts.recover();val retained=failed.firstOrNull {session.database.attachmentDao().recording(it)?.state=="RECORDING"}
            activeDraft=retained
            state.value=AudioState(recording=retained!=null,paused=retained!=null,owner=retained?.let(drafts::owner),error=if(failed.isEmpty()) "Se conservó el audio de una grabación interrumpida en su tarea. Revisa su final: el último fragmento en curso puede estar incompleto." else "Hay una grabación recuperable pendiente. No se ha eliminado; vuelve a desbloquear para reintentar.")
        }} finally {recovering=false}
    }
    init {scope.launch(Dispatchers.IO) {recover()}}

    fun startRecording(owner:String) {
        if(isClosed || state.value.visible || saving || recovering) {scope.launch(Dispatchers.IO) {drafts.abandonPendingTask(owner)};state.value=state.value.copy(error="Finaliza o cierra el audio actual primero.");return}
        // The request originates in foreground UI after the runtime permission succeeds.
        val application=base.applicationContext as? com.r0ybt.arachn0de.Arachn0deApplication
        if(application!=null && (application.security.current.value!==session || application.security.backgrounded.value)) {
            scope.launch(Dispatchers.IO) {drafts.abandonPendingTask(owner)}
            state.value=AudioState(error="Abre Arachn0de para iniciar la grabación.");return
        }
        playGeneration++
        state.value=AudioState(recording=true,paused=true,owner=owner)
        try {val intent=Intent(base,AudioService::class.java).putExtra("record",owner).putExtra("profile",session.access.id.toString());if(Build.VERSION.SDK_INT>=26) base.startForegroundService(intent) else base.startService(intent)}
        catch(e:Exception) {scope.launch {pruneEmptyTask(owner)};state.value=AudioState(error="Android no permitió iniciar la grabación. Inténtalo con Arachn0de visible.")}
    }
    fun startFailed() {scope.launch {
        val message="Android rechazó el servicio de audio. Abre Arachn0de y revisa sus permisos."
        if(state.value.recording) {state.value=state.value.copy(error=message);finish()}
        else {closePlayback();state.value=AudioState(error=message)}
    }}
    fun beginCapture(owner:String) {
        if(captureJob!=null) return
        captureJob=scope.launch {
            try {
                val node=withContext(Dispatchers.IO) {requireNotNull(session.database.nodeDao().getById(owner))}
                if(saving) return@launch
                require(node.purpose=="ACTION") {"El audio se graba en una Tarea."}
                require(acquireFocus()) {"El micrófono/audio está ocupado."}
                val id=withContext(Dispatchers.IO) {com.r0ybt.arachn0de.data.repository.AttachmentRepository.fileOperations.withLock {drafts.begin(owner) {activeDraft=it}}};activeDraft=id
                if(saving) return@launch
                val engine=AudioCapture(drafts,id,progress={ms->state.update {it.copy(durationMs=ms)}},interrupted={state.value=state.value.copy(paused=true,error="Android ha interrumpido el micrófono. El audio anterior se conserva; continúa cuando esté disponible.")});capture=engine
                state.value=AudioState(recording=true,owner=owner,title=node.title)
                engine.run()
                if(engine.limitReached) state.value=state.value.copy(error="Se alcanzó el límite existente de 20 MiB por adjunto. La grabación se finalizó y se conserva.")
            } catch(c:CancellationException) {throw c} catch(e:Exception) {state.value=state.value.copy(error="Captura interrumpida. El material recuperable se conserva.")}
            finally {capture=null;if(!saving) scope.launch {finish(allowIncomplete=false)}}
        }
    }
    suspend fun finish(discard:Boolean=false,allowIncomplete:Boolean=true) = withContext(NonCancellable) { operations.withLock {
        if(!state.value.recording && activeDraft==null && captureJob==null) return@withLock
        saving=true
        try {
            capture?.stop();captureJob?.join();captureJob=null
            val id=activeDraft
            if(id!=null) {if(discard) drafts.discard(id) else {drafts.finish(id,allowIncomplete=allowIncomplete);if(drafts.incompleteRecovered) state.value=state.value.copy(error="Se conservaron los fragmentos completos. El último fragmento interrumpido no pudo recuperarse.")}}
            activeDraft=null;state.value.owner?.let {pruneEmptyTask(it)};abandonFocus();state.value=AudioState(error=state.value.error)
        } catch(e:Exception) {state.value=state.value.copy(recording=activeDraft!=null,paused=true,error="No se pudo finalizar. La grabación se conserva; puedes reintentar o descartar.")}
        finally {saving=false;stopService()}
        }}
    fun play(id:String) {
        if(isClosed || state.value.recording || saving || recovering) {state.value=state.value.copy(error="Finaliza la grabación primero.");return}
        val generation=++playGeneration
        scope.launch {
            try {
                closePlayback(switching=true)
                val row=withContext(Dispatchers.IO) {requireNotNull(session.database.attachmentDao().get(id)).also {require(it.mimeType=="audio/aac")}}
                val owners=withContext(Dispatchers.IO) {session.database.attachmentDao().nodeAttachments().filter {it.attachmentId==id}}
                val title=withContext(Dispatchers.IO) {owners.firstOrNull()?.nodeId?.let {session.database.nodeDao().getById(it)?.title}} ?: row.originalName
                if(generation!=playGeneration) return@launch
                state.value=AudioState(fileId=id,owner=owners.firstOrNull()?.nodeId,title=title,paused=true,durationMs=row.durationMs,positionMs=row.playbackMs,loading=true)
                val intent=Intent(base,AudioService::class.java).putExtra("play",id).putExtra("profile",session.access.id.toString())
                if(Build.VERSION.SDK_INT>=26) base.startForegroundService(intent) else base.startService(intent)
            } catch(e:Exception) {state.value=AudioState(error="No se pudo abrir el audio.")}
        }
    }
    fun beginPlayback(id:String) {
        if(player!=null || prepareJob?.isActive==true || state.value.fileId!=id) return
        prepareJob=scope.launch {
            try {
                val row=withContext(Dispatchers.IO) {requireNotNull(session.database.attachmentDao().get(id))}
                source=withContext(Dispatchers.IO) {PrivateAudioSource(session.attachments.localFile(row),session.access)}
                val media=MediaPlayer();player=media;media.setAudioAttributes(attributes());media.setDataSource(source!!)
                media.setOnCompletionListener {scope.launch {state.value=state.value.copy(paused=true,positionMs=state.value.durationMs,loading=false);persistPosition();abandonFocus();notifyInactive()}}
                media.setOnErrorListener {_,_,_->scope.launch {closePlayback();state.value=AudioState(error="La reproducción se interrumpió; el archivo se conserva.")};true}
                withContext(Dispatchers.IO) {media.prepare()};media.seekTo((if(state.value.positionMs>=row.durationMs) 0 else state.value.positionMs).toInt())
                val start=state.value.loading && acquireFocus()
                if(start) media.start()
                state.value=state.value.copy(paused=!start,loading=false)
                if(!start) notifyInactive()
                ticker=scope.launch {var ticks=0;while(isActive && player===media) {delay(500);if(!state.value.paused) {state.value=state.value.copy(positionMs=media.currentPosition.toLong().coerceIn(0,state.value.durationMs));if(++ticks%2==0) persistPosition()}}}
            } catch(c:CancellationException) {throw c}
            catch(e:Exception) {player?.release();player=null;source?.close();source=null;state.value=AudioState(error="No se pudo reproducir el audio.");stopService()}
        }
    }
    suspend fun pause() {
        if(state.value.recording) {capture?.paused?.set(true);state.value=state.value.copy(paused=true)}
        else {runCatching {player?.pause()};val position=runCatching {player?.currentPosition?.toLong()}.getOrNull() ?: state.value.positionMs;state.value=state.value.copy(paused=true,loading=false,positionMs=position.coerceIn(0,state.value.durationMs));persistPosition()}
        abandonFocus();notifyInactive()
    }
    suspend fun toggle() {
        if(!canToggle) return
        if(!state.value.paused) pause() else if(acquireFocus()) {
            if(state.value.recording) capture?.paused?.set(false) else {if(state.value.positionMs>=state.value.durationMs) {player?.seekTo(0);state.value=state.value.copy(positionMs=0)};player?.start()}
            state.value=state.value.copy(paused=false)
        }
    }
    suspend fun seek(ms:Long) {val position=ms.coerceIn(0,state.value.durationMs);if(prepareJob?.isActive!=true) player?.seekTo(position.toInt());state.value=state.value.copy(positionMs=position);persistPosition()}
    suspend fun seekFile(id:String,ms:Long) {
        val row=withContext(Dispatchers.IO) {session.database.attachmentDao().get(id)} ?: return
        require(row.mimeType=="audio/aac")
        val at=ms.coerceIn(0,row.durationMs)
        if(state.value.fileId==id) seek(at) else withContext(Dispatchers.IO) {session.database.attachmentDao().playback(id,at)}
    }
    suspend fun bookmarkFile(id:String,position:Long)=withContext(NonCancellable+Dispatchers.IO) {operations.withLock {
        if(isClosed) return@withLock
        session.database.withTransaction {
            val row=session.database.attachmentDao().get(id) ?: return@withTransaction
            require(row.mimeType=="audio/aac")
            session.database.attachmentDao().bookmark(id,if(row.bookmarkMs==null) position.coerceIn(0,row.durationMs) else null)
        }
    }}
    suspend fun bookmark() {val s=state.value;s.fileId?.let {bookmarkFile(it,s.positionMs)}}
    suspend fun returnToBookmark() {val id=state.value.fileId ?: return;val ms=withContext(Dispatchers.IO) {session.database.attachmentDao().get(id)?.bookmarkMs} ?: return;seek(ms)}
    private suspend fun persistPosition() {val s=state.value;s.fileId?.let {withContext(Dispatchers.IO) {session.database.attachmentDao().playback(it,s.positionMs.coerceIn(0,s.durationMs))}}}
    suspend fun closePlayback(switching:Boolean=false) = withContext(NonCancellable) { operations.withLock {
        if(isClosed && !state.value.visible && player==null && source==null) return@withLock
        if(!switching) playGeneration++
        val preparing=prepareJob;prepareJob=null
        if(preparing!==currentCoroutineContext()[Job]) preparing?.cancelAndJoin()
        persistPosition();ticker?.cancel();ticker=null;player?.release();player=null;source?.close();source=null;abandonFocus()
        if(!switching) {if(!state.value.recording) state.value=AudioState();stopService()}
        }}
    fun dispose() {isClosed=true;capture?.stop();runCatching {player?.release()};source?.close();abandonFocus();scope.cancel()}
    suspend fun lock() {isClosed=true;if(state.value.recording || activeDraft!=null) finish(allowIncomplete=false) else closePlayback();state.value=AudioState();operations.withLock {};scope.coroutineContext[Job]?.cancelAndJoin()}
    private suspend fun pruneEmptyTask(owner:String) {drafts.abandonPendingTask(owner)}
    fun serviceDestroyed() {if(capture!=null) command("service-stop") else if(player!=null) command("close")}
    suspend fun beforeOwnersDeleted(ids:Set<String>) {
        val s=state.value
        val owners=if(s.recording) listOfNotNull(s.owner) else s.fileId?.let {file->withContext(Dispatchers.IO) {session.database.attachmentDao().nodeAttachments().filter {it.attachmentId==file}.map {it.nodeId}}}.orEmpty()
        for(owner in owners) {
            var node=withContext(Dispatchers.IO) {session.database.nodeDao().getById(owner)}
            val visited=hashSetOf<String>()
            while(node!=null && visited.add(node.id)) {
                if(node.id in ids) {if(state.value.recording) finish() else closePlayback();check(activeDraft==null) {"Finaliza o recupera la grabación antes de continuar."};return}
                node=node.parentId?.let {id->withContext(Dispatchers.IO) {session.database.nodeDao().getById(id)}}
            }
        }
    }
    suspend fun beforeProjectDeleted(id:String) {
        val s=state.value
        if(s.fileId!=null && withContext(Dispatchers.IO) {session.database.attachmentDao().forProject(id).any {it.attachmentId==s.fileId}}) {closePlayback();return}
        val roots=withContext(Dispatchers.IO) {session.database.nodeDao().getProjectNodes(id).mapTo(hashSetOf()) {it.id}}
        beforeOwnersDeleted(roots)
    }
    suspend fun beforeDelete(id:String) {if(state.value.fileId==id) closePlayback()}
    private fun notifyInactive() {(base.applicationContext as? com.r0ybt.arachn0de.Arachn0deApplication)?.security?.audioStopped(session)}
    private fun stopService() {base.stopService(Intent(base,AudioService::class.java));base.getSystemService(android.app.NotificationManager::class.java)?.cancel(AudioService.NOTIFICATION);notifyInactive()}
    fun command(action:String) {scope.launch {when(action) {"toggle"->toggle();"finish"->finish();"service-stop"->finish(allowIncomplete=false);"close"->if(state.value.recording) pause() else closePlayback();"back"->seek(state.value.positionMs-10000);"forward"->seek(state.value.positionMs+10000)}}}
}

/** Seekable, bounded memory copy; no cleartext file and no cross-session handles. */
private class PrivateAudioSource(private val file:File,private val owner:SecureFiles.Session):MediaDataSource() {
    private var bytes=SecureFiles.input(file).use {input->val output=WipingByteArrayOutputStream();val b=ByteArray(8192);while(true) {val n=input.read(b);if(n<0) break;require(output.size()+n<=20*1024*1024);output.write(b,0,n)};b.fill(0);output.toByteArray().also {output.erase()}}
    init {try {owner.retain(this)} catch(e:Exception) {bytes.fill(0);throw e}}
    @Synchronized override fun readAt(position:Long,buffer:ByteArray,offset:Int,size:Int):Int {SecureFiles.requireActive(file);if(position<0 || position>=bytes.size) return -1;val count=minOf(size,bytes.size-position.toInt());bytes.copyInto(buffer,offset,position.toInt(),position.toInt()+count);return count}
    @Synchronized override fun getSize()=bytes.size.toLong()
    @Synchronized override fun close() {bytes.fill(0);bytes=ByteArray(0);owner.release(this)}
}
