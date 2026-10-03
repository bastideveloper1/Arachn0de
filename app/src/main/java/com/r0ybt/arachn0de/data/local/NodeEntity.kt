package com.r0ybt.arachn0de.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.r0ybt.arachn0de.domain.model.Node

@Entity(
    tableName = "nodes",
    foreignKeys = [
        ForeignKey(
            entity = ProjectEntity::class,
            parentColumns = ["id"], childColumns = ["projectId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = NodeEntity::class,
            parentColumns = ["projectId", "id"], childColumns = ["projectId", "parentId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["projectId", "id"], unique = true),
        Index(value = ["projectId", "parentId"]),
    ],
)
data class NodeEntity(
    @PrimaryKey val id: String,
    val projectId: String,
    val parentId: String?,
    val title: String,
    val description: String,
    val isCompleted: Boolean,
    val position: Int,
    val createdAt: Long,
    val updatedAt: Long,
    val startAt: Long? = null,
    val dueAt: Long? = null,
    @ColumnInfo(defaultValue = "'ACTION'") val purpose: String = "ACTION",
)

internal fun NodeEntity.toNode(hasChildren: Boolean) = Node(
    id, projectId, parentId, title, description, isCompleted,
    position, createdAt, updatedAt, hasChildren, startAt, dueAt, com.r0ybt.arachn0de.domain.model.NodePurpose.valueOf(purpose),
)
