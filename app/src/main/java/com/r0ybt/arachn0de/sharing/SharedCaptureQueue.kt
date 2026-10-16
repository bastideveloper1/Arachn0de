package com.r0ybt.arachn0de.sharing

import androidx.room.withTransaction
import com.r0ybt.arachn0de.access.AccessRepository
import com.r0ybt.arachn0de.data.local.*
import com.r0ybt.arachn0de.data.repository.AttachmentRepository
import com.r0ybt.arachn0de.domain.model.AttachmentReferences
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject
import java.util.UUID

/** Operational journal in the authenticated SQLCipher store. Never contains external URIs. */
internal class SharedCaptureQueue(private val db:Arachn0deDatabase,internal val attachments:AttachmentRepository) {
    internal data class Entry(val id:String,val title:String,val description:String,val destination:String?,val phase:String,val image:Boolean,val order:Long,val removed:List<String> = emptyList(),val linkNote:Boolean=false) {
        fun json()=JSONObject().put("version",if(linkNote) 2 else 1).apply {if(linkNote) put("linkNote",true)}.put("id",id).put("title",title).put("description",description).put("destination",destination ?: JSONObject.NULL).put("phase",phase).put("image",image).put("order",order).put("removed",org.json.JSONArray(removed)).toString()
    }
    private val ingestion=Mutex()
    private val dao=db.privatePreferenceDao()
    private suspend fun entries()=dao.all().filter {it.name.startsWith(PREFIX)}.map {row->
        val j=JSONObject(row.payload);val version=j.getInt("version");require((version==1 && j.length()==9) || (version==2 && j.length()==10))
        val linkNote=if(version==2) j.get("linkNote").also {require(it is Boolean)} as Boolean else false
        require(!linkNote || !j.getBoolean("image"))
        val id=j.getString("id");require(UUID.fromString(id).toString()==id && row.name==PREFIX+id)
        val phase=j.getString("phase");require(phase in setOf("PREPARING","READY","CANCELLED"))
        Entry(id,j.getString("title"),j.getString("description"),if(j.isNull("destination")) null else j.getString("destination"),phase,j.getBoolean("image"),j.getLong("order"),j.getJSONArray("removed").let {a->(0 until a.length()).map {a.getString(it).also {id->require(UUID.fromString(id).toString()==id)}}},linkNote)
    }.sortedBy {it.order}
    fun observe()=db.invalidationTracker.createFlow("private_preferences").map {entries().filter {it.phase=="READY"}}.distinctUntilChanged()
    private suspend fun put(e:Entry)=dao.save(PrivatePreferenceEntity(PREFIX+e.id,e.json()))
    private suspend fun remove(id:String)=dao.delete(PREFIX+id)
    suspend fun receive(id:String,input:SharedContent) = withContext(Dispatchers.IO) {ingestion.withLock {
        require(!input.linkNote || (input.image==null && SharedContent.isLink(input.description)))
        val inserted=AttachmentRepository.fileOperations.withLock {db.withTransaction {
            require(UUID.fromString(id).toString()==id)
            if(dao.get(PREFIX+id)!=null || db.nodeDao().getById(id)!=null) false else {
                put(Entry(id,input.title,literalReferences(input.description),null,"PREPARING",input.image!=null,(entries().maxOfOrNull {it.order} ?: 0L)+1,linkNote=input.linkNote));true
            }
        }
        }
        if(!inserted) return@withLock
        try {
            val file=input.image?.let {attachments.import(it,id)}
            db.withTransaction {
                val row=entries().first {it.id==id}
                put(row.copy(phase="READY",description=row.description+(file?.let {(if(row.description.isEmpty()) "" else "\n")+AttachmentReferences.token(it.id)} ?: "")))
            }
        } catch(c:CancellationException) {throw c}
        catch(e:Exception) {withContext(NonCancellable) {discard(id)};throw e}
    }}
    suspend fun recover()=withContext(Dispatchers.IO) {ingestion.withLock {
        entries().forEach {row->
            when(row.phase) {
                "CANCELLED" -> discard(row.id)
                "PREPARING" -> {
                    val files=attachments.pendingImports(row.id)
                    if(!row.image || files.size==1) db.withTransaction {
                        put(row.copy(phase="READY",description=row.description+(files.singleOrNull()?.let {(if(row.description.isEmpty()) "" else "\n")+AttachmentReferences.token(it.id)} ?: "")))
                    } else discard(row.id)
                }
            }
        }
    }}
    suspend fun update(id:String,title:String,description:String,destination:String?,removed:List<String> = emptyList())=withContext(Dispatchers.IO) {db.withTransaction {
        val row=entries().firstOrNull {it.id==id && it.phase=="READY"} ?: return@withTransaction
        put(row.copy(title=title,description=description,destination=destination,removed=removed))
    }}
    suspend fun discard(id:String)=withContext(Dispatchers.IO) {
        db.withTransaction {entries().firstOrNull {it.id==id}?.let {put(it.copy(phase="CANCELLED"))}}
        attachments.discardDraft(id)
        db.withTransaction {remove(id)}
    }
    suspend fun save(id:String,destination:String,title:String,description:String,removed:Set<String>)=withContext(Dispatchers.IO) {
        attachments.saveNodeDraft(id,listOf(id),description,removed) {effective->
            val row=requireNotNull(entries().firstOrNull {it.id==id && it.phase=="READY"}) {"La captura ya fue procesada."}
            if(row.linkNote) require(SharedContent.isLink(description.trim())) {"Introduce únicamente un enlace HTTP o HTTPS válido."}
            AccessRepository(db).capture(destination,title,if(row.linkNote) description.trim() else effective,id,
                purpose=if(row.linkNote) com.r0ybt.arachn0de.domain.model.NodePurpose.NOTE else com.r0ybt.arachn0de.domain.model.NodePurpose.ACTION)
            remove(id)
        }
    }
    companion object {
        private fun literalReferences(text:String):String {
            val result=StringBuilder(text)
            AttachmentReferences.matches(text).toList().asReversed().forEach {result.insert(it.range.first,'\\')}
            return result.toString()
        }
        const val PREFIX="share_pending:"
        suspend fun requireSettled(db:Arachn0deDatabase) {
            require(db.privatePreferenceDao().all().none {it.name.startsWith(PREFIX)}) {"Guarda o cancela las capturas compartidas pendientes antes de respaldar o restaurar."}
        }
    }
}
