package com.r0ybt.arachn0de.metro

import android.content.Context
import androidx.room.withTransaction
import com.r0ybt.arachn0de.data.local.*
import com.r0ybt.arachn0de.data.repository.NodeRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

internal data class MetroJourney(val row: MetroJourneyEntity, val data: MetroJourneyData)
internal data class MetroSnapshot(val preferences: MetroPreferences, val journeys: List<MetroJourney>)
internal class MetroRepository(private val db: Arachn0deDatabase, context: Context) {
    private val catalog = context.applicationContext.assets.open("metro/santiago-beta1.json").bufferedReader(Charsets.UTF_8).use { it.readText() }
    private val dao get()=db.metroDao()
    private suspend fun preferences() = dao.preferences()?.let { MetroCodec.preferences(it.payload) } ?: MetroPreferences(catalog)
    fun observe() = combine(dao.observePreferences(),dao.observeJourneys()) { prefs,rows ->
        val p=prefs?.let { MetroCodec.preferences(it.payload) } ?: MetroPreferences(catalog)
        MetroSnapshot(p,rows.map { MetroJourney(it,MetroCodec.journey(it.payload,p.network)) })
    }.flowOn(Dispatchers.IO)
    suspend fun snapshot(): MetroSnapshot = withContext(Dispatchers.IO) { db.withTransaction {
        val p=preferences(); MetroSnapshot(p,dao.journeys().map { MetroJourney(it,MetroCodec.journey(it.payload,p.network)) })
    } }
    suspend fun settings(update: (MetroPreferences)->MetroPreferences) = withContext(Dispatchers.IO) { db.withTransaction {
        val p=update(preferences()); val raw=MetroCodec.preferences(p); MetroCodec.preferences(raw); dao.preferences(MetroPreferencesEntity(payload=raw)); currentCoroutineContext().ensureActive()
    } }
    suspend fun savePlan(route: MetroRoute, nodeId: String?=null, personId: String?=null, existingId: String?=null): String = withContext(Dispatchers.IO) { db.withTransaction {
        MetroPlanPersistence.save(db,preferences(),route,nodeId,personId,existingId)
    } }
    suspend fun createTask(route: MetroRoute, projectId: String, parentId: String?, title: String, personId: String?, priority: com.r0ybt.arachn0de.domain.model.Priority,
        startAt: Long?=null,dueAt: Long?=null,description: String=""): String = withContext(Dispatchers.IO) { db.withTransaction {
        val task=NodeRepository(db).createNode(projectId,parentId,title,description=description,priority=priority,startAt=startAt,dueAt=dueAt,responsibleIds=personId?.let { setOf(it) }.orEmpty())
        savePlan(route,task.id,personId)
    } }
    suspend fun enabled(id: String,enabled: Boolean)=withContext(Dispatchers.IO) { db.withTransaction {
        val row=requireNotNull(dao.journey(id)); val data=MetroCodec.journey(row.payload,preferences().network)
        require(data.active==null) { "Finaliza el seguimiento antes de volver al modo normal." }
        dao.save(row.copy(enabled=enabled,revision=Math.addExact(row.revision,1)))
    } }
    suspend fun traveler(id: String,personId: String?)=withContext(Dispatchers.IO) { db.withTransaction {
        val row=requireNotNull(dao.journey(id)); if(personId!=null) requireNotNull(db.personDao().get(personId))
        val data=MetroCodec.journey(row.payload,preferences().network)
        val updated=data.active?.let { active->data.copy(sessions=data.sessions.dropLast(1)+active.copy(personId=personId)) } ?: data
        dao.save(row.copy(personId=personId,payload=MetroCodec.journey(updated),revision=Math.addExact(row.revision,1)))
    } }
    suspend fun begin(id: String,revision: Long,time: MetroTime)=act(id,revision) { data ->
        require(data.active==null)
        val row=requireNotNull(dao.journey(id)); require(row.enabled)
        if(row.nodeId!=null) { val task=requireNotNull(db.nodeDao().getById(row.nodeId)); require(task.purpose=="ACTION" && !db.nodeDao().hasChildren(task.projectId,task.id)) }
        val net=preferences().network
        require(dao.journeys().none { MetroCodec.journey(it.payload,net).active!=null }) { "Ya existe un seguimiento activo." }
        data.copy(sessions=data.sessions+MetroTracking.start(data.plan,time).copy(personId=row.personId))
    }
    suspend fun tracking(id: String,revision: Long,action: (MetroSession)->MetroSession)=act(id,revision) { data ->
        val active=requireNotNull(data.active); data.copy(sessions=data.sessions.dropLast(1)+action(active))
    }
    suspend fun undoArrival(id:String,revision:Long,time:MetroTime):Boolean=act(id,revision) {data->
        val latest=requireNotNull(data.sessions.lastOrNull())
        if(latest.control?.undo==null) return@act data
        require(latest.control.undo.reversible) {"Ya comenzó una etapa posterior. Corrige explícitamente la estación actual."}
        require(dao.journeys().none {it.id!=id && MetroCodec.journey(it.payload,preferences().network).active!=null}) {"Otro viaje está activo; no se puede restaurar un seguimiento anterior."}
        data.copy(sessions=data.sessions.dropLast(1)+MetroStages.undoArrival(latest,time))
    }
    private suspend fun act(id: String,revision: Long,action: suspend (MetroJourneyData)->MetroJourneyData): Boolean=withContext(Dispatchers.IO) { db.withTransaction {
        val row=dao.journey(id) ?: return@withTransaction false
        if(row.revision!=revision) return@withTransaction false
        val net=preferences().network
        val previous=MetroCodec.journey(row.payload,net)
        val data=action(previous);if(data==previous) return@withTransaction true
        val raw=MetroCodec.journey(data); MetroCodec.journey(raw,net)
        dao.save(row.copy(payload=raw,revision=Math.addExact(revision,1))); currentCoroutineContext().ensureActive(); true
    } }
    suspend fun remove(id: String)=withContext(Dispatchers.IO) { db.withTransaction {
        val row=requireNotNull(dao.journey(id)); require(MetroCodec.journey(row.payload,preferences().network).active==null); dao.delete(id)
    } }
}
internal fun metroTime(context: Context)=MetroTime(System.currentTimeMillis(),android.os.SystemClock.elapsedRealtime(),android.provider.Settings.Global.getInt(context.contentResolver,android.provider.Settings.Global.BOOT_COUNT,0))

/** Shared plan persistence: callers reuse the same validation, identity and session rules. */
internal object MetroPlanPersistence {
    suspend fun save(db:Arachn0deDatabase,preferences:MetroPreferences,route:MetroRoute,nodeId:String?=null,personId:String?=null,existingId:String?=null,preserveEnabled:Boolean=false,updateActiveTraveler:Boolean=false):String=db.withTransaction {
        val dao=db.metroDao()
        val p=preferences; dao.preferences(MetroPreferencesEntity(payload=MetroCodec.preferences(p)))
        if(nodeId!=null) { val task=requireNotNull(db.nodeDao().getById(nodeId)); require(task.purpose=="ACTION" && !db.nodeDao().hasChildren(task.projectId,task.id)) }
        if(personId!=null) requireNotNull(db.personDao().get(personId))
        val previous=existingId?.let { dao.journey(it) } ?: nodeId?.let { dao.forNode(it) }
        if(nodeId!=null && previous!=null) require(previous.nodeId==null || previous.nodeId==nodeId) { "El plan pertenece a otra tarea." }
        val data=previous?.let { MetroCodec.journey(it.payload,p.network) }
        val sessions=data?.sessions.orEmpty().map {s->if(updateActiveTraveler && s.ended==null) s.copy(personId=personId) else s}
        val updated=MetroJourneyData(route,sessions)
        val payload=MetroCodec.journey(updated); MetroCodec.journey(payload,p.network)
        val id=previous?.id ?: java.util.UUID.randomUUID().toString()
        dao.save(MetroJourneyEntity(id,nodeId ?: previous?.nodeId,personId,if(preserveEnabled) previous?.enabled ?: true else true,payload,Math.addExact(previous?.revision ?: 0,1)))
        currentCoroutineContext().ensureActive(); id
    }
}
