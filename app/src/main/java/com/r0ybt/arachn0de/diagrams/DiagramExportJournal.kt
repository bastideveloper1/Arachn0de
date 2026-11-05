package com.r0ybt.arachn0de.diagrams

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import com.r0ybt.arachn0de.backup.syncBackupDirectory
import com.r0ybt.arachn0de.data.repository.AttachmentRepository
import com.r0ybt.arachn0de.security.SecureFiles
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject
import java.io.File

/** Only operational metadata lives here (encrypted in the vault), never exported frames.
 * The unique name is durable BEFORE SAF creation, closing the create/record crash window.
 * Recovery only touches this operation's exact document; completed outputs are user-owned.
 */
internal class DiagramExportJournal(
    private val context:Context,private val privateContext:Context,
    private val sync:(File)->Unit=::syncBackupDirectory,
    private val persist:(Uri)->Unit={context.contentResolver.takePersistableUriPermission(it,Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)},
    private val theme:Boolean=false, // Historical export journals are recovery-only.
    private val report:Boolean=false,
    private val cards:Boolean=false
) {
    internal class CardCleanupException(cause:Throwable):IllegalStateException("Quedó un PNG parcial pendiente de limpieza.",cause)
    internal data class Ticket(val id:String,val tree:Uri,val name:String,val uri:Uri?=null,val complete:Boolean=false)
    private val root get()=File(privateContext.filesDir,when {cards->"card-image-export-journal";report->"document-export-journal";theme->"theme-export-journal";else->"diagram-export-journal"})
    private val prefix get()=if(cards)"tarjeta" else if(report)"informe" else if(theme)"tema" else "diagrama"
    private val recoveryScreen get()=if(cards)"Compartir y exportar" else if(report)"Exportar PDF / Word" else if(theme)"Apariencia" else "Diagramas"
    companion object {private val active=mutableSetOf<String>()}
    private fun marker(t:Ticket)=File(root,"${t.id}.json")
    private fun record(t:Ticket) {
        check(root.mkdirs()||root.isDirectory)
        val next=File(root,"${t.id}.next")
        val raw=JSONObject().put("tree",t.tree.toString()).put("name",t.name).put("uri",t.uri?.toString() ?: JSONObject.NULL).put("complete",t.complete).toString()
        SecureFiles.write(next,raw.toByteArray());check(next.renameTo(marker(t)));sync(root);sync(privateContext.filesDir)
    }
    private fun forget(t:Ticket) {val file=marker(t);if(file.exists())check(file.delete());sync(root)}
    private fun read(file:File):Ticket {
        require(file.length()<=16384);val j=JSONObject(SecureFiles.text(file));val id=file.name.removeSuffix(".json")
        require(java.util.UUID.fromString(id).toString()==id)
        val name=j.getString("name")
        if(cards)require(name.length<=160 && name.matches(Regex("[0-9]{4,10}-[\\p{L}\\p{N} _-]+-tarjeta-$id\\.png")))
        else require(name.startsWith("${prefix}-$id.")&&name.substringAfterLast('.') in when {report->setOf("pdf","docx","md","json","zip");theme->setOf("json");else->setOf("png","gif","mp4")})
        return Ticket(id,Uri.parse(j.getString("tree")),name,if(j.isNull("uri"))null else Uri.parse(j.getString("uri")),j.getBoolean("complete"))
    }
    private fun query(uri:Uri,columns:Array<String>):android.database.Cursor? =
        if(android.os.Build.VERSION.SDK_INT>=26)context.contentResolver.query(uri,columns,android.os.Bundle.EMPTY,null)
        else context.contentResolver.query(uri,columns,null,null,null)
    private fun candidates(t:Ticket):List<Uri> {
        if(t.uri!=null)return listOf(t.uri)
        val children=DocumentsContract.buildChildDocumentsUriUsingTree(t.tree,DocumentsContract.getTreeDocumentId(t.tree))
        return requireNotNull(query(children,arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID,DocumentsContract.Document.COLUMN_DISPLAY_NAME))).use {cursor->
            buildList {while(cursor.moveToNext())if(cursor.getString(1)==t.name)add(DocumentsContract.buildDocumentUriUsingTree(t.tree,cursor.getString(0)))}
        }
    }
    private fun removeDocuments(t:Ticket) {
        candidates(t).forEach {uri->
            val exists=try {query(uri,arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID))?.use {it.moveToFirst()} ?: error("No se pudo comprobar el archivo parcial.")} catch(_:java.io.FileNotFoundException) {false}
            if(exists)check(DocumentsContract.deleteDocument(context.contentResolver,uri)) {"El proveedor no permitió retirar el archivo parcial: $uri"}
        }
    }
    private fun remove(t:Ticket) {removeDocuments(t);forget(t)}
    suspend fun begin(tree:Uri,format:DiagramFormat):Ticket {check(!theme&&!report);return beginFile(tree,format.mime,format.extension)}
    suspend fun beginReport(tree:Uri,mime:String,extension:String):Ticket {check(report&&!theme);require(extension in setOf("pdf","docx","md","json","zip"));return beginFile(tree,mime,extension)}
    suspend fun beginCard(tree:Uri,ordinal:Int,total:Int,title:String):Ticket {
        check(cards&&!theme&&!report);require(ordinal in 1..total)
        val safe=title.filter {it.isLetterOrDigit() || it in " _-"}.trim().take(48).ifBlank {"elemento"}
        val number=ordinal.toString().padStart(maxOf(4,total.toString().length),'0')
        return withContext(NonCancellable+Dispatchers.IO) {
            persist(tree)
            var t=AttachmentRepository.fileOperations.withLock {
                check((root.listFiles()?.count {it.extension=="json"} ?: 0)<16) {"Hay guardados pendientes de limpieza."}
                val id=diagramId();Ticket(id,tree,"$number-$safe-tarjeta-$id.png").also {record(it);active.add(id)}
            }
            try {
                val parent=DocumentsContract.buildDocumentUriUsingTree(tree,DocumentsContract.getTreeDocumentId(tree))
                // SAF may block in another process. Never hold the common file mutex here.
                t=t.copy(uri=requireNotNull(DocumentsContract.createDocument(context.contentResolver,parent,"image/png",t.name)))
                AttachmentRepository.fileOperations.withLock {record(t)};t
            } catch(failure:Throwable) {
                val cleanup=runCatching {removeDocuments(t);AttachmentRepository.fileOperations.withLock {forget(t)}}.exceptionOrNull()
                AttachmentRepository.fileOperations.withLock {active.remove(t.id)}
                if(cleanup!=null)throw CardCleanupException(failure.apply {addSuppressed(cleanup)})
                throw failure
            }
        }
    }
    private suspend fun beginFile(tree:Uri,mime:String,extension:String,name:((String)->String)?=null):Ticket=withContext(NonCancellable+Dispatchers.IO) {
        AttachmentRepository.fileOperations.withLock {
            persist(tree)
            check((root.listFiles()?.count {it.extension=="json"} ?: 0)<16) {"Hay exportaciones pendientes de limpieza. Vuelve a abrir $recoveryScreen con acceso a sus carpetas."}
            val id=diagramId();var t=Ticket(id,tree,name?.invoke(id) ?: "${prefix}-$id.$extension")
            record(t);active.add(id)
            try {
                val parent=DocumentsContract.buildDocumentUriUsingTree(tree,DocumentsContract.getTreeDocumentId(tree))
                t=t.copy(uri=requireNotNull(DocumentsContract.createDocument(context.contentResolver,parent,mime,t.name)))
                record(t);t
            } catch(e:Throwable) {
                val cleanup=runCatching {remove(t)}.exceptionOrNull();active.remove(id)
                if(cleanup!=null)throw IllegalStateException("No se pudo retirar la exportación parcial ${t.name}. Se reintentará al abrir $recoveryScreen.",e.apply {addSuppressed(cleanup)})
                throw e
            }
        }
    }
    suspend fun complete(t:Ticket)=withContext(NonCancellable+Dispatchers.IO) {AttachmentRepository.fileOperations.withLock {
        record(t.copy(complete=true));active.remove(t.id)
        // A completed marker is safe to retain if metadata cleanup fails.
        runCatching {forget(t)};Unit
    }}
    suspend fun rollback(t:Ticket)=withContext(NonCancellable+Dispatchers.IO) {
        if(cards) {
            try {removeDocuments(t);AttachmentRepository.fileOperations.withLock {forget(t)}}
            finally {AttachmentRepository.fileOperations.withLock {active.remove(t.id)}}
        } else AttachmentRepository.fileOperations.withLock {try {remove(t)} finally {active.remove(t.id)}}
    }
    private suspend fun recoverCards():List<String> {
        val failures=mutableListOf<String>()
        val pending=AttachmentRepository.fileOperations.withLock {
            root.listFiles().orEmpty().filter {it.extension=="json" && it.nameWithoutExtension !in active}.mapNotNull {file->
                runCatching {read(file)}.onFailure {failures+=file.name}.getOrNull()
            }
        }
        for(t in pending)runCatching {
            if(!t.complete)removeDocuments(t)
            AttachmentRepository.fileOperations.withLock {forget(t)}
        }.onFailure {failures+=t.name}
        AttachmentRepository.fileOperations.withLock {
            root.listFiles().orEmpty().filter {it.extension=="next"&&it.nameWithoutExtension !in active&&runCatching {java.util.UUID.fromString(it.nameWithoutExtension)}.isSuccess}.forEach {it.delete()}
        }
        return failures
    }
    suspend fun recover():List<String> =withContext(Dispatchers.IO) {
        if(cards)return@withContext recoverCards()
        AttachmentRepository.fileOperations.withLock {
            val failures=mutableListOf<String>()
            root.listFiles().orEmpty().filter {it.extension=="json"}.forEach {file->
                if(file.nameWithoutExtension !in active)runCatching {val t=read(file);if(t.complete)forget(t) else remove(t)}.onFailure {failures+="${file.name}: ${it.message}"}
            }
            root.listFiles().orEmpty().filter {it.extension=="next"&&it.nameWithoutExtension !in active&&runCatching {java.util.UUID.fromString(it.nameWithoutExtension)}.isSuccess}.forEach {it.delete()}
            failures
        }
    }
}
