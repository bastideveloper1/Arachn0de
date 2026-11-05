package com.r0ybt.arachn0de.data.local

import androidx.room.*
import com.r0ybt.arachn0de.domain.model.NodeEvent
import com.r0ybt.arachn0de.domain.model.NodeEventType
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "node_events", foreignKeys = [
    ForeignKey(entity = NodeEntity::class, parentColumns = ["id"], childColumns = ["nodeId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index(value = ["nodeId", "occurredAt"])])
data class NodeEventEntity(@PrimaryKey val id: String, val nodeId: String, val type: String, val occurredAt: Long)
internal fun NodeEventEntity.toEvent() = NodeEvent(id, nodeId, NodeEventType.valueOf(type), occurredAt)

data class NodeCompletionTime(val nodeId:String,val occurredAt:Long)

/** No event mutation API: ordinary domain writes only append; restore inserts the snapshot. */
@Dao interface NodeEventDao {
    @Query("SELECT EXISTS(SELECT 1 FROM node_events WHERE id=:eventId AND nodeId=:nodeId AND type='COMPLETED')")
    suspend fun committedCompletion(eventId:String,nodeId:String):Boolean

    @Query("""SELECT e.nodeId, MAX(e.occurredAt) AS occurredAt
        FROM nodes n JOIN node_events e ON e.nodeId=n.id
        WHERE n.projectId=:projectId AND e.type='COMPLETED' GROUP BY e.nodeId""")
    fun observeCompletionTimes(projectId:String):Flow<List<NodeCompletionTime>>

    @Query("SELECT * FROM node_events WHERE nodeId IN (:ids) ORDER BY occurredAt DESC, rowid DESC") suspend fun eventsForNodes(ids: List<String>): List<NodeEventEntity>

    @Query("SELECT * FROM node_events WHERE nodeId = :nodeId ORDER BY occurredAt DESC, rowid DESC")
    fun observe(nodeId: String): Flow<List<NodeEventEntity>>
    @Query("SELECT * FROM node_events WHERE nodeId = :nodeId ORDER BY occurredAt DESC, rowid DESC")
    suspend fun forNode(nodeId: String): List<NodeEventEntity>
    @Query("SELECT * FROM node_events ORDER BY rowid")
    suspend fun all(): List<NodeEventEntity>
    @Insert suspend fun insert(event: NodeEventEntity)
    @Insert suspend fun insertAll(events: List<NodeEventEntity>)
}
