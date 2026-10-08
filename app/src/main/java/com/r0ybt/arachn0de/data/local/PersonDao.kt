package com.r0ybt.arachn0de.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface PersonDao {
    @Query("SELECT * FROM node_person WHERE nodeId IN (:ids)") suspend fun assignmentsForNodes(ids: List<String>): List<NodePersonEntity>

    @Query("SELECT * FROM persons ORDER BY name COLLATE NOCASE, id")
    fun observePeople(): Flow<List<PersonEntity>>
    @Query("SELECT * FROM persons WHERE id = :id")
    suspend fun get(id: String): PersonEntity?
    @Insert suspend fun insert(person: PersonEntity)
    @Query("UPDATE persons SET name = :name, avatarFile = :avatarFile, avatarZoom = :zoom, avatarX = :x, avatarY = :y WHERE id = :id")
    suspend fun update(id: String, name: String, avatarFile: String?, zoom: Float = 1f, x: Float = 0f, y: Float = 0f): Int
    @Query("DELETE FROM persons WHERE id = :id") suspend fun delete(id: String): Int
    @Query("SELECT COUNT(*) FROM persons WHERE avatarFile = :file") suspend fun avatarReferences(file: String): Int
    @Query("SELECT COUNT(*) FROM nodes WHERE id = :id") suspend fun nodeExists(id: String): Int
    @Query("SELECT personId FROM node_person WHERE nodeId = :nodeId ORDER BY personId")
    suspend fun assignmentIds(nodeId: String): List<String>
    @Query("DELETE FROM node_person WHERE nodeId = :id") suspend fun clearAssignments(id: String)
    @Insert suspend fun assign(assignments: List<NodePersonEntity>)
    @Query("SELECT np.nodeId, p.id, p.name, p.avatarFile, p.avatarZoom, p.avatarX, p.avatarY FROM node_person np JOIN persons p ON p.id = np.personId ORDER BY p.name COLLATE NOCASE, p.id")
    fun observeAllAssignments(): Flow<List<AssignedPersonRow>>

    @Query("SELECT np.nodeId, p.id, p.name, p.avatarFile, p.avatarZoom, p.avatarX, p.avatarY FROM node_person np JOIN persons p ON p.id = np.personId JOIN nodes n ON n.id = np.nodeId WHERE n.projectId = :projectId ORDER BY p.name COLLATE NOCASE, p.id")
    fun observeAssignments(projectId: String): Flow<List<AssignedPersonRow>>
}
