package com.r0ybt.arachn0de.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

/** Only properties without a counterpart are dormant; content and descendants always remain live. */
@Entity(tableName = "conversion_roots", foreignKeys = [
    ForeignKey(entity = ProjectEntity::class, parentColumns = ["id"], childColumns = ["projectId"], onDelete = ForeignKey.CASCADE),
    ForeignKey(entity = NodeEntity::class, parentColumns = ["id"], childColumns = ["nodeId"], onDelete = ForeignKey.CASCADE),
], indices = [Index(value = ["projectId"], unique = true), Index(value = ["nodeId"], unique = true)])
data class ConversionRootEntity(@PrimaryKey val id: String, val projectId: String?, val nodeId: String?,
    val projectIdentity: String, val nodeIdentity: String, val projectPosition: Int, val nodePosition: Int,
    val startAt: Long?, val dueAt: Long?, val priority: String, val creationGroupId: String?, val sprintMode: Boolean)

@Entity(tableName = "conversion_people", primaryKeys = ["rootId", "personId"], foreignKeys = [
    ForeignKey(entity = ConversionRootEntity::class, parentColumns = ["id"], childColumns = ["rootId"], onDelete = ForeignKey.CASCADE),
    ForeignKey(entity = PersonEntity::class, parentColumns = ["id"], childColumns = ["personId"], onDelete = ForeignKey.CASCADE),
], indices = [Index("personId")])
data class ConversionPersonEntity(val rootId: String, val personId: String)
@Entity(tableName = "conversion_tags", primaryKeys = ["rootId", "tagId"], foreignKeys = [
    ForeignKey(entity = ConversionRootEntity::class, parentColumns = ["id"], childColumns = ["rootId"], onDelete = ForeignKey.CASCADE),
    ForeignKey(entity = TagEntity::class, parentColumns = ["id"], childColumns = ["tagId"], onDelete = ForeignKey.CASCADE),
], indices = [Index("tagId")])
data class ConversionTagEntity(val rootId: String, val tagId: String)
@Entity(tableName = "conversion_events", foreignKeys = [
    ForeignKey(entity = ConversionRootEntity::class, parentColumns = ["id"], childColumns = ["rootId"], onDelete = ForeignKey.CASCADE),
], indices = [Index("rootId")])
data class ConversionEventEntity(@PrimaryKey val id: String, val rootId: String, val type: String, val occurredAt: Long)
@Entity(tableName = "conversion_work_states", foreignKeys = [
    ForeignKey(entity = ConversionRootEntity::class, parentColumns = ["id"], childColumns = ["rootId"], onDelete = ForeignKey.CASCADE),
    ForeignKey(entity = NodeEntity::class, parentColumns = ["id"], childColumns = ["nodeId"], onDelete = ForeignKey.CASCADE),
], indices = [Index("rootId")])
data class ConversionWorkStateEntity(@PrimaryKey val nodeId: String, val rootId: String, val workState: String)
@Entity(tableName = "node_sort_preferences")
data class NodeSortPreferenceEntity(@PrimaryKey val context: String, val mode: String, @ColumnInfo(defaultValue="0") val layersFirst:Boolean=false)

@Dao interface ConversionDao {
    @Query("SELECT * FROM conversion_roots ORDER BY id") suspend fun roots(): List<ConversionRootEntity>
    @Query("SELECT * FROM conversion_roots WHERE projectId = :id") suspend fun project(id: String): ConversionRootEntity?
    @Query("SELECT * FROM conversion_roots WHERE nodeId = :id") suspend fun node(id: String): ConversionRootEntity?
    @Query("SELECT * FROM conversion_people ORDER BY rootId, personId") suspend fun people(): List<ConversionPersonEntity>
    @Query("SELECT * FROM conversion_tags ORDER BY rootId, tagId") suspend fun tags(): List<ConversionTagEntity>
    @Query("SELECT * FROM conversion_events ORDER BY occurredAt, rowid") suspend fun events(): List<ConversionEventEntity>
    @Query("SELECT * FROM conversion_work_states ORDER BY nodeId") suspend fun workStates(): List<ConversionWorkStateEntity>
    @Upsert suspend fun save(row: ConversionRootEntity)
    @Insert suspend fun people(rows: List<ConversionPersonEntity>)
    @Insert suspend fun tags(rows: List<ConversionTagEntity>)
    @Insert suspend fun events(rows: List<ConversionEventEntity>)
    @Insert suspend fun workStates(rows: List<ConversionWorkStateEntity>)
    @Query("DELETE FROM conversion_people WHERE rootId = :id") suspend fun clearPeople(id: String)
    @Query("DELETE FROM conversion_tags WHERE rootId = :id") suspend fun clearTags(id: String)
    @Query("DELETE FROM conversion_events WHERE rootId = :id") suspend fun clearEvents(id: String)
    @Query("DELETE FROM conversion_work_states WHERE rootId = :id") suspend fun clearWorkStates(id: String)
    @Query("DELETE FROM conversion_roots") suspend fun clear()
}
@Dao interface NodeSortPreferenceDao {
    @Query("SELECT * FROM node_sort_preferences ORDER BY context") suspend fun all(): List<NodeSortPreferenceEntity>
    @Query("SELECT * FROM node_sort_preferences ORDER BY context") fun observe(): Flow<List<NodeSortPreferenceEntity>>
    @Upsert suspend fun save(row: NodeSortPreferenceEntity)
    @Query("DELETE FROM node_sort_preferences WHERE context = :context") suspend fun delete(context: String)
    @Query("DELETE FROM node_sort_preferences") suspend fun clear()
}
