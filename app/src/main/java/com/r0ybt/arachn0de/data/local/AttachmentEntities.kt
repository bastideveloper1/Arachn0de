package com.r0ybt.arachn0de.data.local

import androidx.room.*

@Entity(tableName = "attachment_files", indices = [Index(value = ["storageName"], unique = true)])
data class AttachmentFileEntity(
    @PrimaryKey val id: String, val storageName: String, val originalName: String,
    val mimeType: String, val byteSize: Long, val width: Int, val height: Int,
    val sha256: String, val createdAt: Long, val lifecycleState: String = "READY",
    @ColumnInfo(defaultValue = "0") val durationMs: Long = 0,
    @ColumnInfo(defaultValue = "0") val playbackMs: Long = 0,
    val bookmarkMs: Long? = null,
)

@Entity(tableName = "node_attachments", primaryKeys = ["nodeId", "attachmentId"], foreignKeys = [
    ForeignKey(entity = NodeEntity::class, parentColumns = ["id"], childColumns = ["nodeId"], onDelete = ForeignKey.CASCADE),
    ForeignKey(entity = AttachmentFileEntity::class, parentColumns = ["id"], childColumns = ["attachmentId"], onDelete = ForeignKey.RESTRICT),
], indices = [Index("attachmentId")])
data class NodeAttachmentEntity(val nodeId: String, val attachmentId: String)

@Entity(tableName = "project_attachments", primaryKeys = ["projectId", "attachmentId"], foreignKeys = [
    ForeignKey(entity = ProjectEntity::class, parentColumns = ["id"], childColumns = ["projectId"], onDelete = ForeignKey.CASCADE),
    ForeignKey(entity = AttachmentFileEntity::class, parentColumns = ["id"], childColumns = ["attachmentId"], onDelete = ForeignKey.RESTRICT),
], indices = [Index("attachmentId")])
data class ProjectAttachmentEntity(val projectId: String, val attachmentId: String)

/** Operational journal only: completed media and its user metadata live in attachment_files. */
@Entity(tableName="audio_recording_operations")
data class AudioRecordingOperationEntity(@PrimaryKey val id:String,val state:String,val nodeId:String?=null,val provisionalTitle:String?=null)
