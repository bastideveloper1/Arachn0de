package com.r0ybt.arachn0de.data.repository

import androidx.room.withTransaction
import com.r0ybt.arachn0de.data.local.Arachn0deDatabase
import com.r0ybt.arachn0de.data.local.NodePersonEntity
import com.r0ybt.arachn0de.data.local.NodeEventEntity
import com.r0ybt.arachn0de.data.local.toEvent
import com.r0ybt.arachn0de.domain.model.Priority
import com.r0ybt.arachn0de.domain.model.NodeEventType
import com.r0ybt.arachn0de.data.local.NodeEntity
import com.r0ybt.arachn0de.data.local.toNode
import com.r0ybt.arachn0de.domain.model.Node
import com.r0ybt.arachn0de.domain.model.Obligation
import com.r0ybt.arachn0de.domain.model.GeneratedNodeSpec
import com.r0ybt.arachn0de.domain.model.NodeBatchGenerator
import com.r0ybt.arachn0de.domain.model.NodePurpose
import com.r0ybt.arachn0de.domain.model.NodeProgress
import com.r0ybt.arachn0de.domain.model.NodeTreeSnapshot
import java.util.UUID
import com.r0ybt.arachn0de.domain.model.TitleLimits
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map

class NodeRepository(
    private val database: Arachn0deDatabase,
    private val currentTimeMillis: () -> Long = System::currentTimeMillis,
) {
    val tags = TagRepository(database)
    val recurrence = RecurrenceRepository(database, currentTimeMillis)
    private val nodeDao = database.nodeDao()

    fun observeHistory(nodeId: String) = database.nodeEventDao().observe(nodeId).map { rows -> rows.map { it.toEvent() } }
        .flowOn(Dispatchers.Default)

    private suspend fun appendEvent(nodeId: String, type: NodeEventType, occurredAt: Long) {
        val id = if (type == NodeEventType.CREATED) UUID.nameUUIDFromBytes("node-created:$nodeId".toByteArray(Charsets.UTF_8)).toString()
            else UUID.randomUUID().toString()
        database.nodeEventDao().insert(NodeEventEntity(id, nodeId, type.name, occurredAt))
    }

    /** Reopen a completed ACTION leaf before it becomes a NOTE/container; triggers remain the defence. */
    private suspend fun reopenBeforeConversion(id: String?, at: Long) {
        if (id == null) return
        val current = nodeDao.getById(id) ?: return
        if (current.isCompleted) check(changeCompletion(current, false, at, updatedAt = current.updatedAt))
    }

    fun observeAllState(): Flow<NodeTreeSnapshot> =
        nodeDao.observeAllNodes().map(::snapshot).flowOn(Dispatchers.Default)

    fun observeProjectState(projectId: String): Flow<NodeTreeSnapshot> =
        nodeDao.observeProjectNodes(projectId).map(::snapshot).flowOn(Dispatchers.Default)

    /** Repair legacy order before the screen starts observing; normal observers remain read-only. */
    fun observePreparedProjectState(projectId: String): Flow<NodeTreeSnapshot> = flow {
        normalizeProjectOrder(projectId)
        emitAll(observeProjectState(projectId))
    }.flowOn(Dispatchers.Default)

    suspend fun normalizeProjectOrder(projectId: String) = database.withTransaction {
        nodeDao.getProjectNodes(projectId).groupBy { it.parentId }.values.forEach { siblings ->
            writeOrder(sortSiblingsForDisplay(siblings))
        }
    }

    /** A stale parent is rejected. At a boundary this is a successful no-op (plus normalization). */
    suspend fun reorderNode(id: String, expectedParentId: String?, moveUp: Boolean): Boolean = database.withTransaction {
        val current = nodeDao.getById(id) ?: return@withTransaction false
        if (current.parentId != expectedParentId) return@withTransaction false
        val siblings = sortSiblingsForDisplay(nodeDao.getSiblings(current.projectId, current.parentId))
        val sameGroup = siblings.filter { it.isCompleted == current.isCompleted }
        val from = sameGroup.indexOfFirst { it.id == id }
        check(from >= 0)
        val to = from + if (moveUp) -1 else 1
        val changedIds = if (to in sameGroup.indices) {
            val other = sameGroup[to]
            val ordered = siblings.toMutableList()
            val fromIndex = ordered.indexOfFirst { it.id == id }
            val toIndex = ordered.indexOfFirst { it.id == other.id }
            ordered[fromIndex] = other
            ordered[toIndex] = current
            writeOrder(ordered, setOf(id, other.id))
            setOf(id, other.id)
        } else emptySet()
        if (changedIds.isEmpty()) return@withTransaction true
        true
    }

    /** Drop onto a sibling's slot; reject stale parents or completion groups atomically. */
    suspend fun reorderNodeTo(id: String, expectedParentId: String?, targetId: String): Boolean = database.withTransaction {
        val current = nodeDao.getById(id) ?: return@withTransaction false
        val target = nodeDao.getById(targetId) ?: return@withTransaction false
        if (current.parentId != expectedParentId || target.parentId != expectedParentId ||
            current.projectId != target.projectId || current.isCompleted != target.isCompleted) return@withTransaction false
        val ordered = sortSiblingsForDisplay(nodeDao.getSiblings(current.projectId, expectedParentId)).toMutableList()
        val from = ordered.indexOfFirst { it.id == id }
        val to = ordered.indexOfFirst { it.id == targetId }
        ordered.add(to, ordered.removeAt(from))
        writeOrder(ordered, if (from == to) emptySet() else setOf(id, targetId))
        true
    }

    private suspend fun writeOrder(siblings: List<NodeEntity>, changedIds: Set<String> = emptySet()) {
        val now = if (changedIds.isEmpty()) null else currentTimeMillis()
        siblings.forEachIndexed { position, node ->
            if (node.position != position || node.id in changedIds) {
                check(nodeDao.updateOrder(node.id, position, if (node.id in changedIds) checkNotNull(now) else node.updatedAt) == 1)
            }
        }
    }

    private fun sortSiblingsForDisplay(siblings: List<NodeEntity>): List<NodeEntity> =
        siblings.sortedWith(
            compareBy<NodeEntity> { if (it.isCompleted) 1 else 0 }
                .thenBy { it.position }
                .thenBy { it.createdAt }
                .thenBy { it.id },
        )

    fun observeRootNodes(projectId: String): Flow<List<Node>> =
        observeProjectState(projectId).map { it.childrenOf(null) }

    fun observeChildren(projectId: String, parentId: String): Flow<List<Node>> =
        observeProjectState(projectId).map { it.childrenOf(parentId) }

    suspend fun getNode(id: String): Node? = database.withTransaction {
        nodeDao.getById(id)?.let { it.toNode(nodeDao.hasChildren(it.projectId, id)) }
    }

    suspend fun createNode(
        projectId: String,
        parentId: String?,
        title: String,
        description: String = "",
        creationId: String = UUID.randomUUID().toString(),
        startAt: Long? = null,
        dueAt: Long? = null,
        purpose: NodePurpose = NodePurpose.ACTION,
        obligation: Obligation? = null,
        responsibleIds: Set<String> = emptySet(),
        tagIds: Set<String> = emptySet(),
        priority: Priority = Priority.NONE,
    ): Node = database.withTransaction {
        com.r0ybt.arachn0de.domain.model.TaskTemporal.validateDates(startAt, dueAt)
        require(purpose == NodePurpose.ACTION || obligation == null) { "Una nota no puede ser obligación." }
        require(purpose == NodePurpose.ACTION || priority == Priority.NONE) { "Una nota no admite prioridad." }
        val normalizedTitle = validateTitle(title)
        require(creationId.isNotBlank())
        nodeDao.getById(creationId)?.let { existing ->
            check(existing.projectId == projectId && existing.parentId == parentId &&
                existing.title == normalizedTitle && existing.description == description &&
                existing.startAt == startAt && existing.dueAt == dueAt && existing.purpose == purpose.name &&
                existing.amountMinor == obligation?.amountMinor && existing.currencyCode == obligation?.currencyCode && existing.priority == priority.name &&
                database.personDao().assignmentIds(existing.id).toSet() == responsibleIds &&
                database.tagDao().nodeIds(existing.id).toSet() == tagIds) {
                "Creation already committed with different content or destination"
            }
            return@withTransaction existing.toNode(nodeDao.hasChildren(projectId, creationId))
        }
        require(database.projectDao().getById(projectId) != null) { "Project not found" }
        validateParent(projectId, parentId)
        responsibleIds.forEach { requireNotNull(database.personDao().get(it)) { "Person not found" } }
        tags.validate(tagIds)
        val now = currentTimeMillis()
        val entity = NodeEntity(
            id = creationId, projectId = projectId, parentId = parentId,
            title = normalizedTitle, description = description, isCompleted = false,
            position = nextPosition(projectId, parentId), createdAt = now, updatedAt = now,
            startAt = startAt, dueAt = dueAt, purpose = purpose.name,
            amountMinor = obligation?.amountMinor, currencyCode = obligation?.currencyCode, priority = priority.name,
        )
        reopenBeforeConversion(parentId, now)
        nodeDao.insert(entity)
        appendEvent(entity.id, NodeEventType.CREATED, now)
        if (responsibleIds.isNotEmpty()) database.personDao().assign(responsibleIds.map { NodePersonEntity(entity.id, it) })
        tags.assignNode(entity.id, tagIds)
        entity.toNode(hasChildren = false)
    }

    /** The preview's exact specs are committed with assignments in one transaction. Stable IDs make retries safe. */
    suspend fun createBatch(
        projectId: String,
        parentId: String?,
        batchId: String,
        specifications: List<GeneratedNodeSpec>,
        responsibleIds: Set<String> = emptySet(),
        tagIds: Set<String> = emptySet(),
    ): List<Node> {
        // Snapshot caller-owned collections before suspending.
        val specs = specifications.toList()
        val people = responsibleIds.toSet()
        val labels = tagIds.toSet()
        NodeBatchGenerator.validateSpecs(specs)
        require(batchId.isNotBlank())
        val ids = specs.indices.map { UUID.nameUUIDFromBytes("$batchId:$it".toByteArray(Charsets.UTF_8)).toString() }
        return database.withTransaction {
            val existing = ids.map { nodeDao.getById(it) }
            if (existing.any { it != null }) {
                check(nodeDao.getById(UUID.nameUUIDFromBytes("$batchId:${specs.size}".toByteArray(Charsets.UTF_8)).toString()) == null) { "El lote ya fue creado con otra cantidad." }
                check(existing.all { it != null }) { "El lote ya existe parcialmente; no se puede repetir." }
                existing.mapIndexed { index, row ->
                    val node = checkNotNull(row)
                    val spec = specs[index]
                    check(node.projectId == projectId && node.parentId == parentId && node.title == spec.title &&
                        node.description == spec.description && node.purpose == spec.purpose.name &&
                        node.startAt == null && node.dueAt == spec.dueAt &&
                        node.amountMinor == spec.obligation?.amountMinor && node.currencyCode == spec.obligation?.currencyCode && node.priority == spec.priority.name &&
                        database.personDao().assignmentIds(node.id).toSet() == people && database.tagDao().nodeIds(node.id).toSet() == labels) {
                        "El lote ya fue creado con otros datos."
                    }
                    node.toNode(nodeDao.hasChildren(projectId, node.id))
                }
            } else {
                require(database.projectDao().getById(projectId) != null) { "Project not found" }
                validateParent(projectId, parentId)
                people.forEach { requireNotNull(database.personDao().get(it)) { "Person not found" } }
                tags.validate(labels)
                var maximum = nodeDao.maxPosition(projectId, parentId) ?: -1
                if (maximum.toLong() + specs.size > Int.MAX_VALUE) {
                    writeOrder(sortSiblingsForDisplay(nodeDao.getSiblings(projectId, parentId)))
                    maximum = nodeDao.maxPosition(projectId, parentId) ?: -1
                }
                check(maximum.toLong() + specs.size <= Int.MAX_VALUE) { "Sibling position exhausted" }
                val now = currentTimeMillis()
                specs.mapIndexed { index, spec ->
                    val entity = NodeEntity(ids[index], projectId, parentId, spec.title, spec.description,
                        false, maximum + 1 + index, now, now, startAt = null, dueAt = spec.dueAt, purpose = spec.purpose.name,
                        amountMinor = spec.obligation?.amountMinor, currencyCode = spec.obligation?.currencyCode, priority = spec.priority.name)
                    reopenBeforeConversion(parentId, now)
                    nodeDao.insert(entity)
                    appendEvent(entity.id, NodeEventType.CREATED, now)
                    if (people.isNotEmpty()) database.personDao().assign(people.map {
                        NodePersonEntity(entity.id, it)
                    })
                    tags.assignNode(entity.id, labels)
                    entity.toNode(hasChildren = false)
                }
            }
        }
    }

    /** Content edits never change structure, completion, identity or sibling order. */
    suspend fun updateNode(id: String, title: String, description: String = ""): Boolean =
        database.withTransaction {
            nodeDao.updateContent(id, validateTitle(title), description, currentTimeMillis()) == 1
        }

    /** Dates are operational only for leaves; content-only edits preserve dormant layer dates. */
    suspend fun updateNodeWithDates(id: String, title: String, description: String, startAt: Long?, dueAt: Long?): Boolean =
        database.withTransaction {
            com.r0ybt.arachn0de.domain.model.TaskTemporal.validateDates(startAt, dueAt)
            val current = nodeDao.getById(id) ?: return@withTransaction false
            if (current.purpose != NodePurpose.ACTION.name || nodeDao.hasChildren(current.projectId, id)) return@withTransaction false
            nodeDao.updateContentAndDates(id, validateTitle(title), description, startAt, dueAt, currentTimeMillis()) == 1
        }

    /** Financial/content/date edits are atomic and preserve completion, identity and assignments. */
    suspend fun updateLeaf(id: String, title: String, description: String, startAt: Long?, dueAt: Long?, obligation: Obligation?, removeObligation: Boolean = false): Boolean = database.withTransaction {
        com.r0ybt.arachn0de.domain.model.TaskTemporal.validateDates(startAt, dueAt)
        val current = nodeDao.getById(id) ?: return@withTransaction false
        if (current.purpose != NodePurpose.ACTION.name || nodeDao.hasChildren(current.projectId, id)) return@withTransaction false
        require(current.amountMinor == null || obligation != null || removeObligation) { "Confirma la eliminación de los datos financieros." }
        nodeDao.updateLeaf(id, validateTitle(title), description, startAt, dueAt, obligation?.amountMinor, obligation?.currencyCode, currentTimeMillis()) == 1
    }

    suspend fun updateEditor(id: String, title: String, description: String, startAt: Long?, dueAt: Long?,
        obligation: Obligation?, removeObligation: Boolean, editDates: Boolean, tagIds: Set<String>, priority: Priority? = null): Boolean = database.withTransaction {
        tags.validate(tagIds)
        val updated = if (editDates) updateLeaf(id, title, description, startAt, dueAt, obligation, removeObligation)
            else updateNode(id, title, description)
        if (updated) {
            priority?.let { check(setPriority(id, it)) { "Este elemento ya no admite prioridad." } }
            tags.assignNode(id, tagIds)
        }
        updated
    }

    suspend fun setPriority(id: String, priority: Priority): Boolean = database.withTransaction {
        val current = nodeDao.getById(id) ?: return@withTransaction false
        if (current.purpose != NodePurpose.ACTION.name || nodeDao.hasChildren(current.projectId, id)) return@withTransaction false
        if (current.priority == priority.name) return@withTransaction true
        nodeDao.updatePriority(id, priority.name, currentTimeMillis()) == 1
    }

    /** Conversion retains identity, content, dates, position and assignments; completion is cleared. */
    suspend fun convertPurpose(id: String, purpose: NodePurpose, removeObligation: Boolean = false): Boolean = database.withTransaction {
        val current = nodeDao.getById(id) ?: return@withTransaction false
        if (nodeDao.hasChildren(current.projectId, id)) return@withTransaction false
        if (current.purpose == purpose.name) return@withTransaction true
        require(current.amountMinor == null || removeObligation) { "Confirma la eliminación de los datos financieros." }
        val at = currentTimeMillis()
        reopenBeforeConversion(id, at)
        check(nodeDao.updatePurpose(id, purpose.name, at) == 1) { "Purpose change was not written" }
        true
    }

    /** A null parent explicitly moves the node to the project root. */
    suspend fun moveNode(id: String, parentId: String?): Boolean = database.withTransaction {
        val current = nodeDao.getById(id) ?: return@withTransaction false
        validateParent(current.projectId, parentId, movingId = id)
        if (current.parentId == parentId) return@withTransaction true
        val at = currentTimeMillis()
        reopenBeforeConversion(parentId, at)
        check(nodeDao.move(id, parentId, nextPosition(current.projectId, parentId), at) == 1) { "Move was not written" }
        true
    }

    suspend fun setCompleted(id: String, completed: Boolean): Boolean = database.withTransaction {
        val current = nodeDao.getById(id) ?: return@withTransaction false
        changeCompletion(current, completed, currentTimeMillis())
    }

    suspend fun toggleCompleted(id: String): Boolean = database.withTransaction {
        val current = nodeDao.getById(id) ?: return@withTransaction false
        changeCompletion(current, !current.isCompleted, currentTimeMillis())
    }

    /** Read/validate current persisted state under the same Room transaction as both writes. */
    private suspend fun changeCompletion(current: NodeEntity, completed: Boolean, at: Long, updatedAt: Long = at): Boolean {
        if (current.purpose != NodePurpose.ACTION.name || nodeDao.hasChildren(current.projectId, current.id)) return false
        if (current.isCompleted == completed) return true
        if (nodeDao.setCompleted(current.id, completed, updatedAt) != 1) return false
        appendEvent(current.id, if (completed) NodeEventType.COMPLETED else NodeEventType.REOPENED, at)
        return true
    }

    suspend fun deleteNode(id: String): Boolean = database.withTransaction { nodeDao.delete(id) == 1 }

    suspend fun calculateProgress(nodeId: String): NodeProgress? = database.withTransaction {
        val current = nodeDao.getById(nodeId) ?: return@withTransaction null
        snapshot(nodeDao.getProjectNodes(current.projectId)).progressById[nodeId]
    }

    suspend fun calculateProjectProgress(projectId: String): NodeProgress? = database.withTransaction {
        val projectNodes = nodeDao.getProjectNodes(projectId)
        if (projectNodes.isEmpty()) {
            return@withTransaction NodeProgress(
                nodeId = projectId,
                completed = 0,
                total = 0,
                percentage = 0,
                state = com.r0ybt.arachn0de.domain.model.NodeProgressState.NO_WORK,
            )
        }
        snapshot(projectNodes).projectProgressById[projectId]
    }

    suspend fun getProjectNodes(projectId: String): List<Node> =
        snapshot(nodeDao.getProjectNodes(projectId)).nodes

    suspend fun hasChildren(nodeId: String): Boolean = getNode(nodeId)?.hasChildren == true

    suspend fun getNodeDepth(nodeId: String): Int = getNodePath(nodeId).size

    suspend fun getNodePath(nodeId: String): List<Node> = database.withTransaction {
        val path = mutableListOf<Node>()
        val visited = mutableSetOf<String>()
        var currentId: String? = nodeId
        var projectId: String? = null
        while (currentId != null) {
            check(visited.add(currentId)) { "Cycle in node hierarchy" }
            val current = nodeDao.getById(currentId)
            if (current == null && path.isEmpty()) break
            checkNotNull(current) { "Missing ancestor" }
            if (projectId == null) projectId = current.projectId
            check(current.projectId == projectId) { "Invalid parent project" }
            path.add(current.toNode(nodeDao.hasChildren(current.projectId, current.id)))
            currentId = current.parentId
        }
        path.asReversed().toList()
    }

    private suspend fun validateParent(projectId: String, parentId: String?, movingId: String? = null) {
        val visited = mutableSetOf<String>()
        var currentId = parentId
        while (currentId != null) {
            require(currentId != movingId) { "A node cannot be its own ancestor" }
            check(visited.add(currentId)) { "Cycle in existing hierarchy" }
            val parent = nodeDao.getById(currentId)
            requireNotNull(parent) { "Parent node not found" }
            require(parent.amountMinor == null) { "Convierte esta obligación en una tarea antes de usarla como capa." }
            require(parent.purpose == NodePurpose.ACTION.name) { "Notes cannot receive children" }
            require(parent.projectId == projectId) { "Parent node must belong to the same project" }
            currentId = parent.parentId
        }
    }

    private suspend fun nextPosition(projectId: String, parentId: String?): Int {
        var maximum = nodeDao.maxPosition(projectId, parentId) ?: -1
        if (maximum == Int.MAX_VALUE) {
            writeOrder(nodeDao.getSiblings(projectId, parentId))
            maximum = nodeDao.maxPosition(projectId, parentId) ?: -1
        }
        check(maximum < Int.MAX_VALUE) { "Sibling position exhausted" }
        return maximum + 1
    }

    private fun snapshot(entities: List<NodeEntity>): NodeTreeSnapshot {
        val parents = entities.mapNotNull { it.parentId }.toHashSet()
        return NodeTreeSnapshot(entities.map { it.toNode(it.id in parents) })
    }

    private fun validateTitle(title: String): String = title.trim().also {
        require(it.isNotEmpty()) { "Node title must not be blank" }
        require(TitleLimits.count(it) <= TitleLimits.NODE) { "Node title exceeds 100 characters" }
    }
}
