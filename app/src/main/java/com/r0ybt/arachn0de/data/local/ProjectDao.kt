package com.r0ybt.arachn0de.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface ProjectDao {
    @Query("SELECT projects.*, p.id AS photo_id, p.projectId AS photo_projectId, p.nodeId AS photo_nodeId, p.file AS photo_file, p.photoZoom AS photo_photoZoom, p.photoX AS photo_photoX, p.photoY AS photo_photoY FROM projects LEFT JOIN project_photos p ON p.projectId = projects.id ORDER BY projects.position, projects.createdAt, projects.id")
    fun observeWithPhotos(): Flow<List<ProjectWithPhoto>>

    @Query("SELECT projects.*, p.id AS photo_id, p.projectId AS photo_projectId, p.nodeId AS photo_nodeId, p.file AS photo_file, p.photoZoom AS photo_photoZoom, p.photoX AS photo_photoX, p.photoY AS photo_photoY FROM projects LEFT JOIN project_photos p ON p.projectId = projects.id WHERE projects.id = :id")
    suspend fun getWithPhoto(id: String): ProjectWithPhoto?

    @Query("SELECT * FROM projects ORDER BY position ASC, createdAt ASC, id ASC")
    fun observeAll(): Flow<List<ProjectEntity>>

    @Query("SELECT * FROM projects ORDER BY position ASC, createdAt ASC, id ASC")
    suspend fun getAll(): List<ProjectEntity>

    @Query("SELECT * FROM projects WHERE id = :id")
    suspend fun getById(id: String): ProjectEntity?

    @Query("SELECT MAX(position) FROM projects")
    suspend fun maxPosition(): Int?

    @Query("UPDATE projects SET position = :position, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateOrder(id: String, position: Int, updatedAt: Long): Int

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(project: ProjectEntity)

    /** A retried creation can acknowledge a committed row, but never replace it. */
    @Transaction
    suspend fun insertOrGet(project: ProjectEntity): ProjectEntity {
        val existing = getById(project.id)
        if (existing != null) return existing
        insert(project)
        return project
    }

    // Only editable fields change; callers cannot overwrite identity or creation time.
    @Query("UPDATE projects SET name = :name, description = :description, updatedAt = :updatedAt WHERE id = :id")
    suspend fun update(id: String, name: String, description: String, updatedAt: Long): Int

    @Query("DELETE FROM projects WHERE id = :id")
    suspend fun deleteProjectRow(id: String): Int

    @Query("UPDATE nodes SET parentId = NULL WHERE projectId = :id AND parentId IS NOT NULL")
    suspend fun detachProjectNodes(id: String)

    /** Flatten first so the project cascade never recursively walks a deep tree. */
    @Transaction
    suspend fun delete(id: String): Int {
        detachProjectNodes(id)
        return deleteProjectRow(id)
    }
}
