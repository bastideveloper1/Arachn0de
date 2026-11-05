package com.r0ybt.arachn0de.photos

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import java.util.UUID
import com.r0ybt.arachn0de.backup.syncBackupDirectory
import com.r0ybt.arachn0de.data.repository.AttachmentRepository
import com.r0ybt.arachn0de.security.DurableVaultFile
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject

/** External camera output only. Durable states never expire an unresolved camera request. */
internal class CameraCaptureFiles(context:Context,private val sync:(File)->Unit=::syncBackupDirectory,private val delete:(File)->Boolean={it.delete()}) {
    private val context=context.applicationContext
    private val root=File(this.context.cacheDir.canonicalFile,"antisocial-camera").also {require(it.canonicalFile==it.absoluteFile)}
    companion object {private val process=UUID.randomUUID().toString()}
    internal data class Record(val owner:String?,val state:String)
    private fun valid(id:String)=runCatching {UUID.fromString(id).toString()==id}.getOrDefault(false)
    private fun lease(id:String)=File(root,"$id.lease").also {require(valid(id) && it.canonicalFile==it.absoluteFile)}
    fun file(id:String):File {require(valid(id));return File(root,"$id.jpg").also {require(it.canonicalFile==it.absoluteFile)}}
    fun uri(id:String):Uri=FileProvider.getUriForFile(context,"${context.packageName}.photo-camera",file(id))
    private fun <T> files(work:()->T):T=runBlocking {AttachmentRepository.fileOperations.withLock {work()}}
    private fun read(id:String):Record {
        val f=lease(id);DurableVaultFile.recover(f,sync);require(f.isFile && f.length()<=512)
        val text=f.readText()
        // Historical process-only leases do not prove cancellation or successful import.
        if(valid(text))return Record(null,"CAPTURING")
        val j=JSONObject(text);require(j.getInt("version")==1)
        val owner=if(j.isNull("owner"))null else j.getString("owner").also {require(valid(it))}
        val state=j.getString("state");require(state in setOf("CAPTURING","RETURNED","IMPORTED","DISCARD"))
        return Record(owner,state)
    }
    private fun write(id:String,r:Record) {DurableVaultFile.replace(lease(id),JSONObject().put("version",1).put("process",process).put("owner",r.owner ?: JSONObject.NULL).put("state",r.state).toString().toByteArray(),sync);DurableVaultFile.recover(lease(id),sync)}
    fun prepare(owner:UUID?=null):String=files {
        check(root.isDirectory || root.mkdirs())
        require(records().count {it.second.owner!=null}<8) {"Hay capturas pendientes de recuperar."}
        val id=UUID.randomUUID().toString()
        try {
            write(id,Record(owner?.toString(),"CAPTURING"))
            file(id).outputStream().use {it.fd.sync()};sync(root);id
        } catch(failure:Throwable) {
            // No URI has been returned or granted: cancellation is unambiguous here.
            runCatching {
                write(id,Record(owner?.toString(),"DISCARD"));removeJpeg(id)
                check(!lease(id).exists() || delete(lease(id)));sync(root)
            }
            throw failure
        }
    }
    fun revoke(id:String) {context.revokeUriPermission(uri(id),Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)}
    internal fun returned(id:String,owner:UUID)=files {
        val r=read(id);require(r.owner==owner.toString());require(r.state in setOf("CAPTURING","RETURNED","IMPORTED"))
        revoke(id);if(r.state!="IMPORTED")write(id,r.copy(state="RETURNED"))
    }
    internal fun imported(id:String,owner:UUID)=files {
        val r=read(id);require(r.owner==owner.toString() && r.state in setOf("RETURNED","IMPORTED"))
        write(id,r.copy(state="IMPORTED"));removeJpeg(id)
    }
    internal fun discard(id:String,owner:UUID)=files {val r=read(id);require(r.owner==owner.toString());revoke(id);write(id,r.copy(state="DISCARD"))}
    private fun removeJpeg(id:String) {revoke(id);check(!file(id).exists() || delete(file(id)));if(root.exists())sync(root)}
    /** Explicit cancellation/completion only. A failed deletion keeps its durable intent. */
    fun finish(id:String)=files {
        val f=lease(id)
        if(f.exists())write(id,read(id).copy(state="DISCARD"))
        removeJpeg(id);check(!f.exists() || delete(f));if(root.exists())sync(root)
    }
    internal fun pending(owner:UUID):List<Pair<String,Record>> = files {
        records().filter {it.second.owner==owner.toString()}
    }
    private fun records():List<Pair<String,Record>> = root.listFiles().orEmpty().mapNotNull {f->
        val id=f.name.substringBefore(".lease")
        if(!valid(id) || f.name !in listOf("$id.lease","$id.lease.pending","$id.lease.previous","$id.lease.commit","$id.lease.staged"))null else id
    }.distinct().mapNotNull {id->runCatching {id to read(id)}.getOrNull()}
    /** No age/process-based sweep: an external camera may still own an unresolved request. */
    fun recover()=files {
        records().forEach {(id,r)->
            if(r.state=="IMPORTED")runCatching {removeJpeg(id)}
            if(r.state=="DISCARD" && r.owner==null) {removeJpeg(id);check(delete(lease(id)));sync(root)}
        }
        // DISCARD records also protect encrypted reservations; authenticated recovery finishes them.
    }
}
