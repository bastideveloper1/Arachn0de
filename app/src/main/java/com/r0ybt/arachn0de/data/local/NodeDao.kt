package com.r0ybt.arachn0de.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface NodeDao {
    @Query("SELECT * FROM nodes WHERE projectId = :projectId AND parentId IS NULL ORDER BY position ASC, createdAt ASC, id ASC")
    fun observeRootNodes(projectId: String): Flow<List<NodeEntity>>

    @Query("SELECT * FROM nodes WHERE projectId = :projectId AND parentId = :parentId ORDER BY position ASC, createdAt ASC, id ASC")
    fun observeChildren(projectId: String, parentId: String): Flow<List<NodeEntity>>

    @Query("SELECT * FROM nodes WHERE projectId = :projectId AND parentId IS :parentId ORDER BY position ASC, createdAt ASC, id ASC")
    suspend fun getChildren(projectId: String, parentId: String?): List<NodeEntity>

    @Query("SELECT * FROM nodes WHERE id = :id")
    suspend fun getById(id: String): NodeEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(node: NodeEntity)

    @Query(
        "UPDATE nodes SET title = :title, description = :description, parentId = :parentId, isStructural = :isStructural, isCompletable = :isCompletable, isCompleted = :isCompleted, position = :position, updatedAt = :updatedAt WHERE id = :id AND projectId = :projectId",
    )
    suspend fun update(
        projectId: String,
        id: String,
        title: String,
        description: String,
        parentId: String?,
        isStructural: Boolean,
        isCompletable: Boolean,
        isCompleted: Boolean,
        position: Int,
        updatedAt: Long,
    ): Int

    @Query("DELETE FROM nodes WHERE id = :id")
    suspend fun delete(id: String): Int
}
