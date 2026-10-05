package com.r0ybt.arachn0de.data.repository

import android.net.Uri
import androidx.room.withTransaction
import com.r0ybt.arachn0de.data.local.*
import com.r0ybt.arachn0de.domain.model.AttachmentReferences
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** DB owns confirmed relations; durable store reservations own pending editor imports. */
class AttachmentRepository(private val database: Arachn0deDatabase, private val store: AttachmentStore) {
    private val dao = database.attachmentDao()
    companion object { private val fileOperations = Mutex() }

    suspend fun import(uri: Uri, draftId: String): AttachmentFileEntity = withContext(Dispatchers.IO) {
        fileOperations.withLock {
            var imported: AttachmentFileEntity? = null
            try {
                store.import(uri, draftId).also { imported = it; dao.insert(it) }
            } catch (failure: Throwable) {
                withContext(NonCancellable) {
                    imported?.let { if (dao.get(it.id) == null && store.delete(it.storageName)) store.release(it.id) }
                }
                throw failure
            }
        }
    }
    suspend fun pendingImports(draftId: String): List<AttachmentFileEntity> = withContext(Dispatchers.IO) {
        fileOperations.withLock { store.reservations(draftId).mapNotNull { dao.get(it) } }
    }
    suspend fun discardDraft(draftId: String) = withContext(Dispatchers.IO) {
        fileOperations.withLock { store.reservations(draftId).forEach { store.release(it) } }
        cleanup()
    }
    suspend fun associateNode(owner: String, id: String, description: String? = null) = withContext(Dispatchers.IO) {
        fileOperations.withLock {
            database.withTransaction {
                val node = requireNotNull(database.nodeDao().getById(owner))
                requireReady(database, setOf(id))
                dao.attachNode(listOf(NodeAttachmentEntity(owner, id)))
                description?.let {
                    ensureNodeReferences(database, owner, it)
                    check(database.nodeDao().updateContent(owner, node.title, it, System.currentTimeMillis()) == 1)
                }
            }
            store.release(id)
        }
    }
    suspend fun associateProject(owner: String, id: String, description: String? = null) = withContext(Dispatchers.IO) {
        fileOperations.withLock {
            database.withTransaction {
                val project = requireNotNull(database.projectDao().getById(owner))
                requireReady(database, setOf(id))
                dao.attachProject(listOf(ProjectAttachmentEntity(owner, id)))
                description?.let {
                    ensureProjectReferences(database, owner, it)
                    check(database.projectDao().update(owner, project.name, it, System.currentTimeMillis()) == 1)
                }
            }
            store.release(id)
        }
    }
    suspend fun detachNode(owner: String, id: String) {
        database.withTransaction {
            val node = requireNotNull(database.nodeDao().getById(owner))
            database.nodeDao().updateContent(owner, node.title, AttachmentReferences.remove(node.description, id), System.currentTimeMillis())
            dao.detachNode(owner, id)
        }
        cleanup()
    }
    suspend fun detachProject(owner: String, id: String) {
        database.withTransaction {
            val project = requireNotNull(database.projectDao().getById(owner))
            database.projectDao().update(owner, project.name, AttachmentReferences.remove(project.description, id), System.currentTimeMillis())
            dao.detachProject(owner, id)
        }
        cleanup()
    }
    fun observeFiles() = dao.observeFiles()
    suspend fun filesForNode(id: String) = dao.forNode(id).mapNotNull { dao.get(it.attachmentId) }
    suspend fun filesForProject(id: String) = dao.forProject(id).mapNotNull { dao.get(it.attachmentId) }
    suspend fun availableFiles(): Map<String, AttachmentFileEntity> = withContext(Dispatchers.IO) {
        dao.files().filter { it.lifecycleState == "READY" && store.file(it.storageName).isFile }.associateBy { it.id }
    }
    fun localFile(file: AttachmentFileEntity) = store.file(file.storageName)

    /** Save original editor fields and every reference atomically, including newly created owners. */
    suspend fun <T> saveNodeDraft(draftId: String, owners: List<String>, description: String,
        removed: Set<String>, write: suspend (String) -> T): T = saveDraft(draftId, owners, false, description, removed, write)
    suspend fun <T> saveProjectDraft(draftId: String, owner: String, description: String,
        removed: Set<String>, write: suspend (String) -> T): T = saveDraft(draftId, listOf(owner), true, description, removed, write)

    private suspend fun <T> saveDraft(draftId: String, owners: List<String>, project: Boolean,
        description: String, removed: Set<String>, write: suspend (String) -> T): T = withContext(Dispatchers.IO) {
        val result = fileOperations.withLock {
            val reserved = store.reservations(draftId)
            val result = database.withTransaction {
                val original = owners.associateWith { owner ->
                    val text = if (project) database.projectDao().getById(owner)?.description else database.nodeDao().getById(owner)?.description
                    AttachmentReferences.ids(text.orEmpty())
                }
                val ids = AttachmentReferences.ids(description)
                val ready = ids.filter { id ->
                    val file = dao.get(id)
                    if (file == null) {
                        require(owners.all { id in original.getValue(it) }) { "Referencia de imagen desconocida." }
                        false
                    } else {
                        require(file.lifecycleState == "READY") { "Imagen pendiente de eliminación." }
                        require(id in reserved || dao.references(id) > 0) { "La imagen pertenece a otro borrador." }
                        true
                    }
                }
                val ownersExist = owners.all { owner ->
                    if (project) database.projectDao().getById(owner) != null else database.nodeDao().getById(owner) != null
                }
                // Existing owners can confirm references before the normal write. This also
                // preserves stable creation retries after a committed save interrupted in the UI.
                if (ownersExist && ready.size == ids.size) for (owner in owners) {
                    if (project) dao.attachProject(ready.map { ProjectAttachmentEntity(owner, it) })
                    else dao.attachNode(ready.map { NodeAttachmentEntity(owner, it) })
                }
                val result = write(if (ownersExist && ready.size == ids.size) description else AttachmentReferences.withoutReferences(description))
                for (owner in owners) {
                    if (project) {
                        val row = requireNotNull(database.projectDao().getById(owner))
                        dao.attachProject(ready.map { ProjectAttachmentEntity(owner, it) })
                        database.projectDao().update(owner, row.name, description, row.updatedAt)
                        (removed + (original.getValue(owner) - ids)).forEach { dao.detachProject(owner, it) }
                    } else {
                        val row = requireNotNull(database.nodeDao().getById(owner))
                        dao.attachNode(ready.map { NodeAttachmentEntity(owner, it) })
                        database.nodeDao().updateContent(owner, row.title, description, row.updatedAt)
                        (removed + (original.getValue(owner) - ids)).forEach { dao.detachNode(owner, it) }
                    }
                }
                result
            }
            // A committed save must not become a failed editor save due to deferred file cleanup.
            withContext(NonCancellable) {
                reserved.forEach { id -> runCatching { store.release(id) }.onFailure { android.util.Log.e("Attachments", "Reservation cleanup deferred", it) } }
            }
            result
        }
        try { cleanup() } catch (cancelled: CancellationException) { throw cancelled }
        catch (failure: Exception) { android.util.Log.e("Attachments", "File cleanup deferred", failure) }
        result
    }

    suspend fun cleanup() = withContext(Dispatchers.IO) {
        fileOperations.withLock {
            store.recover(dao.files())
            for (file in dao.files()) {
                database.withTransaction {
                    if (dao.references(file.id) > 0) store.release(file.id)
                    else if (!store.reserved(file.id)) dao.pending(file.id)
                }
                database.withTransaction {
                    val current = dao.get(file.id)
                    if (current?.lifecycleState == "DELETE_PENDING" && dao.references(file.id) == 0 && store.delete(file.storageName)) dao.delete(file.id)
                }
            }
        }
    }
}

internal suspend fun requireReady(database: Arachn0deDatabase, ids: Set<String>) {
    ids.forEach { require(database.attachmentDao().get(it)?.lifecycleState == "READY") { "Adjunto ausente o pendiente de eliminación: $it" } }
}
/** Call inside the owner's write transaction, including shared description updates. */
internal suspend fun ensureNodeReferences(database: Arachn0deDatabase, owner: String, description: String) {
    val ids = AttachmentReferences.ids(description)
    requireReady(database, ids)
    ids.forEach { require(database.attachmentDao().references(it) > 0) { "Confirma primero el adjunto mediante AttachmentRepository." } }
    if (ids.isNotEmpty()) database.attachmentDao().attachNode(ids.map { NodeAttachmentEntity(owner, it) })
}
internal suspend fun ensureProjectReferences(database: Arachn0deDatabase, owner: String, description: String) {
    val ids = AttachmentReferences.ids(description)
    requireReady(database, ids)
    ids.forEach { require(database.attachmentDao().references(it) > 0) { "Confirma primero el adjunto mediante AttachmentRepository." } }
    if (ids.isNotEmpty()) database.attachmentDao().attachProject(ids.map { ProjectAttachmentEntity(owner, it) })
}
