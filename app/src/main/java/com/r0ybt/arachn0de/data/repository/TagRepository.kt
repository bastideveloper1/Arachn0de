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
    fun observe() = database.invalidationTracker.createFlow("tags","node_tag","recurrence_tag","conversion_roots","conversion_tags").map {
        database.withTransaction {
            val catalog=dao.tags().sortedBy {it.normalizedName}.map {Tag(it.id,it.name,it.normalizedName)}
            val nodes=dao.nodeTags().groupBy {it.nodeId}.mapValues {(_,rows)->rows.mapTo(linkedSetOf()) {it.tagId}}
            val rules=dao.ruleTags().groupBy {it.ruleId}.mapValues {(_,rows)->rows.mapTo(linkedSetOf()) {it.tagId}}
            val projects=database.conversionDao().roots().mapNotNull {root->root.projectId?.let {root.id to it}}.toMap()
            val projectTags=database.conversionDao().tags().filter {it.rootId in projects}.groupBy {projects.getValue(it.rootId)}.mapValues {(_,rows)->rows.mapTo(linkedSetOf()) {it.tagId}}
            TagState(catalog,nodes,rules,projectTags)
        }
    }.flowOn(Dispatchers.IO)
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
