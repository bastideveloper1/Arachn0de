package com.r0ybt.arachn0de.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface NodeDao {
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

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(node: NodeEntity)

    @Query("UPDATE nodes SET title = :title, description = :description, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateContent(id: String, title: String, description: String, updatedAt: Long): Int

    @Query("UPDATE nodes SET parentId = :parentId, position = :position, updatedAt = :updatedAt WHERE id = :id")
    suspend fun move(id: String, parentId: String?, position: Int, updatedAt: Long): Int

    @Query("""
        UPDATE nodes SET isCompleted = :completed, updatedAt = :updatedAt
        WHERE id = :id AND NOT EXISTS(
            SELECT 1 FROM nodes AS child WHERE child.projectId = nodes.projectId AND child.parentId = nodes.id
        )
    """)
    suspend fun setCompleted(id: String, completed: Boolean, updatedAt: Long): Int

    @Query("DELETE FROM nodes WHERE id = :id")
    suspend fun delete(id: String): Int
}
