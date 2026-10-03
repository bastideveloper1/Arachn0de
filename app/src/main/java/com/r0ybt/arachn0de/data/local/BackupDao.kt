package com.r0ybt.arachn0de.data.local

import androidx.room.Dao
import androidx.room.Query

/** Raw persisted fields: backup must neither normalize positions nor derive completion. */
@Dao
internal interface BackupDao {
    @Query("SELECT * FROM projects ORDER BY id") suspend fun projects(): List<ProjectEntity>
    @Query("SELECT * FROM nodes ORDER BY id") suspend fun nodes(): List<NodeEntity>
    @Query("SELECT * FROM persons ORDER BY id") suspend fun persons(): List<PersonEntity>
    @Query("SELECT * FROM node_person ORDER BY nodeId, personId") suspend fun assignments(): List<NodePersonEntity>
    @Query("DELETE FROM node_person") suspend fun deleteAssignments()
    @Query("UPDATE nodes SET parentId = NULL WHERE parentId IS NOT NULL") suspend fun detachNodes()
    @Query("DELETE FROM nodes") suspend fun deleteNodes()
    @Query("DELETE FROM projects") suspend fun deleteProjects()
    @Query("DELETE FROM persons") suspend fun deletePersons()
}
