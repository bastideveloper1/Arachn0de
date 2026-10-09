package com.r0ybt.arachn0de.data.local

import androidx.room.*

@Dao
interface ProjectPhotoDao {
    @Query("SELECT * FROM project_photos") suspend fun all(): List<ProjectPhotoEntity>
    @Query("SELECT * FROM project_photos WHERE projectId IS NOT NULL OR nodeId IS NOT NULL") suspend fun confirmed(): List<ProjectPhotoEntity>
    @Query("SELECT * FROM project_photos WHERE projectId = :projectId") suspend fun forProject(projectId: String): ProjectPhotoEntity?
    @Query("SELECT * FROM project_photos WHERE nodeId IN (:ids)") suspend fun forNodes(ids: List<String>): List<ProjectPhotoEntity>
    @Query("SELECT COUNT(*) FROM project_photos WHERE file = :name AND (projectId IS NOT NULL OR nodeId IS NOT NULL)") suspend fun references(name: String): Int
    @Upsert suspend fun save(photo: ProjectPhotoEntity)
    @Query("DELETE FROM project_photos WHERE id = :id") suspend fun delete(id: String)
    @Query("DELETE FROM project_photos") suspend fun clear()
}
