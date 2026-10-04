package com.r0ybt.arachn0de.data.repository

import androidx.room.withTransaction
import com.r0ybt.arachn0de.data.local.*
import com.r0ybt.arachn0de.domain.model.*
import kotlinx.coroutines.yield
import java.util.UUID

/** Local, resumable, transactional materialization; no Calendar view or Batch dependency. */
class RecurrenceRepository(
    private val database: Arachn0deDatabase,
    private val now: () -> Long = System::currentTimeMillis,
) {
    val tags = TagRepository(database)
    suspend fun tagIds(id: String) = database.tagDao().ruleIds(id).toSet()
    private val dao = database.recurrenceDao()
    val destinationProjects = database.projectDao().observeAll()
    suspend fun destinationNodes(projectId: String) = NodeRepository(database, now).getProjectNodes(projectId)
    val rules = dao.observeRules()
    val occurrences = dao.observeOccurrences()

    suspend fun get(id: String) = dao.get(id)
    suspend fun people(id: String): Set<String> = dao.people(id).toSet()

    suspend fun create(rule: RecurrenceRuleEntity, people: Set<String> = emptySet(), tagIds: Set<String> = emptySet()): String = database.withTransaction {
        validate(rule)
        tags.validate(tagIds)
        require(rule.status == RecurrenceStatus.ACTIVE.name && rule.nextIndex == 0L)
        validateDestination(rule)
        people.forEach { requireNotNull(database.personDao().get(it)) { "Responsable ausente." } }
        dao.get(rule.id)?.let {
            // Stable creation identity survives retry after a committed insert/materialization.
            check(it.copy(nextIndex = 0, status = "ACTIVE") == rule && dao.people(rule.id).toSet() == people && database.tagDao().ruleIds(rule.id).toSet() == tagIds) { "La regla ya existe con otra configuración." }
            return@withTransaction rule.id
        }
        dao.insert(rule)
        tags.assignRule(rule.id, tagIds)
        dao.assign(people.map { RecurrencePersonEntity(rule.id, it) })
        rule.id
    }

    /** At most 256 receipts per transaction. The cursor is never advanced past unprocessed dates. */
    suspend fun materializeBatch(at: Long = now()): Boolean = database.withTransaction {
        var remaining = 256
        var pending = false
        dao.rules().filter { it.status == "ACTIVE" }.forEach { initial ->
            validate(initial)
            var rule = initial
            if (!destinationExists(rule,allowLegacyAction=true)) {
                dao.update(rule.copy(status = "PAUSED"))
                return@forEach
            }
            val frequency = RecurrenceFrequency.valueOf(rule.frequency)
            while (true) {
                val day = RecurrenceSchedule.date(rule.startDay, frequency, rule.interval, rule.nextIndex)
                if (rule.endDay != null && day > rule.endDay!!) {
                    dao.update(rule.copy(status = "FINISHED"))
                    break
                }
                val due = RecurrenceSchedule.timestamp(day, rule.zoneId, rule.dueMinute)
                if (due > at) break
                if (remaining == 0) { pending = true; break }
                remaining--
                if (dao.occurrence(rule.id, day) == null) {
                    val id = UUID.nameUUIDFromBytes("recurrence:${rule.id}:$day".toByteArray(Charsets.UTF_8)).toString()
                    // Older rules could target a task before its first child existed. Preserve that
                    // deferred conversion at the first due materialization, not during migration.
                    rule.parentId?.let { parentId ->
                        if(database.nodeDao().getById(parentId)?.purpose=="ACTION")
                            check(NodeRepository(database,now).convertPurpose(parentId,NodePurpose.LAYER))
                    }
                    NodeRepository(database, now).createNode(rule.projectId, rule.parentId, rule.title, rule.description,
                        creationId = id,
                        startAt = rule.startOffsetMillis?.let { Math.subtractExact(due, it) }, dueAt = due,
                        obligation = rule.amountMinor?.let { Obligation(it, checkNotNull(rule.currencyCode)) },
                        responsibleIds = dao.people(rule.id).toSet(), tagIds = database.tagDao().ruleIds(rule.id).toSet(), priority = Priority.valueOf(rule.priority))
                    dao.record(RecurrenceOccurrenceEntity(rule.id, day, id))
                }
                rule = rule.copy(nextIndex = Math.addExact(rule.nextIndex, 1))
                dao.update(rule)
            }
        }
        pending
    }

    /** Defensive work budget per check, not a lifetime limit. True means the durable cursor has more debt. */
    suspend fun materializeDue(at: Long = now()): Boolean {
        repeat(16) {
            if (!materializeBatch(at)) return false
            yield()
        }
        return true
    }

    private suspend fun settle(at: Long) { while (materializeDue(at)) yield() }

    /** Settle ACTIVE debt first; never silently discard obligations on pause/edit/finish. */
    suspend fun setStatus(id: String, status: RecurrenceStatus) {
        val at = now()
        settle(at)
        database.withTransaction {
            val rule = requireNotNull(dao.get(id))
            val old = RecurrenceStatus.valueOf(rule.status)
            require(old != RecurrenceStatus.FINISHED || status == RecurrenceStatus.FINISHED) { "Una recurrencia finalizada no se puede reactivar." }
            require(status != RecurrenceStatus.ACTIVE || old == RecurrenceStatus.PAUSED) { "Solo se puede reanudar una regla pausada." }
            if (status == RecurrenceStatus.ACTIVE) require(destinationExists(rule,allowLegacyAction=true))
            val index = if (status == RecurrenceStatus.ACTIVE) maxOf(rule.nextIndex,
                RecurrenceSchedule.indexOnOrAfter(rule.startDay, RecurrenceFrequency.valueOf(rule.frequency), rule.interval,
                    RecurrenceSchedule.localDay(at, rule.zoneId))) else rule.nextIndex
            val day = RecurrenceSchedule.date(rule.startDay, RecurrenceFrequency.valueOf(rule.frequency), rule.interval, index)
            val effective = if (rule.endDay != null && day > rule.endDay) RecurrenceStatus.FINISHED else status
            dao.update(rule.copy(status = effective.name, nextIndex = index))
        }
        materializeDue(at)
    }

    /** Template only. Schedule identity is immutable so edits cannot accidentally replay old periods. */
    suspend fun editTemplate(id: String, projectId: String, parentId: String?, title: String, description: String,
        obligation: Obligation?, people: Set<String>, tagIds: Set<String>? = null, priority: Priority? = null) {
        settle(now())
        database.withTransaction {
            tagIds?.let { tags.validate(it) }
            val old = requireNotNull(dao.get(id))
            require(old.status != "FINISHED") { "La regla finalizada conserva su configuración histórica." }
            val updated = old.copy(projectId = projectId, parentId = parentId, title = title.trim(), description = description,
                amountMinor = obligation?.amountMinor, currencyCode = obligation?.currencyCode, priority = priority?.name ?: old.priority)
            validate(updated); validateDestination(updated)
            people.forEach { requireNotNull(database.personDao().get(it)) }
            tagIds?.let { tags.assignRule(id, it) }
            dao.update(updated); dao.clearPeople(id); dao.assign(people.map { RecurrencePersonEntity(id, it) })
        }
    }

    private suspend fun destinationExists(rule: RecurrenceRuleEntity,allowLegacyAction:Boolean=false): Boolean {
        if (database.projectDao().getById(rule.projectId) == null) return false
        val parent = rule.parentId?.let { database.nodeDao().getById(it) ?: return false }
        return parent == null || (parent.projectId == rule.projectId && (parent.purpose == "LAYER" || (allowLegacyAction && parent.purpose=="ACTION")) && parent.amountMinor == null)
    }
    private suspend fun validateDestination(rule: RecurrenceRuleEntity) {
        require(destinationExists(rule)) { "El destino ya no existe o no puede recibir tareas. Edita el destino antes de reanudar." }
    }

    companion object {
        fun validate(rule: RecurrenceRuleEntity) {
            require(rule.id.isNotBlank() && rule.projectId.isNotBlank())
            require(rule.title.isNotBlank() && TitleLimits.count(rule.title) <= TitleLimits.NODE)
            Priority.valueOf(rule.priority)
            RecurrenceStatus.valueOf(rule.status)
            val frequency = RecurrenceFrequency.valueOf(rule.frequency)
            RecurrenceSchedule.format(rule.startDay)
            require(rule.interval in 1..10000 && rule.nextIndex >= 0)
            rule.endDay?.let { require(it >= rule.startDay); RecurrenceSchedule.format(it) }
            RecurrenceSchedule.date(rule.startDay, frequency, rule.interval, rule.nextIndex)
            RecurrenceSchedule.timestamp(rule.startDay, rule.zoneId, rule.dueMinute)
            require(rule.startOffsetMillis == null || rule.startOffsetMillis >= 0)
            require((rule.amountMinor == null) == (rule.currencyCode == null))
            rule.amountMinor?.let { Obligation(it, checkNotNull(rule.currencyCode)) }
        }
    }
}
