package com.r0ybt.arachn0de.data.repository

import android.net.Uri
import androidx.room.withTransaction
import com.r0ybt.arachn0de.data.local.*
import com.r0ybt.arachn0de.domain.model.Person
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class PersonRepository(private val database: Arachn0deDatabase, private val avatars: AvatarStore) {
    private val dao = database.personDao()
    fun observePeople() = dao.observePeople().map { rows -> rows.map { Person(it.id, it.name, it.avatarFile) } }
    fun observeAssignments(projectId: String) = dao.observeAssignments(projectId).map { rows ->
        rows.groupBy { it.nodeId }.mapValues { (_, people) -> people.map { Person(it.id, it.name, it.avatarFile) } }
    }
    suspend fun importAvatar(uri: Uri): String {
        var file: String? = null
        try {
            return withContext(Dispatchers.IO) { avatars.import(uri).also { file = it } }
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            withContext(kotlinx.coroutines.NonCancellable + Dispatchers.IO) { file?.let(avatars::delete) }
            throw cancelled
        }
    }
    suspend fun discardAvatar(file: String?) = withContext(Dispatchers.IO) {
        // Cleanup is best effort: a confirmed database write must not be reported as failed
        // because a redundant file could not be removed.
        try {
            if (file != null && dao.avatarReferences(file) == 0) avatars.delete(file)
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (_: Exception) { }
    }
    suspend fun save(id: String, name: String, avatarFile: String?, isNew: Boolean = true): Boolean {
        val trimmed = name.trim()
        require(trimmed.isNotEmpty())
        if (avatarFile != null) require(withContext(Dispatchers.IO) { avatars.exists(avatarFile) })
        var previous: String? = null
        val result = database.withTransaction {
            val current = dao.get(id)
            previous = current?.avatarFile
            if (current == null && !isNew) return@withTransaction false
            if (current != null && isNew) return@withTransaction current.name == trimmed && current.avatarFile == avatarFile
            if (current == null) { dao.insert(PersonEntity(id, trimmed, avatarFile)); true }
            else dao.update(id, trimmed, avatarFile) == 1
        }
        if (result && previous != avatarFile) discardAvatar(previous)
        return result
    }
    suspend fun delete(id: String): Boolean {
        var previous: String? = null
        val result = database.withTransaction {
            previous = dao.get(id)?.avatarFile
            dao.delete(id) == 1
        }
        if (result) discardAvatar(previous)
        return result
    }
    suspend fun setResponsiblePeople(nodeId: String, ids: Set<String>): Boolean = database.withTransaction {
        if (dao.nodeExists(nodeId) == 0 || ids.any { dao.get(it) == null }) return@withTransaction false
        dao.clearAssignments(nodeId)
        dao.assign(ids.map { NodePersonEntity(nodeId, it) })
        true
    }
}
