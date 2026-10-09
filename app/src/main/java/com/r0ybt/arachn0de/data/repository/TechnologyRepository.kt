package com.r0ybt.arachn0de.data.repository

import android.net.Uri
import androidx.room.withTransaction
import com.r0ybt.arachn0de.data.local.*
import com.r0ybt.arachn0de.backup.validateAvatar
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.withLock

internal data class TechnologyState(
    val catalog: List<TechnologyEntity> = emptyList(),
    val nodeIds: Map<String, Set<String>> = emptyMap(),
    val projectIds: Map<String, Set<String>> = emptyMap(),
    val loaded: Boolean = false,
    val failed: Boolean = false,
) {
    fun forOwner(id: String, project: Boolean): List<TechnologyEntity> {
        val ids = (if (project) projectIds else nodeIds)[id].orEmpty()
        val byId = catalog.associateBy { it.id }; return ids.mapNotNull(byId::get)
    }
}

internal class TechnologyRepository(private val database: Arachn0deDatabase, private val icons: TechnologyIconStore, sessionScope:CoroutineScope?=null) {
    private val dao = database.technologyDao()
    private val scope = sessionScope ?: CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val stateUpdates=Any()
    private val cachedState=MutableStateFlow(TechnologyState())
    val state:StateFlow<TechnologyState> = cachedState.asStateFlow()
    private val updates = database.invalidationTracker.createFlow("technologies", "node_technologies", "project_technologies").map {
        database.withTransaction {
            val catalog=dao.catalog().sortedWith(compareBy({ row->row.name.map { if(it in 'A'..'Z') it.lowercaseChar() else it }.joinToString("") },{it.id}))
            val nodes=dao.nodes();val projects=dao.projects()
            TechnologyState(catalog,
                nodes.groupBy {it.nodeId}.mapValues {(_,rows)->rows.mapTo(linkedSetOf()) {it.technologyId}},
                projects.groupBy {it.projectId}.mapValues {(_,rows)->rows.mapTo(linkedSetOf()) {it.technologyId}},loaded=true)
        }
    }.retryWhen { _, attempt -> delay(minOf(1000L * (attempt + 1), 10_000)); true }
    init {
        scope.launch {
            cachedState.subscriptionCount.map {it>0}.distinctUntilChanged().collectLatest {subscribed ->
                if(subscribed) updates.collect {next ->
                    val job=currentCoroutineContext()
                    synchronized(stateUpdates) {job.ensureActive();cachedState.value=next}
                }
            }
        }
    }

    private suspend fun <T> files(work: suspend () -> T): T = withContext(Dispatchers.IO) {
        AttachmentRepository.fileOperations.withLock { work() }
    }
    suspend fun importIcon(uri: Uri): String {
        var name: String? = null
        try { return files { val coroutine = currentCoroutineContext(); icons.import(uri) { coroutine.ensureActive() }.also { name = it } } }
        catch (cancelled: CancellationException) {
            withContext(NonCancellable) { discardIcon(name) }
            throw cancelled
        }
    }
    suspend fun retainIcon(name: String?, draftId: String) = files { name?.let { icons.lifecycle.reserve(it, draftId) } }
    suspend fun discardIcon(name: String?, draftId: String? = null) = files {
        if (name != null) runCatching {
            icons.lifecycle.release(name); draftId?.let { icons.lifecycle.release(name, it) }
            database.withTransaction { if (dao.iconReferences(name) == 0) icons.durable.delete(name) }
        }
        Unit
    }
    suspend fun save(id: String, name: String, iconFile: String?, isNew: Boolean, draftId: String? = null): Boolean = files {
        require(id.isNotBlank() && id.length <= 256 && name.trim().isNotEmpty())
        recoverLocked()
        iconFile?.let { validateAvatar(icons.durable.read(it)) }
        try {
            val result = database.withTransaction {
                val old = dao.get(id)
                if (old == null && !isNew) return@withTransaction false
                if (old != null && isNew) return@withTransaction old.name == name.trim() && old.iconFile == iconFile
                icons.durable.record(setOfNotNull(old?.iconFile, iconFile))
                val row = TechnologyEntity(id, name.trim(), iconFile)
                if (old == null) { dao.insert(row); true } else dao.update(row) == 1
            }
            if (result && iconFile != null) withContext(NonCancellable) { runCatching {
                icons.lifecycle.release(iconFile); draftId?.let { icons.lifecycle.release(iconFile, it) }
            } }
            result
        } finally { withContext(NonCancellable) { runCatching { recoverLocked() } } }
    }
    suspend fun delete(id: String): Boolean = files {
        recoverLocked()
        try {
            database.withTransaction {
                val old = dao.get(id) ?: return@withTransaction false
                icons.durable.record(setOfNotNull(old.iconFile))
                dao.delete(id) == 1 // FK cascades remove only technology links, never their owners.
            }
        } finally { withContext(NonCancellable) { runCatching { recoverLocked() } } }
    }
    suspend fun assign(owner: String, project: Boolean, ids: Set<String>): Boolean = files {
        database.withTransaction {
            val exists = if (project) database.projectDao().getById(owner) != null else database.nodeDao().getById(owner) != null
            if (!exists || ids.any { dao.get(it) == null }) return@withTransaction false
            if (project) { dao.clearProject(owner); dao.assignProjects(ids.mapIndexed { index, id -> ProjectTechnologyEntity(owner, id, index) }) }
            else { dao.clearNode(owner); dao.assignNodes(ids.mapIndexed { index, id -> NodeTechnologyEntity(owner, id, index) }) }
            true
        }
    }
    suspend fun recover() = files { recoverLocked() }
    suspend fun cleanup() = files {
        recoverLocked()
        database.withTransaction { icons.cleanup(dao.catalog().mapNotNull { it.iconFile }.toSet(), icons.durable.recorded()) }
    }
    private suspend fun recoverLocked() {
        if (!icons.durable.hasJournal()) return
        val names = icons.durable.recorded()
        database.withTransaction { names.forEach { if (dao.iconReferences(it) == 0) icons.durable.delete(it) } }
        icons.durable.clearJournal()
    }
    fun close() { scope.cancel();synchronized(stateUpdates) {cachedState.value=TechnologyState()} }
}
