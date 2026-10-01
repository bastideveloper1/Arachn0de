package com.r0ybt.arachn0de.data.repository

import com.r0ybt.arachn0de.data.local.NodeDao
import com.r0ybt.arachn0de.data.local.NodeEntity
import com.r0ybt.arachn0de.data.local.toNode
import com.r0ybt.arachn0de.domain.model.Node
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

    suspend fun deleteNode(id: String): Boolean = nodeDao.delete(id) == 1

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
