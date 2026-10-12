package com.r0ybt.arachn0de.access

import androidx.room.withTransaction
import com.r0ybt.arachn0de.data.local.*
import com.r0ybt.arachn0de.data.repository.NodeRepository
import com.r0ybt.arachn0de.security.EncryptedPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.withContext

internal data class AccessState(val favorites:Set<String> = emptySet(),val recent:List<String> = emptyList(),val counts:Map<String,Int> = emptyMap(),val destination:String? = null)
internal object AccessCodec {
    const val NAME="element_access"
    fun encode(s:AccessState)=EncryptedPreferences.encode(mapOf("version" to 1,"favorites" to s.favorites,"recent" to s.recent.joinToString("\n"),"counts" to s.counts.entries.joinToString("\n") {"${it.key}=${it.value}"},"destination" to s.destination.orEmpty()))
    fun decode(raw:String):AccessState {
        val v=EncryptedPreferences.decode(raw)
        require(v.keys==setOf("version","favorites","recent","counts","destination") && v["version"]==1)
        fun key(k:String) {require(k.length in 3..265 && (k.startsWith("project:") || k.startsWith("node:")) && k.substringAfter(':').isNotBlank() && k.none {it=='\n' || it=='='})}
        val favorites=(v["favorites"] as? Set<*>)?.map {it as String}?.toSet() ?: error("Favoritos inválidos")
        val recent=(v["recent"] as? String ?: error("Recientes inválidos")).split('\n').filter {it.isNotEmpty()}
        val countsRaw=(v["counts"] as? String ?: error("Frecuentes inválidos")).split('\n').filter {it.isNotEmpty()}
        require(countsRaw.map {it.substringBefore('=')}.distinct().size==countsRaw.size)
        val counts=(v["counts"] as? String ?: error("Frecuentes inválidos")).split('\n').filter {it.isNotEmpty()}.associate {line->val parts=line.split('=');require(parts.size==2);parts[0] to parts[1].toInt().also {require(it in 1..1000000)}}
        val destination=(v["destination"] as? String ?: error("Destino inválido")).ifEmpty {null}
        (favorites+recent+counts.keys+listOfNotNull(destination)).forEach(::key)
        require(recent.size<=20 && recent.distinct().size==recent.size && counts.size<=200)
        return AccessState(favorites,recent,counts,destination)
    }
}
internal class AccessRepository(private val db:Arachn0deDatabase) {
    private suspend fun valid()=db.backupDao().projects().map {"project:${it.id}"}.toSet()+db.backupDao().nodes().map {"node:${it.id}"}
    private suspend fun read():AccessState {
        val raw=db.privatePreferenceDao().get(AccessCodec.NAME)?.payload ?: return AccessState()
        val s=AccessCodec.decode(raw);val valid=valid()
        val clean=s.copy(favorites=s.favorites.intersect(valid),recent=s.recent.filter {it in valid},counts=s.counts.filterKeys {it in valid},destination=s.destination?.takeIf {it in valid})
        if(clean!=s) write(clean)
        return clean
    }
    private suspend fun write(s:AccessState) {db.privatePreferenceDao().save(PrivatePreferenceEntity(AccessCodec.NAME,AccessCodec.encode(s)))}
    internal suspend fun cleanReferences() {read()}
    suspend fun snapshot()=withContext(Dispatchers.IO) {db.withTransaction {read()}}
    fun observe()=db.invalidationTracker.createFlow("private_preferences","projects","nodes").map {snapshot()}.distinctUntilChanged()
    suspend fun toggle(key:String)=withContext(Dispatchers.IO) {db.withTransaction {
        require(key in valid());val s=read();write(s.copy(favorites=if(key in s.favorites) s.favorites-key else s.favorites+key))
    }}
    suspend fun opened(key:String)=withContext(Dispatchers.IO) {db.withTransaction {
        if(key !in valid()) return@withTransaction
        val s=read();val recent=(listOf(key)+s.recent.filter {it!=key}).take(20)
        val counts=(s.counts+(key to ((s.counts[key] ?: 0)+1).coerceAtMost(1000000))).entries.sortedByDescending {it.value}.take(200).associate {it.toPair()}
        write(s.copy(recent=recent,counts=counts))
    }}
    suspend fun capture(key:String,title:String,description:String,creationId:String)=withContext(Dispatchers.IO) {db.withTransaction {
        require(key in valid()) {"El destino ya no existe."}
        val (project,parent)=if(key.startsWith("project:")) key.substringAfter(':') to null else {
            val n=requireNotNull(db.nodeDao().getById(key.substringAfter(':')))
            n.projectId to n.id
        }
        val node=NodeRepository(db).createNode(project,parent,title,description,creationId=creationId)
        write(read().copy(destination=key));node
    }}
}
