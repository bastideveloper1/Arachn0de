package com.r0ybt.arachn0de.data.repository

import android.net.Uri
import androidx.room.withTransaction
import com.r0ybt.arachn0de.data.local.*
import com.r0ybt.arachn0de.domain.model.Person
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.flow.map

class PersonRepository(private val database: Arachn0deDatabase, private val avatars: AvatarStore) {
    private val dao = database.personDao()
    fun observePeople() = dao.observePeople().map { rows -> rows.map { Person(it.id, it.name, it.avatarFile, it.avatarZoom, it.avatarX, it.avatarY) } }
    fun observeAssignments(projectId: String) = dao.observeAssignments(projectId).map { rows ->
        rows.groupBy { it.nodeId }.mapValues { (_, people) -> people.map { Person(it.id, it.name, it.avatarFile, it.avatarZoom, it.avatarX, it.avatarY) } }
    }
    fun observeAllAssignments() = dao.observeAllAssignments().map { rows ->
        rows.groupBy { it.nodeId }.mapValues { (_, people) -> people.map { Person(it.id, it.name, it.avatarFile, it.avatarZoom, it.avatarX, it.avatarY) } }
    }
    private suspend fun <T> files(work: suspend () -> T): T = withContext(Dispatchers.IO) {
        AttachmentRepository.fileOperations.withLock { work() }
    }
    suspend fun importAvatar(uri: Uri): String {
        var file: String? = null
        try { return files {
            val coroutine = currentCoroutineContext()
            avatars.import(uri) { coroutine.ensureActive() }.also { file = it }
        } } catch (cancelled: CancellationException) {
            withContext(NonCancellable) { discardAvatar(file) }; throw cancelled
        }
    }
    suspend fun retainAvatar(file: String?, draftId: String) = files { file?.let { avatars.lifecycle.reserve(it, draftId) } }
    suspend fun discardAvatar(file: String?, draftId: String? = null) = files {
        if (file != null) runCatching {
            avatars.lifecycle.release(file)
            draftId?.let { avatars.lifecycle.release(file, it) }
            database.withTransaction { if (dao.avatarReferences(file) == 0) avatars.delete(file) }
        }
        Unit
    }
    suspend fun save(id: String, name: String, avatarFile: String?, isNew: Boolean = true, draftId: String? = null, zoom: Float = 1f, x: Float = 0f, y: Float = 0f): Boolean = files {
        com.r0ybt.arachn0de.domain.model.AvatarFraming(zoom, x, y).validate()
        val trimmed = name.trim(); require(trimmed.isNotEmpty())
        recoverLocked()
        if (avatarFile != null) require(avatars.exists(avatarFile))
        try {
            val result = database.withTransaction {
                val current = dao.get(id)
                if (current == null && !isNew) return@withTransaction false
                if (current != null && isNew) return@withTransaction current.name == trimmed && current.avatarFile == avatarFile && current.avatarZoom == zoom && current.avatarX == x && current.avatarY == y
                val names = setOfNotNull(current?.avatarFile, avatarFile)
                if (names.isNotEmpty()) avatars.durable.record(names)
                if (current == null) { dao.insert(PersonEntity(id, trimmed, avatarFile, zoom, x, y)); true }
                else dao.update(id, trimmed, avatarFile, zoom, x, y) == 1
            }
            if (result && avatarFile != null) withContext(NonCancellable) { runCatching {
                avatars.lifecycle.release(avatarFile); draftId?.let { avatars.lifecycle.release(avatarFile, it) }
            } }
            result
        } finally { withContext(NonCancellable) { runCatching { recoverLocked() } } }
    }
    suspend fun delete(id: String): Boolean = files {
        recoverLocked()
        try { database.withTransaction {
            val old = dao.get(id) ?: return@withTransaction false
            old.avatarFile?.let { avatars.durable.record(setOf(it)) }
            dao.delete(id) == 1
        } } finally { withContext(NonCancellable) { runCatching { recoverLocked() } } }
    }
    suspend fun cleanup() = files {
        recoverLocked()
        database.withTransaction {
            avatars.cleanup(database.backupDao().persons().mapNotNull { it.avatarFile }.toSet(), avatars.durable.recorded())
        }
    }
    private suspend fun recoverLocked() {
        if (!avatars.durable.hasJournal()) return
        val names = avatars.durable.recorded()
        database.withTransaction { names.forEach { if (dao.avatarReferences(it) == 0) avatars.durable.delete(it) } }
        avatars.durable.clearJournal()
    }
    suspend fun setResponsiblePeople(nodeId: String, ids: Set<String>): Boolean = database.withTransaction {
        if (dao.nodeExists(nodeId) == 0 || ids.any { dao.get(it) == null }) return@withTransaction false
        dao.clearAssignments(nodeId)
        dao.assign(ids.map { NodePersonEntity(nodeId, it) })
        true
    }
}
