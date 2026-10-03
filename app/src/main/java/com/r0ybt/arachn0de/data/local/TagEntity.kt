package com.r0ybt.arachn0de.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "tags", indices = [Index(value = ["normalizedName"], unique = true)])
data class TagEntity(@PrimaryKey val id: String, val name: String, val normalizedName: String)
@Entity(tableName = "node_tag", primaryKeys = ["nodeId", "tagId"], foreignKeys = [
    ForeignKey(entity = NodeEntity::class, parentColumns = ["id"], childColumns = ["nodeId"], onDelete = ForeignKey.CASCADE),
    ForeignKey(entity = TagEntity::class, parentColumns = ["id"], childColumns = ["tagId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("nodeId"), Index("tagId")])
data class NodeTagEntity(val nodeId: String, val tagId: String)
@Entity(tableName = "recurrence_tag", primaryKeys = ["ruleId", "tagId"], foreignKeys = [
    ForeignKey(entity = RecurrenceRuleEntity::class, parentColumns = ["id"], childColumns = ["ruleId"], onDelete = ForeignKey.CASCADE),
    ForeignKey(entity = TagEntity::class, parentColumns = ["id"], childColumns = ["tagId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("ruleId"), Index("tagId")])
data class RecurrenceTagEntity(val ruleId: String, val tagId: String)
data class TagLinks(@Embedded val tag: TagEntity,
    @Relation(parentColumn = "id", entityColumn = "tagId") val nodes: List<NodeTagEntity>,
    @Relation(parentColumn = "id", entityColumn = "tagId") val rules: List<RecurrenceTagEntity>)
@Dao interface TagDao {
    @Transaction @Query("SELECT * FROM tags ORDER BY normalizedName") fun observe(): Flow<List<TagLinks>>
    @Query("SELECT * FROM tags") suspend fun tags(): List<TagEntity>
    @Query("SELECT * FROM node_tag") suspend fun nodeTags(): List<NodeTagEntity>
    @Query("SELECT * FROM recurrence_tag") suspend fun ruleTags(): List<RecurrenceTagEntity>
    @Query("SELECT * FROM tags WHERE id = :id") suspend fun get(id: String): TagEntity?
    @Query("SELECT * FROM tags WHERE normalizedName = :name") suspend fun named(name: String): TagEntity?
    @Insert suspend fun insert(tags: List<TagEntity>)
    @Update suspend fun update(tag: TagEntity)
    @Query("DELETE FROM tags WHERE id = :id") suspend fun delete(id: String)
    @Query("DELETE FROM tags") suspend fun clear()
    @Query("SELECT tagId FROM node_tag WHERE nodeId = :id") suspend fun nodeIds(id: String): List<String>
    @Query("SELECT tagId FROM recurrence_tag WHERE ruleId = :id") suspend fun ruleIds(id: String): List<String>
    @Query("DELETE FROM node_tag WHERE nodeId = :id") suspend fun clearNode(id: String)
    @Query("DELETE FROM recurrence_tag WHERE ruleId = :id") suspend fun clearRule(id: String)
    @Insert suspend fun assignNodes(rows: List<NodeTagEntity>)
    @Insert suspend fun assignRules(rows: List<RecurrenceTagEntity>)
}
