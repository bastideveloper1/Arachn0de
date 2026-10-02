package com.r0ybt.arachn0de.data.repository

import androidx.room.withTransaction
import com.r0ybt.arachn0de.data.local.Arachn0deDatabase
import com.r0ybt.arachn0de.data.local.NodeEntity
import com.r0ybt.arachn0de.data.local.toNode
import com.r0ybt.arachn0de.domain.model.Node
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
    private val nodeDao = database.nodeDao()

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
    ): Node = database.withTransaction {
        val normalizedTitle = validateTitle(title)
        require(creationId.isNotBlank())
        nodeDao.getById(creationId)?.let { existing ->
            check(existing.projectId == projectId && existing.parentId == parentId &&
                existing.title == normalizedTitle && existing.description == description) {
                "Creation already committed with different content or destination"
            }
            return@withTransaction existing.toNode(nodeDao.hasChildren(projectId, creationId))
        }
        require(database.projectDao().getById(projectId) != null) { "Project not found" }
        validateParent(projectId, parentId)
        val now = currentTimeMillis()
        val entity = NodeEntity(
            id = creationId, projectId = projectId, parentId = parentId,
            title = normalizedTitle, description = description, isCompleted = false,
            position = nextPosition(projectId, parentId), createdAt = now, updatedAt = now,
        )
        nodeDao.insert(entity)
        entity.toNode(hasChildren = false)
    }

    /** Content edits never change structure, completion, identity or sibling order. */
    suspend fun updateNode(id: String, title: String, description: String = ""): Boolean =
        database.withTransaction {
            nodeDao.updateContent(id, validateTitle(title), description, currentTimeMillis()) == 1
        }

    /** A null parent explicitly moves the node to the project root. */
    suspend fun moveNode(id: String, parentId: String?): Boolean = database.withTransaction {
        val current = nodeDao.getById(id) ?: return@withTransaction false
        validateParent(current.projectId, parentId, movingId = id)
        if (current.parentId == parentId) return@withTransaction true
        nodeDao.move(id, parentId, nextPosition(current.projectId, parentId), currentTimeMillis()) == 1
    }

    suspend fun setCompleted(id: String, completed: Boolean): Boolean = database.withTransaction {
        nodeDao.setCompleted(id, completed, currentTimeMillis()) == 1
    }

    suspend fun toggleCompleted(id: String): Boolean = database.withTransaction {
        val current = nodeDao.getById(id) ?: return@withTransaction false
        nodeDao.setCompleted(id, !current.isCompleted, currentTimeMillis()) == 1
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
