package com.r0ybt.arachn0de.data.local

import androidx.room.*

@Entity(tableName = "persons")
data class PersonEntity(@PrimaryKey val id: String, val name: String, val avatarFile: String?, @ColumnInfo(defaultValue = "1") val avatarZoom: Float = 1f, @ColumnInfo(defaultValue = "0") val avatarX: Float = 0f, @ColumnInfo(defaultValue = "0") val avatarY: Float = 0f)

@Entity(
    tableName = "node_person",
    primaryKeys = ["nodeId", "personId"],
    foreignKeys = [
        ForeignKey(entity = NodeEntity::class, parentColumns = ["id"], childColumns = ["nodeId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = PersonEntity::class, parentColumns = ["id"], childColumns = ["personId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("personId")],
)
data class NodePersonEntity(val nodeId: String, val personId: String)

data class AssignedPersonRow(val nodeId: String, val id: String, val name: String, val avatarFile: String?, val avatarZoom: Float = 1f, val avatarX: Float = 0f, val avatarY: Float = 0f)
