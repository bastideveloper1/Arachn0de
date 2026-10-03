package com.r0ybt.arachn0de.data.repository

import androidx.room.withTransaction
import com.r0ybt.arachn0de.data.local.*
import com.r0ybt.arachn0de.domain.model.*
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.flowOn

class TagRepository(private val database: Arachn0deDatabase) {
    private val dao = database.tagDao()
    fun observe() = dao.observe().map { rows ->
        val nodes = mutableMapOf<String, MutableSet<String>>()
        val rules = mutableMapOf<String, MutableSet<String>>()
        rows.forEach { row ->
            row.nodes.forEach { nodes.getOrPut(it.nodeId) { linkedSetOf() }.add(it.tagId) }
            row.rules.forEach { rules.getOrPut(it.ruleId) { linkedSetOf() }.add(it.tagId) }
        }
        TagState(rows.map { Tag(it.tag.id, it.tag.name, it.tag.normalizedName) }, nodes, rules)
    }.flowOn(Dispatchers.Default)
    suspend fun create(name: String): Tag = database.withTransaction {
        val display = TagNames.display(name)
        val key = TagNames.normalize(display)
        val row = dao.named(key) ?: TagEntity(UUID.randomUUID().toString(), display, key).also { dao.insert(listOf(it)) }
        Tag(row.id, row.name, row.normalizedName)
    }
    suspend fun rename(id: String, name: String) = database.withTransaction {
        val row = requireNotNull(dao.get(id)) { "Etiqueta inexistente." }
        val key = TagNames.normalize(name)
        require(dao.named(key)?.id.let { it == null || it == id }) { "Ya existe una etiqueta con ese nombre." }
        dao.update(row.copy(name = TagNames.display(name), normalizedName = key))
    }
    suspend fun delete(id: String) = database.withTransaction { dao.delete(id) }
    internal suspend fun validate(ids: Set<String>) { ids.forEach { requireNotNull(dao.get(it)) { "Etiqueta inexistente." } } }
    suspend fun assignNode(id: String, ids: Set<String>) = database.withTransaction {
        requireNotNull(database.nodeDao().getById(id)) { "Nodo inexistente." }
        validate(ids); dao.clearNode(id); dao.assignNodes(ids.map { NodeTagEntity(id, it) })
    }
    internal suspend fun assignRule(id: String, ids: Set<String>) {
        validate(ids); dao.clearRule(id); dao.assignRules(ids.map { RecurrenceTagEntity(id, it) })
    }
}
