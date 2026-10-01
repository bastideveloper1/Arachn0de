package com.r0ybt.arachn0de.data.repository

import com.r0ybt.arachn0de.data.local.NodeDao
import com.r0ybt.arachn0de.data.local.NodeEntity
import com.r0ybt.arachn0de.data.local.toNode
import com.r0ybt.arachn0de.domain.model.Node
import com.r0ybt.arachn0de.domain.model.NodeProgress
import com.r0ybt.arachn0de.domain.model.NodeProgressState
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class NodeRepository(
    private val nodeDao: NodeDao,
    private val currentTimeMillis: () -> Long = System::currentTimeMillis,
) {
    fun observeRootNodes(projectId: String): Flow<List<Node>> =
        nodeDao.observeRootNodes(projectId).map { it.map(NodeEntity::toNode) }

    fun observeChildren(projectId: String, parentId: String): Flow<List<Node>> =
        nodeDao.observeChildren(projectId, parentId).map { it.map(NodeEntity::toNode) }

    suspend fun getNode(id: String): Node? = nodeDao.getById(id)?.toNode()

    suspend fun createNode(
        projectId: String,
        parentId: String?,
        title: String,
        description: String = "",
        isStructural: Boolean = true,
        isCompletable: Boolean = false,
        isCompleted: Boolean = false,
    ): Node {
        val normalizedTitle = validateTitle(title)
        val flags = normalizeFlags(isStructural, isCompletable, isCompleted)
        if (parentId != null) {
            val parent = nodeDao.getById(parentId)
            require(parent != null) { "Parent node not found" }
            require(parent.projectId == projectId) { "Parent node must belong to the same project" }
        }
        val resolvedParentId = parentId
        val now = currentTimeMillis()
        val entity = NodeEntity(
            id = UUID.randomUUID().toString(),
            projectId = projectId,
            parentId = resolvedParentId,
            title = normalizedTitle,
            description = description,
            isStructural = flags.first,
            isCompletable = flags.second,
            isCompleted = flags.third,
            position = nodeDao.getChildren(projectId, resolvedParentId).size,
            createdAt = now,
            updatedAt = now,
        )
        nodeDao.insert(entity)
        return entity.toNode()
    }

    suspend fun updateNode(
        id: String,
        title: String,
        description: String = "",
        parentId: String? = null,
        isStructural: Boolean = true,
        isCompletable: Boolean = false,
        isCompleted: Boolean = false,
        position: Int = -1,
    ): Boolean {
        val current = nodeDao.getById(id) ?: return false
        val normalizedTitle = validateTitle(title)
        val flags = normalizeFlags(isStructural, isCompletable, isCompleted)
        val resolvedParentId = if (parentId != null) {
            val parent = nodeDao.getById(parentId) ?: return false
            require(parent.projectId == current.projectId) { "Parent node must belong to the same project" }
            parent.id
        } else {
            current.parentId
        }
        val resolvedPosition = if (position >= 0) position else current.position
        val updatedAt = currentTimeMillis()
        return nodeDao.update(
            projectId = current.projectId,
            id = id,
            title = normalizedTitle,
            description = description,
            parentId = resolvedParentId,
            isStructural = flags.first,
            isCompletable = flags.second,
            isCompleted = flags.third,
            position = resolvedPosition,
            updatedAt = updatedAt,
        ) == 1
    }

    suspend fun setCompleted(id: String, completed: Boolean): Boolean {
        val current = nodeDao.getById(id) ?: return false
        if (!current.isCompletable) return false
        if (hasChildren(current.id)) return false
        val updatedAt = currentTimeMillis()
        return nodeDao.update(
            projectId = current.projectId,
            id = id,
            title = current.title,
            description = current.description,
            parentId = current.parentId,
            isStructural = false,
            isCompletable = true,
            isCompleted = completed,
            position = current.position,
            updatedAt = updatedAt,
        ) == 1
    }

    suspend fun toggleCompleted(id: String): Boolean {
        val current = nodeDao.getById(id) ?: return false
        if (!current.isCompletable) return false
        if (hasChildren(current.id)) return false
        return setCompleted(id, !current.isCompleted)
    }

    suspend fun calculateProgress(nodeId: String): NodeProgress? {
        val current = nodeDao.getById(nodeId) ?: return null
        val allNodes = nodeDao.getProjectNodes(current.projectId)
        val descendants = collectDescendantIds(nodeId, allNodes)
        val relevantNodes = allNodes.filter { it.id == nodeId || descendants.contains(it.id) }
        val completableNodes = relevantNodes
            .filter { it.isCompletable }
            .filterNot { hasChildren(it.id, allNodes) }
        val total = completableNodes.size
        if (total == 0) {
            return NodeProgress(
                nodeId = nodeId,
                completed = 0,
                total = 0,
                percentage = 0,
                state = NodeProgressState.NO_WORK,
            )
        }
        val completed = completableNodes.count { it.isCompleted }
        val percentage = (completed * 100) / total
        val state = when {
            completed == 0 -> NodeProgressState.NOT_STARTED
            completed == total -> NodeProgressState.COMPLETE
            else -> NodeProgressState.PARTIAL
        }
        return NodeProgress(
            nodeId = nodeId,
            completed = completed,
            total = total,
            percentage = percentage,
            state = state,
        )
    }

    suspend fun deleteNode(id: String): Boolean = nodeDao.delete(id) == 1

    suspend fun getProjectNodes(projectId: String): List<Node> =
        nodeDao.getProjectNodes(projectId).map(NodeEntity::toNode)

    suspend fun hasChildren(nodeId: String): Boolean {
        val current = nodeDao.getById(nodeId) ?: return false
        return nodeDao.getChildren(current.projectId, nodeId).isNotEmpty()
    }

    suspend fun getNodeDepth(nodeId: String): Int {
        val start = nodeDao.getById(nodeId) ?: return 0
        var depth = 1
        var current: NodeEntity? = start
        while (current?.parentId != null) {
            current = nodeDao.getById(current.parentId)
            if (current != null) depth += 1
        }
        return depth
    }

    suspend fun getNodePath(nodeId: String): List<Node> {
        val path = mutableListOf<Node>()
        var currentId: String? = nodeId
        while (currentId != null) {
            val current = nodeDao.getById(currentId) ?: break
            path.add(0, current.toNode())
            currentId = current.parentId
        }
        return path
    }

    private fun collectDescendantIds(rootId: String, nodes: List<NodeEntity>): Set<String> {
        val childrenByParent = nodes.groupBy { it.parentId }
        val results = mutableSetOf<String>()
        fun walk(currentId: String) {
            childrenByParent[currentId].orEmpty().forEach { child ->
                results += child.id
                walk(child.id)
            }
        }
        walk(rootId)
        return results
    }

    private fun hasChildren(nodeId: String, allNodes: List<NodeEntity>): Boolean =
        allNodes.any { it.parentId == nodeId }

    private fun normalizeFlags(
        isStructural: Boolean,
        isCompletable: Boolean,
        isCompleted: Boolean,
    ): Triple<Boolean, Boolean, Boolean> {
        require(!(isStructural && isCompletable)) { "Node must be structural or completable, not both" }
        require(isStructural || isCompletable) { "Node must be either structural or completable" }
        return Triple(
            first = isStructural,
            second = isCompletable,
            third = if (isCompletable) isCompleted else false,
        )
    }

    private fun validateTitle(title: String): String {
        val normalizedTitle = title.trim()
        require(normalizedTitle.isNotEmpty()) { "Node title must not be blank" }
        return normalizedTitle
    }
}
