package com.r0ybt.arachn0de.data.local

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
            parentColumns = ["id"],
            childColumns = ["projectId"],
            onDelete = ForeignKey.CASCADE,
            onUpdate = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = NodeEntity::class,
            parentColumns = ["id"],
            childColumns = ["parentId"],
            onDelete = ForeignKey.CASCADE,
            onUpdate = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["projectId"]), Index(value = ["parentId"])],
)
data class NodeEntity(
    @PrimaryKey val id: String,
    val projectId: String,
    val parentId: String?,
    val title: String,
    val description: String,
    val isStructural: Boolean,
    val isCompletable: Boolean,
    val isCompleted: Boolean,
    val position: Int,
    val createdAt: Long,
    val updatedAt: Long,
)

internal fun NodeEntity.toNode() = Node(
    id = id,
    projectId = projectId,
    parentId = parentId,
    title = title,
    description = description,
    isStructural = isStructural,
    isCompletable = isCompletable,
    isCompleted = isCompleted,
    position = position,
    createdAt = createdAt,
    updatedAt = updatedAt,
)
