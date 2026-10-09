package com.r0ybt.arachn0de.data.local

import androidx.room.*

@Entity(tableName = "technologies")
data class TechnologyEntity(@PrimaryKey val id: String, val name: String, val iconFile: String?)

@Entity(tableName = "node_technologies", primaryKeys = ["nodeId", "technologyId"], foreignKeys = [
    ForeignKey(entity = NodeEntity::class, parentColumns = ["id"], childColumns = ["nodeId"], onDelete = ForeignKey.CASCADE),
    ForeignKey(entity = TechnologyEntity::class, parentColumns = ["id"], childColumns = ["technologyId"], onDelete = ForeignKey.CASCADE),
], indices = [Index("technologyId")])
data class NodeTechnologyEntity(val nodeId: String, val technologyId: String, @ColumnInfo(defaultValue = "0") val position: Int = 0)

@Entity(tableName = "project_technologies", primaryKeys = ["projectId", "technologyId"], foreignKeys = [
    ForeignKey(entity = ProjectEntity::class, parentColumns = ["id"], childColumns = ["projectId"], onDelete = ForeignKey.CASCADE),
    ForeignKey(entity = TechnologyEntity::class, parentColumns = ["id"], childColumns = ["technologyId"], onDelete = ForeignKey.CASCADE),
], indices = [Index("technologyId")])
data class ProjectTechnologyEntity(val projectId: String, val technologyId: String, @ColumnInfo(defaultValue = "0") val position: Int = 0)
