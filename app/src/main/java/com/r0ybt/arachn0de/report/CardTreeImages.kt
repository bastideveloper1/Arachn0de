package com.r0ybt.arachn0de.report

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import com.r0ybt.arachn0de.data.local.toNode
import com.r0ybt.arachn0de.data.repository.AttachmentRepository
import com.r0ybt.arachn0de.diagrams.DiagramExportJournal
import com.r0ybt.arachn0de.domain.model.NodeTreeSnapshot
import com.r0ybt.arachn0de.export.PublicExportStaging
import com.r0ybt.arachn0de.export.TaskPlainTextRenderer
import com.r0ybt.arachn0de.security.VaultSession
import com.r0ybt.arachn0de.backup.syncBackupDirectory
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.withLock

internal data class CardImageItem(val id:String,val title:String,val project:Boolean=false)

internal object CardImageTree {
    suspend fun capture(session:VaultSession,projectId:String?,nodeId:String?):List<CardImageItem> = session.database.withTransaction {
        val db=session.database
        val root=nodeId?.let {requireNotNull(db.nodeDao().getById(it)) {"El elemento ya no existe."}.also {n->require(n.purpose=="LAYER")}}
        val project=if(root==null)requireNotNull(db.projectDao().getById(requireNotNull(projectId))) else null
        val owner=root?.projectId ?: projectId
        val rows=db.nodeDao().getProjectNodes(owner);val parents=rows.mapNotNullTo(hashSetOf()) {it.parentId}
        val tree=NodeTreeSnapshot(rows.map {it.toNode(it.id in parents)})
        val job=currentCoroutineContext()
        val ids=TaskPlainTextRenderer.branch(tree,nodeId,owner.orEmpty(),onlyPending=false) {job.ensureActive()}
        require(ids.size+(if(project!=null)1 else 0)>1) {"El elemento ya no tiene descendientes."}
        val items=ids.filter {id->
            val node=tree.nodesById.getValue(id)
            node.purpose!=com.r0ybt.arachn0de.domain.model.NodePurpose.ACTION || !node.isCompleted
        }.map {CardImageItem(it,tree.nodesById.getValue(it).title)}
        val result=if(project!=null)listOf(CardImageItem(project.id,project.name,true))+items else items
        require(result.isNotEmpty()) {"No hay elementos pendientes para guardar."};result
    }
}

/** One authenticated PNG at a time. No source-file lock or vault key across SAF writes. */
internal class CardTreeImages(
    private val context:Context,private val storage:Context,private val journal:DiagramExportJournal,
    private val sync:(java.io.File)->Unit=::syncBackupDirectory
) {
    private var destination:Uri?=null
    private var batch:List<CardImageItem> = emptyList()
    var saved:Int=0;private set
    var failedTitle:String?=null;private set
    var cleanupPending:Boolean=false;private set
    suspend fun save(tree:Uri,items:List<CardImageItem>,colors:CardShareColors,authorized:()->Unit,source:suspend (CardImageItem)->CardShareData):Int=withContext(Dispatchers.IO) {
        if(destination!=tree || batch!=items || saved==items.size) {saved=0;destination=tree;batch=items.toList()}
        failedTitle=null;authorized()
        cleanupPending=journal.recover().isNotEmpty()
        check(!cleanupPending) {"No se pudo retirar un PNG parcial anterior. Recupera el acceso a la carpeta antes de reintentar."}
        for((index,item) in items.withIndex().drop(saved)) {
            currentCoroutineContext().ensureActive();authorized();failedTitle=item.title
            var stage:PublicExportStaging?=null;var ticket:DiagramExportJournal.Ticket?=null
            try {
                val name=AttachmentRepository.fileOperations.withLock {
                    authorized();stage=PublicExportStaging(storage,sync=sync)
                    val job=currentCoroutineContext();val data=source(item);job.ensureActive();authorized()
                    val bitmap=CardShareRenderer.render(data,colors) {job.ensureActive();authorized()}
                    try {requireNotNull(stage).put(CardShareFiles.MAX_BYTES) {out->ObligationReportFiles.writePng(bitmap,out);job.ensureActive();authorized()}}
                    finally {bitmap.recycle()}
                }
                authorized();currentCoroutineContext().ensureActive()
                withContext(NonCancellable) {ticket=journal.beginCard(tree,index+1,items.size,item.title)}
                authorized();currentCoroutineContext().ensureActive()
                requireNotNull(context.contentResolver.openOutputStream(requireNotNull(ticket?.uri),"wt")).use {output->
                    requireNotNull(stage).input(name).use {input->
                        val buffer=ByteArray(32768)
                        try {
                            while(true) {currentCoroutineContext().ensureActive();authorized();val n=input.read(buffer);if(n<0)break;authorized();output.write(buffer,0,n)}
                            output.flush();authorized();currentCoroutineContext().ensureActive()
                        } finally {buffer.fill(0)}
                    }
                }
                withContext(NonCancellable) {journal.complete(requireNotNull(ticket));saved++;ticket=null}
            } catch(failure:Throwable) {
                if(failure is DiagramExportJournal.CardCleanupException)cleanupPending=true
                ticket?.let {withContext(NonCancellable) {if(runCatching {journal.rollback(it)}.isFailure)cleanupPending=true}}
                throw failure
            } finally {
                withContext(NonCancellable) {AttachmentRepository.fileOperations.withLock {stage?.close()}}
            }
        }
        failedTitle=null;saved
    }
}
