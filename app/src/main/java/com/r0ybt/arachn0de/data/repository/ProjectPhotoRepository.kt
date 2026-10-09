package com.r0ybt.arachn0de.data.repository

import android.net.Uri
import androidx.room.withTransaction
import com.r0ybt.arachn0de.backup.validateAvatar
import com.r0ybt.arachn0de.data.local.*
import com.r0ybt.arachn0de.domain.model.AvatarFraming
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.withLock
import java.util.UUID

internal class ProjectPhotoRepository(private val database: Arachn0deDatabase, private val store: ProjectPhotoStore) {
    private val dao = database.projectPhotoDao()
    private suspend fun <T> files(work: suspend () -> T): T = withContext(Dispatchers.IO) {
        AttachmentRepository.fileOperations.withLock { work() }
    }
    suspend fun import(uri: Uri, owner: String): String {
        var name: String? = null
        try { return files {
            val job = currentCoroutineContext()
            store.import(uri) { job.ensureActive() }.also { name = it; store.lifecycle.reserve(it, owner) }
        } } catch (cancelled: CancellationException) {
            withContext(NonCancellable) { name?.let { discard(setOf(it), owner) } }; throw cancelled
        } catch (failure: Exception) {
            withContext(NonCancellable) { name?.let { discard(setOf(it), owner) } }; throw failure
        }
    }
    suspend fun retain(name: String, owner: String) = files { store.lifecycle.reserve(name, owner) }
    suspend fun discard(names: Set<String>, owner: String) = files {
        names.forEach { name ->
            store.lifecycle.release(name, owner); store.lifecycle.release(name)
            if (dao.references(name) == 0) store.durable.delete(name)
        }
    }
    suspend fun save(projectId: String, name: String?, framing: AvatarFraming, owner: String): Boolean = files {
        framing.validate()
        recoverLocked()
        name?.let { validateAvatar(store.durable.read(it)) }
        try {
            val success = database.withTransaction {
                if (database.projectDao().getById(projectId) == null) return@withTransaction false
                val old = dao.forProject(projectId)
                store.durable.record(setOfNotNull(old?.file, name))
                if (name == null) old?.let { dao.delete(it.id) }
                else dao.save(ProjectPhotoEntity(old?.id ?: UUID.randomUUID().toString(), projectId, null, name, framing.zoom, framing.x, framing.y))
                true
            }
            if (success && name != null) withContext(NonCancellable) {
                runCatching { store.lifecycle.release(name, owner); store.lifecycle.release(name) }
            }
            success
        } finally { withContext(NonCancellable) { runCatching { recoverLocked() } } }
    }
    suspend fun cleanup() = files {
        recoverLocked()
        store.cleanup(dao.confirmed().mapTo(hashSetOf()) { it.file })
    }
    private suspend fun recoverLocked() {
        // FK SET NULL leaves a durable record when either kind of owner is deleted.
        dao.all().filter { it.projectId == null && it.nodeId == null }.forEach { row ->
            store.lifecycle.queue(row.file)
            if (dao.references(row.file) == 0) store.durable.delete(row.file)
            dao.delete(row.id)
        }
        if (store.durable.hasJournal()) {
            store.durable.recorded().forEach { if (dao.references(it) == 0) store.durable.delete(it) }
            store.durable.clearJournal()
        }
    }
}
