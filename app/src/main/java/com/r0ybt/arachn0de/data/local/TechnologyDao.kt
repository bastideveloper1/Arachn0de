package com.r0ybt.arachn0de.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface TechnologyDao {
    @Query("SELECT * FROM technologies ORDER BY name COLLATE NOCASE, id") fun observeCatalog(): Flow<List<TechnologyEntity>>
    @Query("SELECT * FROM node_technologies") fun observeNodes(): Flow<List<NodeTechnologyEntity>>
    @Query("SELECT * FROM project_technologies") fun observeProjects(): Flow<List<ProjectTechnologyEntity>>
    @Query("SELECT * FROM technologies ORDER BY id") suspend fun catalog(): List<TechnologyEntity>
    @Query("SELECT * FROM node_technologies ORDER BY nodeId, technologyId") suspend fun nodes(): List<NodeTechnologyEntity>
    @Query("SELECT * FROM node_technologies WHERE nodeId IN (:ids)") suspend fun forNodes(ids: List<String>): List<NodeTechnologyEntity>
    @Query("SELECT * FROM project_technologies ORDER BY projectId, technologyId") suspend fun projects(): List<ProjectTechnologyEntity>
    @Query("SELECT * FROM technologies WHERE id = :id") suspend fun get(id: String): TechnologyEntity?
    @Insert suspend fun insert(row: TechnologyEntity)
    @Update suspend fun update(row: TechnologyEntity): Int
    @Query("DELETE FROM technologies WHERE id = :id") suspend fun delete(id: String): Int
    @Query("DELETE FROM technologies") suspend fun clear()
    @Query("DELETE FROM node_technologies WHERE nodeId = :id") suspend fun clearNode(id: String)
    @Query("DELETE FROM project_technologies WHERE projectId = :id") suspend fun clearProject(id: String)
    @Insert suspend fun assignNodes(rows: List<NodeTechnologyEntity>)
    @Insert suspend fun assignProjects(rows: List<ProjectTechnologyEntity>)
    @Query("SELECT COUNT(*) FROM technologies WHERE iconFile = :name") suspend fun iconReferences(name: String): Int
}
