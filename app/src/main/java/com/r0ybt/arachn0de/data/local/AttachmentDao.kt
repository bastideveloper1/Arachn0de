package com.r0ybt.arachn0de.data.local

import androidx.room.*

@Dao
interface AttachmentDao {
    @Insert suspend fun insert(file: AttachmentFileEntity)
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun attachNode(rows: List<NodeAttachmentEntity>)
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun attachProject(rows: List<ProjectAttachmentEntity>)
    @Query("SELECT * FROM attachment_files WHERE id = :id") suspend fun get(id: String): AttachmentFileEntity?
    @Query("SELECT * FROM attachment_files") fun observeFiles(): kotlinx.coroutines.flow.Flow<List<AttachmentFileEntity>>
    @Query("SELECT * FROM attachment_files") suspend fun files(): List<AttachmentFileEntity>
    @Query("SELECT * FROM node_attachments WHERE nodeId = :id") suspend fun forNode(id: String): List<NodeAttachmentEntity>
    @Query("SELECT * FROM project_attachments WHERE projectId = :id") suspend fun forProject(id: String): List<ProjectAttachmentEntity>
    @Query("SELECT (SELECT COUNT(*) FROM node_attachments WHERE attachmentId = :id) + (SELECT COUNT(*) FROM project_attachments WHERE attachmentId = :id)") suspend fun references(id: String): Int
    @Query("DELETE FROM node_attachments WHERE nodeId = :owner AND attachmentId = :id") suspend fun detachNode(owner: String, id: String)
    @Query("DELETE FROM project_attachments WHERE projectId = :owner AND attachmentId = :id") suspend fun detachProject(owner: String, id: String)
    @Query("UPDATE attachment_files SET lifecycleState = 'DELETE_PENDING' WHERE id = :id AND lifecycleState = 'READY'") suspend fun pending(id: String)
    @Query("DELETE FROM attachment_files WHERE id = :id") suspend fun delete(id: String)
    @Query("SELECT * FROM node_attachments") suspend fun nodeAttachments(): List<NodeAttachmentEntity>
    @Query("SELECT * FROM project_attachments") suspend fun projectAttachments(): List<ProjectAttachmentEntity>
    @Query("DELETE FROM node_attachments") suspend fun clearNodes()
    @Query("DELETE FROM project_attachments") suspend fun clearProjects()
    @Query("DELETE FROM attachment_files") suspend fun clearFiles()
    @Query("SELECT COUNT(*) FROM attachment_files WHERE storageName = :name") suspend fun storageReferences(name: String): Int
    @Query("SELECT EXISTS(SELECT 1 FROM attachment_files)") suspend fun hasFiles(): Boolean
}
