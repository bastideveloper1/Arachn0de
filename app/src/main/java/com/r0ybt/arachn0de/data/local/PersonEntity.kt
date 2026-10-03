package com.r0ybt.arachn0de.data.local

import androidx.room.*

@Entity(tableName = "persons")
data class PersonEntity(@PrimaryKey val id: String, val name: String, val avatarFile: String?)

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

data class AssignedPersonRow(val nodeId: String, val id: String, val name: String, val avatarFile: String?)
