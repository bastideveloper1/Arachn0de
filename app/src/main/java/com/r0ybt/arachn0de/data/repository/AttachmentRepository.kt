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
    companion object { internal val fileOperations = Mutex() }

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
    /** Publish a validated derivative before replacing references; historical editable copies remain supported. */
    internal suspend fun saveAnnotated(sourceId:String,bytes:ByteArray,draftId:String?,replace:Boolean=false):AttachmentFileEntity = withContext(Dispatchers.IO) {
        val saved=fileOperations.withLock {
            val source=requireNotNull(dao.get(sourceId));require(source.lifecycleState=="READY")
            val editable=com.r0ybt.arachn0de.annotations.EditableImagePng.read(bytes)
            require(if(replace)editable==null else editable!=null) {"El formato de la imagen anotada no es válido."}
            if(draftId!=null) require(sourceId in store.reservations(draftId) || dao.references(sourceId)>0) {"La imagen pertenece a otro borrador."}
            val nodes=dao.nodeAttachments().filter {it.attachmentId==sourceId}
            val projects=dao.projectAttachments().filter {it.attachmentId==sourceId}
            require(draftId!=null || nodes.isNotEmpty() || projects.isNotEmpty()) {"Abre el editor del elemento para guardar la copia."}
            val reservation=draftId ?: "annotation-${java.util.UUID.randomUUID()}"
            var imported:AttachmentFileEntity?=null
            try {
                val copy=store.importBytes(bytes,source.originalName.substringBeforeLast('.')+"-anotada.png",reservation);imported=copy
                if(replace)require(copy.mimeType=="image/png")
                database.withTransaction {
                    requireReady(database,setOf(sourceId));dao.insert(copy)
                    if(draftId==null) {
                        val nodes=dao.nodeAttachments().filter {it.attachmentId==sourceId}
                        val projects=dao.projectAttachments().filter {it.attachmentId==sourceId}
                        require(nodes.isNotEmpty() || projects.isNotEmpty()) {"La imagen ya no pertenece al elemento."}
                        dao.attachNode(nodes.map {NodeAttachmentEntity(it.nodeId,copy.id)})
                        nodes.forEach {link->val node=requireNotNull(database.nodeDao().getById(link.nodeId));check(database.nodeDao().updateContent(node.id,node.title,if(replace) AttachmentReferences.replace(node.description,sourceId,copy.id) else node.description+"\n"+AttachmentReferences.token(copy.id),System.currentTimeMillis())==1)}
                        dao.attachProject(projects.map {ProjectAttachmentEntity(it.projectId,copy.id)})
                        projects.forEach {link->val project=requireNotNull(database.projectDao().getById(link.projectId));check(database.projectDao().update(project.id,project.name,if(replace) AttachmentReferences.replace(project.description,sourceId,copy.id) else project.description+"\n"+AttachmentReferences.token(copy.id),System.currentTimeMillis())==1)}
                        if(replace) {
                            nodes.forEach {dao.detachNode(it.nodeId,sourceId)}
                            projects.forEach {dao.detachProject(it.projectId,sourceId)}
                            if(dao.references(sourceId)==0 && !store.reserved(sourceId)) dao.pending(sourceId)
                        }
                    }
                }
                if(draftId==null) withContext(NonCancellable) {runCatching {store.release(copy.id)}}
                copy
            } catch(failure:Throwable) {
                withContext(NonCancellable) {imported?.let {if(dao.get(it.id)==null&&store.delete(it.storageName))store.release(it.id)}}
                throw failure
            }
        }
        if(replace && draftId==null) withContext(NonCancellable) {runCatching {cleanup()}}
        saved
    }

    internal suspend fun importPrivatePhoto(uri:Uri,draft:String):AttachmentFileEntity=withContext(Dispatchers.IO) {
        val clean=store.cleanPhoto(uri)
        try {deduplicatePhoto(importGuestBytes(clean.bytes,draft,if(clean.mime=="image/jpeg") "fotografia.jpg" else "fotografia.png"),draft)}
        finally {clean.bytes.fill(0)}
    }

    internal suspend fun importGallery(uri:Uri,draft:String):AttachmentFileEntity {
        return deduplicatePhoto(import(uri,draft),draft)
    }
    internal suspend fun deduplicatePhoto(imported:AttachmentFileEntity,draft:String):AttachmentFileEntity {
        return withContext(Dispatchers.IO) {fileOperations.withLock {
            val owned=store.reservations(draft)
            val shared=dao.files().firstOrNull {it.id!=imported.id && it.lifecycleState=="READY" && it.sha256==imported.sha256 && it.byteSize==imported.byteSize && it.mimeType==imported.mimeType && (dao.references(it.id)>0 || it.id in owned)}
            if(shared==null) imported else {
                store.retainGallery(shared.id,draft,shared.storageName)
                store.release(imported.id)
                database.withTransaction {dao.pending(imported.id)}
                runCatching {if(store.delete(imported.storageName)) dao.delete(imported.id)}
                shared
            }
        }}
    }
    internal suspend fun importGuestBytes(bytes:ByteArray,draft:String,name:String="captura-invitado.jpg"):AttachmentFileEntity=withContext(Dispatchers.IO) {
        fileOperations.withLock {
            val hash=java.security.MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") {"%02x".format(it)}
            store.reservations(draft).mapNotNull {dao.get(it)}.firstOrNull {it.sha256==hash && it.byteSize==bytes.size.toLong() && it.lifecycleState=="READY"}
                ?: store.importBytes(bytes,name,draft).also {dao.insert(it)}
        }
    }
    suspend fun pendingImports(draftId: String): List<AttachmentFileEntity> = withContext(Dispatchers.IO) {
        fileOperations.withLock { store.reservations(draftId).mapNotNull { dao.get(it) } }
    }
    suspend fun discardDraft(draftId: String) = withContext(Dispatchers.IO) {
        fileOperations.withLock { store.reservations(draftId).forEach { store.releaseGallery(it,draftId) } }
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
                // Audio finalized by the foreground service must survive a concurrent editor save.
                val audioUses=if(project) emptyList() else owners.flatMap {owner->
                    AttachmentReferences.matches(database.nodeDao().getById(owner)?.description.orEmpty())
                        .filter {AttachmentReferences.isAudio(it) && it.groupValues[1] !in removed && it.groupValues[1] !in AttachmentReferences.ids(description)}.map {it.value}.toList()
                }.distinct()
                val effectiveDescription=description+audioUses.joinToString(separator="\n",prefix=if(audioUses.isEmpty()) "" else "\n")
                val original = owners.associateWith { owner ->
                    val text = if (project) database.projectDao().getById(owner)?.description else database.nodeDao().getById(owner)?.description
                    AttachmentReferences.ids(text.orEmpty())
                }
                val ids = AttachmentReferences.ids(effectiveDescription)
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
                val result = write(if (ownersExist && ready.size == ids.size) effectiveDescription else AttachmentReferences.withoutReferences(effectiveDescription))
                // Do not commit references or overwrite content when the owner write was rejected.
                check(result != false && result != 0) { "No se confirmó el guardado del contenido." }
                for (owner in owners) {
                    if (project) {
                        val row = requireNotNull(database.projectDao().getById(owner))
                        dao.attachProject(ready.map { ProjectAttachmentEntity(owner, it) })
                        database.projectDao().update(owner, row.name, effectiveDescription, row.updatedAt)
                        (removed + (original.getValue(owner) - ids)).forEach { dao.detachProject(owner, it) }
                    } else {
                        val row = requireNotNull(database.nodeDao().getById(owner))
                        dao.attachNode(ready.map { NodeAttachmentEntity(owner, it) })
                        database.nodeDao().updateContent(owner, row.title, effectiveDescription, row.updatedAt)
                        (removed + (original.getValue(owner) - ids)).forEach { dao.detachNode(owner, it) }
                    }
                }
                result
            }
            // A committed save must not become a failed editor save due to deferred file cleanup.
            withContext(NonCancellable) {
                reserved.forEach { id -> runCatching { store.releaseGallery(id,draftId) }.onFailure { android.util.Log.w("Attachments", "Reservation cleanup deferred") } }
            }
            result
        }
        try { cleanup() } catch (cancelled: CancellationException) { throw cancelled }
        catch (failure: Exception) { android.util.Log.w("Attachments", "File cleanup deferred") }
        result
    }

    internal suspend fun retainGallery(draft:String,ids:Set<String>)=withContext(Dispatchers.IO) {
        fileOperations.withLock {requireReady(database,ids);ids.forEach {store.retainGallery(it,draft,requireNotNull(dao.get(it)).storageName)}}
    }
    internal suspend fun <T> saveGalleryDraft(draft:String,ids:Set<String>,write:suspend ()->T):T=withContext(Dispatchers.IO) {
        val result=fileOperations.withLock {
            val reserved=store.reservations(draft)
            val result=database.withTransaction {
                requireReady(database,ids)
                ids.forEach {require(it in reserved || dao.references(it)>0) {"La fotografía pertenece a otro borrador."}}
                write()
            }
            withContext(NonCancellable) {reserved.forEach {runCatching {store.releaseGallery(it,draft)}}}
            result
        }
        try {cleanup()} catch(c:CancellationException) {throw c} catch(e:Exception) {android.util.Log.w("Attachments","Gallery cleanup deferred")}
        result
    }

    suspend fun cleanup() = withContext(Dispatchers.IO) {
        fileOperations.withLock {
            store.recover(dao.files(), store.backupProtectedNames())
            for (file in dao.files()) {
                database.withTransaction {
                    if (dao.references(file.id) > 0) store.release(file.id)
                    else if (!store.reserved(file.id) && file.storageName !in store.backupProtectedNames()) dao.pending(file.id)
                }
                database.withTransaction {
                    val current = dao.get(file.id)
                    // Keep the file lock and Room transaction through the final check/delete:
                    // reservations and restore journals use this lock, references use Room.
                    if (current?.lifecycleState == "DELETE_PENDING" && dao.references(current.id) == 0 &&
                        !store.reserved(current.id) && current.storageName !in store.backupProtectedNames() &&
                        store.delete(current.storageName)) dao.delete(current.id)
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
