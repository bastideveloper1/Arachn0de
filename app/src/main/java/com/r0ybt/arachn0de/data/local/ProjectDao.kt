package com.r0ybt.arachn0de.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ProjectDao {
    @Query("SELECT * FROM projects ORDER BY createdAt ASC, id ASC")
    fun observeAll(): Flow<List<ProjectEntity>>

    @Query("SELECT * FROM projects WHERE id = :id")
    suspend fun getById(id: String): ProjectEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(project: ProjectEntity)

    // Only editable fields change; callers cannot overwrite identity or creation time.
    @Query("UPDATE projects SET name = :name, description = :description, updatedAt = :updatedAt WHERE id = :id")
    suspend fun update(id: String, name: String, description: String, updatedAt: Long): Int

    @Query("DELETE FROM projects WHERE id = :id")
    suspend fun delete(id: String): Int
}
