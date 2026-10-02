package com.r0ybt.arachn0de.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.r0ybt.arachn0de.domain.model.Project

@Entity(tableName = "projects")
data class ProjectEntity(
    @PrimaryKey val id: String,
    val name: String,
    val description: String,
    val position: Int,
    val createdAt: Long,
    val updatedAt: Long,
) {
    constructor(id: String, name: String, description: String, createdAt: Long, updatedAt: Long) :
        this(id, name, description, 0, createdAt, updatedAt)
}

internal fun ProjectEntity.toProject() = Project(id, name, description, position, createdAt, updatedAt)
