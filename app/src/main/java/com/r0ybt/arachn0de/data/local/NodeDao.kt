package com.r0ybt.arachn0de.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface NodeDao {
    @Query("SELECT * FROM nodes ORDER BY projectId, position, createdAt, id")
    fun observeAllNodes(): Flow<List<NodeEntity>>

    @Query("SELECT * FROM nodes WHERE projectId = :projectId ORDER BY position, createdAt, id")
    fun observeProjectNodes(projectId: String): Flow<List<NodeEntity>>

    @Query("SELECT * FROM nodes WHERE projectId = :projectId ORDER BY position, createdAt, id")
    suspend fun getProjectNodes(projectId: String): List<NodeEntity>

    @Query("SELECT * FROM nodes WHERE id = :id")
    suspend fun getById(id: String): NodeEntity?

    @Query("SELECT EXISTS(SELECT 1 FROM nodes WHERE projectId = :projectId AND parentId = :id)")
    suspend fun hasChildren(projectId: String, id: String): Boolean

    @Query("SELECT MAX(position) FROM nodes WHERE projectId = :projectId AND parentId IS :parentId")
    suspend fun maxPosition(projectId: String, parentId: String?): Int?

    @Query("SELECT * FROM nodes WHERE projectId = :projectId AND parentId IS :parentId ORDER BY position, createdAt, id")
    suspend fun getSiblings(projectId: String, parentId: String?): List<NodeEntity>

    @Query("UPDATE nodes SET position = :position, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateOrder(id: String, position: Int, updatedAt: Long): Int

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(node: NodeEntity)

    @Query("UPDATE nodes SET priority = :priority, updatedAt = :at WHERE id = :id")
    suspend fun updatePriority(id: String, priority: String, at: Long): Int

    @Query("UPDATE nodes SET title = :title, description = :description, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateContent(id: String, title: String, description: String, updatedAt: Long): Int

    @Query("UPDATE nodes SET title = :title, description = :description, startAt = :startAt, dueAt = :dueAt, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateContentAndDates(id: String, title: String, description: String, startAt: Long?, dueAt: Long?, updatedAt: Long): Int

    @Query("UPDATE nodes SET purpose = :purpose, isCompleted = 0, amountMinor = NULL, currencyCode = NULL, updatedAt = :updatedAt WHERE id = :id AND NOT EXISTS(SELECT 1 FROM nodes AS child WHERE child.parentId = nodes.id)")
    suspend fun updatePurpose(id: String, purpose: String, updatedAt: Long): Int

    @Query("UPDATE nodes SET title = :title, description = :description, startAt = :startAt, dueAt = :dueAt, amountMinor = :amountMinor, currencyCode = :currencyCode, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateLeaf(id: String, title: String, description: String, startAt: Long?, dueAt: Long?, amountMinor: Long?, currencyCode: String?, updatedAt: Long): Int

    @Query("UPDATE nodes SET parentId = :parentId, position = :position, updatedAt = :updatedAt WHERE id = :id")
    suspend fun move(id: String, parentId: String?, position: Int, updatedAt: Long): Int

    @Query("""
        UPDATE nodes SET isCompleted = :completed, updatedAt = :updatedAt
        WHERE id = :id AND purpose = 'ACTION' AND NOT EXISTS(
            SELECT 1 FROM nodes AS child WHERE child.projectId = nodes.projectId AND child.parentId = nodes.id
        )
    """)
    suspend fun setCompleted(id: String, completed: Boolean, updatedAt: Long): Int

    @Query("""
        WITH RECURSIVE subtree(id) AS (
            SELECT id FROM nodes WHERE id = :id
            UNION
            SELECT child.id FROM nodes AS child JOIN subtree ON child.parentId = subtree.id
        )
        SELECT id FROM subtree
    """)
    suspend fun getSubtreeIds(id: String): List<String>

    @Query("UPDATE nodes SET parentId = NULL WHERE id IN (:ids) AND parentId IS NOT NULL")
    suspend fun detachNodes(ids: List<String>)

    @Query("DELETE FROM nodes WHERE id IN (:ids)")
    suspend fun deleteDetachedNodes(ids: List<String>)

    /** Capture membership before detaching; observers only see the committed deletion. */
    @Transaction
    suspend fun delete(id: String): Int {
        val ids = getSubtreeIds(id)
        if (ids.isEmpty()) return 0
        // Stay below the SQLite bind limit on API 24. Detach every batch before deleting any.
        val batches = ids.chunked(500)
        batches.forEach { detachNodes(it) }
        batches.forEach { deleteDetachedNodes(it) }
        return 1
    }
}
