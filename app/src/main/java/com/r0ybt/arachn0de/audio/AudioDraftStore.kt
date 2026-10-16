package com.r0ybt.arachn0de.audio

import android.content.Context
import com.r0ybt.arachn0de.security.SecureFiles
import com.r0ybt.arachn0de.data.local.*
import com.r0ybt.arachn0de.data.repository.AttachmentRepository
import com.r0ybt.arachn0de.domain.model.AttachmentReferences
import com.r0ybt.arachn0de.backup.verifyBackupAttachment
import androidx.room.withTransaction
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject
import java.io.*
import java.util.UUID
import java.security.MessageDigest

/** Every published segment is authenticated and durable. Journals never expire by age. */
internal class AudioDraftStore(private val context:Context,private val db:Arachn0deDatabase,private val syncDirectory:(File)->Unit=::syncAudioDirectory) {
    var incompleteRecovered=false
        private set
    private val root=File(context.filesDir,"audio-drafts")
    private fun directory(id:String):File {require(UUID.fromString(id).toString()==id);return File(root,id).also {require(it.canonicalFile.parentFile==root.canonicalFile)}}
    private fun sync(dir:File)=syncDirectory(dir)
    suspend fun begin(owner:String,onReserved:(String)->Unit={}):String {
        val id=UUID.randomUUID().toString()
        db.withTransaction {
            val waiting=db.attachmentDao().recording(owner)?.takeIf {it.state=="WAITING"}
            db.attachmentDao().beginRecording(AudioRecordingOperationEntity(id,"RECORDING",owner,waiting?.provisionalTitle))
            if(waiting!=null) db.attachmentDao().releaseRecording(owner)
        }
        onReserved(id)
        val dir=directory(id);check(dir.mkdirs())
        SecureFiles.write(File(dir,"owner.json"),JSONObject().put("nodeId",owner).toString().toByteArray());sync(dir);sync(root);sync(root.parentFile!!)
        return id
    }
    fun append(id:String,number:Int,bytes:ByteArray) {
        require(bytes.isNotEmpty() && number>=0);val dir=directory(id)
        val existing=File(dir,"$number.aac")
        if(existing.isFile) {require(SecureFiles.read(existing).contentEquals(bytes));sync(dir);return}
        val part=File(dir,"$number.part");SecureFiles.write(part,bytes)
        check(part.renameTo(File(dir,"$number.aac")));sync(dir)
    }
    suspend fun pending():List<String> = (db.attachmentDao().recordings().map {it.id}+root.listFiles().orEmpty().filter {it.isDirectory && it.name.matches(Regex("[a-f0-9-]{36}")) && File(it,"owner.json").isFile}.map {it.name}).distinct()
    fun owner(id:String):String?=runCatching {JSONObject(SecureFiles.text(File(directory(id),"owner.json"))).getString("nodeId")}.getOrNull()
    private fun pieces(id:String)=directory(id).listFiles().orEmpty().filter {it.isFile && it.name.matches(Regex("[0-9]+\\.aac"))}.sortedBy {it.nameWithoutExtension.toInt()}
    suspend fun finish(id:String,allowIncomplete:Boolean=false):AttachmentFileEntity?=withContext(Dispatchers.IO) {
        AttachmentRepository.fileOperations.withLock {
            val dir=directory(id);val dao=db.attachmentDao()
            val operation=dao.recording(id)
            val phase=operation?.state
            if(phase=="WAITING") {abandonWaiting(operation!!);return@withLock null}
            val existing=dao.get(id)
            if(phase=="DISCARDED") {
                val store=AttachmentStore(context,syncDirectory)
                if(dao.references(id)==0) {store.release(id);val name=existing?.storageName ?: "$id.aac";require(name !in store.backupProtectedNames() && !store.reserved(id));check(store.delete(name));if(existing!=null) dao.delete(id)}
                discardFiles(id);dao.releaseRecording(id);return@withLock null
            }
            if(phase=="COMMITTED") {
                if(existing!=null && dao.references(id)>0) verifyBackupAttachment(existing,AttachmentStore(context,syncDirectory).file(existing.storageName))
                AttachmentStore(context,syncDirectory).release(id);discardFiles(id);dao.releaseRecording(id);return@withLock existing
            }
            if(!File(dir,"owner.json").exists() && pieces(id).isEmpty()) {operation?.let {pruneEmpty(it.nodeId,it.provisionalTitle)};discardFiles(id);dao.releaseRecording(id);return@withLock null}
            val owner=operation?.nodeId ?: JSONObject(SecureFiles.text(File(dir,"owner.json"))).getString("nodeId")
            dao.beginRecording(AudioRecordingOperationEntity(id,"RECORDING",owner))
            var incomplete=false
            dir.listFiles().orEmpty().filter {it.isFile && it.name.matches(Regex("[0-9]+\\.part"))}.forEach {part->
                val target=File(dir,"${part.nameWithoutExtension}.aac")
                if(!target.exists()) {
                    if(runCatching {SecureFiles.input(part).use(AacFrames::duration)}.isSuccess) {check(part.renameTo(target));sync(dir)}
                    else incomplete=true
                }
            }
            require(!incomplete || allowIncomplete) {"El fragmento final está incompleto. Se conserva hasta que decidas finalizar o descartar."}
            incompleteRecovered=incomplete
            val pieces=pieces(id)
            if(pieces.isEmpty()) {require(!incomplete) {"No hay fragmentos completos. El material parcial se conserva hasta que decidas descartarlo."};pruneEmpty(owner,operation?.provisionalTitle);dao.recordingState(id,"DISCARDED");discardFiles(id);dao.releaseRecording(id);return@withLock null}
            // If the owner was deleted while recording, retain the journal for explicit recovery.
            requireNotNull(db.nodeDao().getById(owner)) {"La tarea ya no existe. La grabación recuperable se conserva."}
            val store=AttachmentStore(context,syncDirectory);val target=store.file("$id.aac")
            val staging=File(context.filesDir,"attachments/staging");check(staging.isDirectory || staging.mkdirs())
            SecureFiles.write(File(staging,"$id.lease"),"audio:$id".toByteArray());sync(staging)
            val part=File(staging,"$id.part");val digest=MessageDigest.getInstance("SHA-256");var size=0L
            SecureFiles.output(part).use {out->pieces.forEach {piece->SecureFiles.input(piece).use {input->val b=ByteArray(8192);while(true) {val n=input.read(b);if(n<0) break;size+=n;require(size<=20L*1024*1024) {"Audio demasiado grande para el respaldo."};out.write(b,0,n);digest.update(b,0,n)}}}}
            val duration=SecureFiles.input(part).use(AacFrames::duration)
            check(part.renameTo(target));sync(target.parentFile!!);sync(staging)
            val row=AttachmentFileEntity(id,target.name,"Audio-$id.aac","audio/aac",size,0,0,digest.digest().joinToString("") {"%02x".format(it)},System.currentTimeMillis(),durationMs=duration)
            db.withTransaction {
                if(dao.get(id)==null) dao.insert(row)
                val node=requireNotNull(db.nodeDao().getById(owner))
                dao.attachNode(listOf(NodeAttachmentEntity(owner,id)))
                val description=if(id in AttachmentReferences.ids(node.description)) node.description else node.description+"\n"+AttachmentReferences.audioToken(id)
                check(db.nodeDao().updateContent(owner,node.title,description,node.updatedAt)==1)
                dao.recordingState(id,"COMMITTED")
            }
            // Physical cleanup failure cannot resurrect an explicitly removed, committed audio.
            withContext(NonCancellable) {runCatching {store.release(id);discardFiles(id);dao.releaseRecording(id)}.onFailure {android.util.Log.w("Audio","Committed recording cleanup deferred")}}
            row
        }
    }
    suspend fun discard(id:String)=withContext(Dispatchers.IO) {AttachmentRepository.fileOperations.withLock {
        val dao=db.attachmentDao();dao.beginRecording(AudioRecordingOperationEntity(id,"DISCARDED"));dao.recordingState(id,"DISCARDED")
        dao.recording(id)?.let {pruneEmpty(it.nodeId,it.provisionalTitle)}
        val store=AttachmentStore(context,syncDirectory)
        if(dao.references(id)==0) {
            val row=dao.get(id);val name=row?.storageName ?: "$id.aac"
            store.release(id)
            if(row!=null) dao.pending(id)
            require(dao.storageReferences(name)==if(row==null) 0 else 1)
            require(name !in store.backupProtectedNames() && !store.reserved(id))
            check(store.delete(name));if(row!=null) dao.delete(id)
        }
        discardFiles(id);dao.releaseRecording(id)
    }}
    suspend fun abandonPendingTask(owner:String)=withContext(Dispatchers.IO) {AttachmentRepository.fileOperations.withLock {
        db.attachmentDao().recording(owner)?.takeIf {it.state=="WAITING"}?.let {abandonWaiting(it)}
    }}
    private suspend fun abandonWaiting(operation:AudioRecordingOperationEntity) {
        db.withTransaction {pruneEmpty(operation.nodeId,operation.provisionalTitle);db.attachmentDao().releaseRecording(operation.id)}
    }
    private suspend fun pruneEmpty(owner:String?,title:String?) {
        if(owner==null || title==null) return
        db.withTransaction {
            val node=db.nodeDao().getById(owner) ?: return@withTransaction
            if(node.title==title && node.description.isBlank() && node.purpose=="ACTION" && !node.isCompleted && node.priority=="NONE" && node.startAt==null && node.dueAt==null && node.amountMinor==null && node.currencyCode==null && node.workState==null && !db.nodeDao().hasChildren(node.projectId,node.id) && db.attachmentDao().forNode(owner).isEmpty() && db.personDao().assignmentsForNodes(listOf(owner)).isEmpty() && db.technologyDao().forNodes(listOf(owner)).isEmpty() && db.tagDao().nodeTags().none {it.nodeId==owner}) db.nodeDao().delete(owner)
        }
    }
    private fun discardFiles(id:String) {
        val dir=directory(id)
        dir.listFiles().orEmpty().filter {it.isFile && (it.name=="owner.json" || it.name.matches(Regex("[0-9]+\\.(aac|part)")))}.forEach {check(it.delete())}
        check(!dir.exists() || dir.delete());if(root.exists()) sync(root)
    }
    suspend fun recover():List<String> {
        val failures=mutableListOf<String>()
        for(id in pending()) try {finish(id)} catch(c:CancellationException) {throw c} catch(e:Exception) {failures.add(id)}
        return failures
    }
    companion object {
        suspend fun requireSettled(context:Context,db:Arachn0deDatabase) {require(db.attachmentDao().recordings().none {it.state in setOf("RECORDING","WAITING")} && File(context.filesDir,"audio-drafts").listFiles().orEmpty().none {it.isDirectory && File(it,"owner.json").exists()}) {"Finaliza o recupera la grabación antes de respaldar o restaurar."}}
    }
}

private fun syncAudioDirectory(dir:File) {
    val fd=android.system.Os.open(dir.path,android.system.OsConstants.O_RDONLY,0)
    try {android.system.Os.fsync(fd)} finally {android.system.Os.close(fd)}
}
