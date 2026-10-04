package com.r0ybt.arachn0de.data.repository

import androidx.room.withTransaction
import com.r0ybt.arachn0de.data.local.Arachn0deDatabase
import com.r0ybt.arachn0de.data.local.NodeTagEntity
import com.r0ybt.arachn0de.data.local.NodePersonEntity
import com.r0ybt.arachn0de.data.local.NodeEventEntity
import com.r0ybt.arachn0de.data.local.toEvent
import com.r0ybt.arachn0de.domain.model.WorkState
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
    val creationDefaults = CreationDefaultsRepository(database)
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
        val sameGroup = siblings.filter { it.isCompleted == current.isCompleted && it.workState == current.workState }
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
            current.projectId != target.projectId || current.isCompleted != target.isCompleted || current.workState != target.workState) return@withTransaction false
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
            workState = initialWorkState(parentId, purpose),
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
        require(batchId.isNotBlank() && batchId.length <= 200)
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
                        node.amountMinor == spec.obligation?.amountMinor && node.currencyCode == spec.obligation?.currencyCode && node.priority == spec.priority.name && node.creationGroupId == batchId &&
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
                        amountMinor = spec.obligation?.amountMinor, currencyCode = spec.obligation?.currencyCode, priority = spec.priority.name, creationGroupId = batchId, workState = initialWorkState(parentId, spec.purpose))
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
        obligation: Obligation?, removeObligation: Boolean, editDates: Boolean, tagIds: Set<String>, priority: Priority? = null, responsibleIds: Set<String>? = null): Boolean = database.withTransaction {
        tags.validate(tagIds)
        val updated = if (editDates) updateLeaf(id, title, description, startAt, dueAt, obligation, removeObligation)
            else updateNode(id, title, description)
        if (updated) {
            priority?.let { check(setPriority(id, it)) { "Este elemento ya no admite prioridad." } }
            tags.assignNode(id, tagIds)
            responsibleIds?.let { ids ->
                ids.forEach { requireNotNull(database.personDao().get(it)) { "Person not found" } }
                database.personDao().clearAssignments(id)
                if (ids.isNotEmpty()) database.personDao().assign(ids.map { NodePersonEntity(id, it) })
            }
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
        check(nodeDao.updatePurpose(id, purpose.name, at, initialWorkState(current.parentId, purpose)) == 1) { "Purpose change was not written" }
        true
    }

    /** A null parent explicitly moves the node to the project root. */
    suspend fun moveNode(id: String, parentId: String?): Boolean = database.withTransaction {
        val current = nodeDao.getById(id) ?: return@withTransaction false
        validateParent(current.projectId, parentId, movingId = id)
        if (current.parentId == parentId) return@withTransaction true
        val at = currentTimeMillis()
        reopenBeforeConversion(parentId, at)
        check(nodeDao.move(id, parentId, nextPosition(current.projectId, parentId), at, movedWorkState(current, parentId)) == 1) { "Move was not written" }
        true
    }

    private suspend fun initialWorkState(parentId: String?, purpose: NodePurpose): String? =
        if (purpose == NodePurpose.ACTION && parentId?.let { nodeDao.getById(it)?.sprintMode } == true) WorkState.UNPLANNED.name else null

    private suspend fun movedWorkState(current: NodeEntity, parentId: String?): String? =
        if (current.purpose == "ACTION" && parentId?.let { nodeDao.getById(it)?.sprintMode } == true)
            current.workState ?: WorkState.fromCompletion(current.isCompleted).name else null

    suspend fun setSprintMode(id: String, enabled: Boolean): Boolean = database.withTransaction {
        val layer = nodeDao.getById(id) ?: return@withTransaction false
        if (layer.purpose != "LAYER") return@withTransaction false
        if (layer.sprintMode == enabled) return@withTransaction true
        val at = currentTimeMillis()
        nodeDao.getSiblings(layer.projectId, id).filter { it.purpose == "ACTION" }.forEach { child ->
            val state = if (enabled) WorkState.fromCompletion(child.isCompleted).name else null
            check(nodeDao.setWorkState(child.id, state, child.isCompleted, at) == 1)
        }
        check(nodeDao.setSprintMode(id, enabled, at) == 1)
        true
    }

    suspend fun setWorkState(id: String, state: WorkState): Boolean = database.withTransaction {
        val current = nodeDao.getById(id) ?: return@withTransaction false
        changeWorkState(current, state)
    }

    suspend fun advanceWorkState(id: String): Boolean = database.withTransaction {
        val current = nodeDao.getById(id) ?: return@withTransaction false
        val state = current.workState?.let(WorkState::valueOf) ?: return@withTransaction false
        changeWorkState(current, state.next())
    }

    private suspend fun changeWorkState(current: NodeEntity, state: WorkState): Boolean {
        if (current.purpose != "ACTION" || current.parentId?.let { nodeDao.getById(it)?.sprintMode } != true) return false
        if (current.workState == state.name) return true
        val at = currentTimeMillis()
        check(nodeDao.setWorkState(current.id, state.name, state.completed, at) == 1)
        if (current.isCompleted != state.completed)
            appendEvent(current.id, if (state.completed) NodeEventType.COMPLETED else NodeEventType.REOPENED, at)
        return true
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
        val state = if (current.workState != null) WorkState.fromCompletion(completed).name else null
        if (nodeDao.setWorkState(current.id, state, completed, updatedAt) != 1) return false
        appendEvent(current.id, if (completed) NodeEventType.COMPLETED else NodeEventType.REOPENED, at)
        return true
    }

    suspend fun createNodeWithUndo(projectId: String, parentId: String?, title: String, description: String, creationId: String,
        startAt: Long?, dueAt: Long?, purpose: NodePurpose, obligation: Obligation?, responsibleIds: Set<String>, tagIds: Set<String>, priority: Priority) = database.withTransaction {
        val node = createNode(projectId,parentId,title,description,creationId,startAt,dueAt,purpose,obligation,responsibleIds,tagIds,priority)
        captureCreation(listOf(node.id))
    }
    suspend fun createBatchWithUndo(projectId: String, parentId: String?, batchId: String, specs: List<GeneratedNodeSpec>, responsibleIds: Set<String>, tagIds: Set<String>) = database.withTransaction {
        captureCreation(createBatch(projectId,parentId,batchId,specs,responsibleIds,tagIds).map { it.id })
    }

    suspend fun groupMembers(groupId: String): List<Node> = database.withTransaction {
        val members = nodeDao.groupMembers(groupId)
        val parents = members.map { it.projectId }.distinct().flatMap { nodeDao.getProjectNodes(it) }.mapNotNull { it.parentId }.toSet()
        members.map { it.toNode(it.id in parents) }
    }

    suspend fun captureCreation(ids: List<String>): com.r0ybt.arachn0de.domain.model.CreationUndo = database.withTransaction {
        require(ids.isNotEmpty() && ids.size == ids.toSet().size)
        val rows = ids.chunked(500).flatMap { nodeDao.byIds(it) }.associateBy { it.id }
        require(rows.size == ids.size) { "La creación cambió." }
        val parents = ids.chunked(500).flatMap { nodeDao.parentsWithChildren(it) }.toSet()
        val tags = ids.chunked(500).flatMap { database.tagDao().tagsForNodes(it) }.groupBy { it.nodeId }
        val people = ids.chunked(500).flatMap { database.personDao().assignmentsForNodes(it) }.groupBy { it.nodeId }
        val events = ids.chunked(500).flatMap { database.nodeEventDao().eventsForNodes(it) }.groupBy { it.nodeId }
        com.r0ybt.arachn0de.domain.model.CreationUndo(ids.map { rows.getValue(it).toNode(it in parents) },
            ids.associateWith { tags[it].orEmpty().map { row -> row.tagId }.toSet() },
            ids.associateWith { people[it].orEmpty().map { row -> row.personId }.toSet() },
            ids.associateWith { events[it].orEmpty().map { row -> row.toEvent() } })
    }

    suspend fun deleteSelected(projectId: String, selected: Set<String>): Int = database.withTransaction {
        val nodes = nodeDao.getProjectNodes(projectId).map { it.toNode(false) }
        val roots = com.r0ybt.arachn0de.domain.model.SelectionRoots.normalize(nodes, selected)
        val children = nodes.groupBy { it.parentId }
        val stack = ArrayDeque<String>(); stack.addAll(roots)
        val affected = mutableListOf<String>()
        while (stack.isNotEmpty()) {
            val id = stack.removeLast(); affected.add(id)
            children[id].orEmpty().forEach { stack.addLast(it.id) }
        }
        val batches = affected.chunked(500)
        batches.forEach { nodeDao.detachNodes(it) }
        batches.forEach { nodeDao.deleteDetachedNodes(it) }
        selected.size
    }

    suspend fun moveSelected(projectId: String, selected: Set<String>, parentId: String?): Int = database.withTransaction {
        val nodes = nodeDao.getProjectNodes(projectId).map { it.toNode(false) }
        val roots = com.r0ybt.arachn0de.domain.model.SelectionRoots.normalize(nodes, selected)
        val byId = nodes.associateBy { it.id }
        val rootsSet = roots.toSet()
        val visited = hashSetOf<String>()
        var ancestor = parentId
        while (ancestor != null) {
            require(ancestor !in rootsSet && visited.add(ancestor)) { "Destino dentro de la selección." }
            val parent = requireNotNull(byId[ancestor]) { "Destino ausente." }
            require(parent.purpose == NodePurpose.LAYER && parent.obligation == null) { "Destino incompatible." }
            ancestor = parent.parentId
        }
        val changing = roots.filter { byId.getValue(it).parentId != parentId }
        if (changing.isNotEmpty()) {
            val at = currentTimeMillis()
            reopenBeforeConversion(parentId, at)
            var maximum = nodes.filter { it.parentId == parentId }.maxOfOrNull { it.position } ?: -1
            if (maximum.toLong() + changing.size > Int.MAX_VALUE) {
                writeOrder(nodeDao.getSiblings(projectId, parentId))
                maximum = nodeDao.maxPosition(projectId, parentId) ?: -1
            }
            check(maximum.toLong() + changing.size <= Int.MAX_VALUE) { "Orden agotado." }
            changing.forEachIndexed { index, id -> check(nodeDao.move(id,parentId,maximum+index+1,at,movedWorkState(checkNotNull(nodeDao.getById(id)),parentId)) == 1) }
        }
        selected.size
    }

    /** Refuse undo if any captured row changed, vanished or acquired children. Never delete new work. */
    suspend fun undoCreation(token: com.r0ybt.arachn0de.domain.model.CreationUndo): Boolean = database.withTransaction {
        val expected = token.nodes
        if (expected.isEmpty()) return@withTransaction false
        val actual = runCatching { captureCreation(expected.map { it.id }) }.getOrNull() ?: return@withTransaction false
        if (expected.any { it.hasChildren } || actual != token) return@withTransaction false
        // Every captured node is still a leaf; direct chunks cannot cascade into new work.
        expected.map { it.id }.chunked(500).forEach { nodeDao.deleteDetachedNodes(it) }
        true
    }

    suspend fun updateGroup(sourceId: String, patch: com.r0ybt.arachn0de.domain.model.SharedNodePatch,
        expectedIds: Set<String>, title: String, description: String, startAt: Long?, dueAt: Long?,
        editDates: Boolean, obligation: Obligation?, removeObligation: Boolean, tagIds: Set<String>, priority: Priority,
        responsibleIds: Set<String>): Int = database.withTransaction {
        val source = requireNotNull(nodeDao.getById(sourceId)) { "El elemento ya no existe." }
        val group = requireNotNull(source.creationGroupId) { "No pertenece a un grupo." }
        val members = nodeDao.groupMembers(group)
        check(members.map { it.id }.toSet() == expectedIds) { "El grupo cambió. Vuelve a revisar sus miembros." }
        require(!patch.isEmpty)
        require(members.all { it.projectId == source.projectId })
        val parents = nodeDao.getProjectNodes(source.projectId).mapNotNull { it.parentId }.toSet()
        patch.tags?.let { tags.validate(it) }
        patch.responsibleIds?.forEach { requireNotNull(database.personDao().get(it)) { "Persona ausente." } }
        val at = currentTimeMillis()
        members.forEach { current ->
            val actualAmount = if (patch.amount != null) patch.amount.value else current.amountMinor
            val currency = if (patch.currency != null) patch.currency.value else current.currencyCode
            val financial = actualAmount?.let { Obligation(it, requireNotNull(currency)) }
            require((actualAmount == null) == (currency == null)) { "Monto y moneda deben ser consistentes." }
            val leaf = current.purpose == "ACTION" && current.id !in parents
            require((patch.amount == null && patch.currency == null && patch.priority == null) || leaf) { "El grupo contiene notas o capas incompatibles con estos cambios." }
            patch.tags?.let { ids ->
                database.tagDao().clearNode(current.id)
                if(ids.isNotEmpty()) database.tagDao().assignNodes(ids.map { NodeTagEntity(current.id,it) })
            }
            patch.responsibleIds?.let { ids ->
                database.personDao().clearAssignments(current.id)
                if(ids.isNotEmpty()) database.personDao().assign(ids.map { NodePersonEntity(current.id,it) })
            }
            check(nodeDao.patchShared(current.id, patch.description ?: current.description, financial?.amountMinor,
                financial?.currencyCode, patch.priority?.name ?: current.priority, at) == 1)
        }
        check(updateEditor(sourceId,title,description,startAt,dueAt,obligation,removeObligation,editDates,tagIds,priority.takeIf { editDates },responsibleIds))
        members.size
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
            require(parent.purpose == NodePurpose.LAYER.name) { "Only layers can receive children" }
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
