package com.r0ybt.arachn0de.cleanup

import androidx.room.withTransaction
import com.r0ybt.arachn0de.data.local.*
import com.r0ybt.arachn0de.data.repository.*
import com.r0ybt.arachn0de.security.EncryptedPreferences
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged

internal object CleanupPreference {
    const val NAME="completed_cleanup"
    val options=linkedMapOf(1 to "1 día",7 to "7 días",30 to "30 días",90 to "90 días",180 to "180 días",365 to "1 año (365 días)",0 to "Nunca")
    fun encode(days:Int):String {require(days in options);return EncryptedPreferences.encode(mapOf("version" to 1,"days" to days))}
    fun decode(raw:String):Int {
        val values=EncryptedPreferences.decode(raw)
        require(values.keys==setOf("version","days") && values["version"]==1)
        return (values["days"] as? Int ?: error("Plazo inválido")).also {require(it in options)}
    }
}

internal class CompletedCleanup(private val db:Arachn0deDatabase,private val nodes:NodeRepository,private val attachments:AttachmentRepository) {
    data class Candidate(val node:NodeEntity,val event:NodeEventEntity?)
    data class Preview(val project:String?,val layer:String?,val candidates:List<Candidate>,val excluded:Int,val days:Int?=null)
    suspend fun days()=db.privatePreferenceDao().get(CleanupPreference.NAME)?.let {CleanupPreference.decode(it.payload)} ?: 0
    fun observeDays()=db.invalidationTracker.createFlow("private_preferences").map {days()}.distinctUntilChanged()
    suspend fun setDays(days:Int) {db.privatePreferenceDao().save(PrivatePreferenceEntity(CleanupPreference.NAME,CleanupPreference.encode(days)))}
    suspend fun preview(project:String?=null,layer:String?=null,days:Int?=null,now:Long=System.currentTimeMillis()):Preview=withContext(Dispatchers.IO) {
        db.withTransaction {select(project,layer,days,now)}
    }
    private suspend fun select(project:String?,layer:String?,days:Int?,now:Long):Preview {
        val rows=db.backupDao().nodes()
        val byId=rows.associateBy {it.id}
        val children=rows.groupBy {it.parentId}
        val scope=if(layer==null) rows.filter {project==null || it.projectId==project}.mapTo(hashSetOf()) {it.id} else {
            val root=byId[layer]
            if(root==null || root.projectId!=project || root.purpose!="LAYER") return Preview(project,layer,emptyList(),0,days)
            val ids=hashSetOf<String>();val stack=ArrayDeque<String>();stack.add(layer)
            while(stack.isNotEmpty()) {val id=stack.removeLast();if(ids.add(id)) children[id].orEmpty().forEach {stack.add(it.id)}}
            ids
        }
        val events=db.nodeEventDao().all().filter {it.type=="COMPLETED" || it.type=="REOPENED"}.groupBy {it.nodeId}
        val candidates=mutableListOf<Candidate>();var excluded=0
        for(row in rows) {
            if(row.id !in scope || row.purpose!="ACTION" || !row.isCompleted || children[row.id].orEmpty().isNotEmpty()) continue
            val history=events[row.id].orEmpty();val event=history.lastOrNull()
            if(days!=null) {
                if(days==0 || event?.type!="COMPLETED" || event.occurredAt<=0 || event.occurredAt<row.createdAt || event.occurredAt>now ||
                    history.zipWithNext().any {(a,b)->b.occurredAt<a.occurredAt} || now-event.occurredAt<days*86_400_000L) continue
            }
            try {BacklogRepository(db).rejectDeletion(setOf(row.id))}
            catch(restricted:IllegalArgumentException) {excluded++;continue}
            candidates.add(Candidate(row,event))
        }
        return Preview(project,layer,candidates,excluded,days)
    }
    suspend fun clean(preview:Preview,now:Long=System.currentTimeMillis()):Int=withContext(Dispatchers.IO) {
        nodes.beforeDelete(preview.candidates.mapTo(hashSetOf()) {it.node.id})
        val count=db.withTransaction {
            if(preview.days!=null && days()!=preview.days) return@withTransaction 0
            val current=select(preview.project,preview.layer,preview.days,now).candidates.associateBy {it.node.id}
            val safe=preview.candidates.filter {current[it.node.id]==it}
            safe.groupBy {it.node.projectId}.values.sumOf {group->nodes.deleteSelectedInTransaction(group.first().node.projectId,group.mapTo(hashSetOf()) {it.node.id})}
        }
        if(count>0) try {attachments.cleanup()} catch(c:CancellationException) {throw c} catch(_:Exception) {android.util.Log.w("CompletedCleanup","File cleanup deferred")}
        count
    }
    suspend fun automatic(now:Long=System.currentTimeMillis()):Int {
        val days=days();if(days==0) return 0
        return clean(preview(days=days,now=now),now)
    }
}
