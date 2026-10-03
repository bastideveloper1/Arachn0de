package com.r0ybt.arachn0de.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

/** Destination references deliberately do not cascade: deleting a layer pauses a rule at next check. */
@Entity(tableName = "recurrence_rules", indices = [Index(value = ["status"])])
data class RecurrenceRuleEntity(
    @PrimaryKey val id: String,
    val projectId: String,
    val parentId: String?,
    val title: String,
    val description: String,
    val amountMinor: Long?,
    val currencyCode: String?,
    val startDay: Long,
    val frequency: String,
    val interval: Int,
    val endDay: Long?,
    val nextIndex: Long,
    val status: String,
    val zoneId: String,
    val dueMinute: Int,
    val startOffsetMillis: Long?,
)

/** Never cascades from Nodes. A deleted Node leaves this durable materialization receipt. */
@Entity(tableName = "recurrence_occurrences", primaryKeys = ["ruleId", "day"],
    foreignKeys = [ForeignKey(entity = RecurrenceRuleEntity::class, parentColumns = ["id"], childColumns = ["ruleId"], onDelete = ForeignKey.RESTRICT)],
    indices = [Index(value = ["nodeId"], unique = true)])
data class RecurrenceOccurrenceEntity(val ruleId: String, val day: Long, val nodeId: String)

@Entity(tableName = "recurrence_person", primaryKeys = ["ruleId", "personId"],
    foreignKeys = [ForeignKey(entity = RecurrenceRuleEntity::class, parentColumns = ["id"], childColumns = ["ruleId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = PersonEntity::class, parentColumns = ["id"], childColumns = ["personId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index(value = ["personId"])])
data class RecurrencePersonEntity(val ruleId: String, val personId: String)

@Dao
interface RecurrenceDao {
    @Query("SELECT * FROM recurrence_rules ORDER BY id") suspend fun rules(): List<RecurrenceRuleEntity>
    @Query("SELECT * FROM recurrence_rules ORDER BY id") fun observeRules(): Flow<List<RecurrenceRuleEntity>>
    @Query("SELECT * FROM recurrence_rules WHERE id = :id") suspend fun get(id: String): RecurrenceRuleEntity?
    @Insert suspend fun insert(rule: RecurrenceRuleEntity)
    @Update suspend fun update(rule: RecurrenceRuleEntity)
    @Query("SELECT * FROM recurrence_occurrences ORDER BY ruleId, day") suspend fun occurrences(): List<RecurrenceOccurrenceEntity>
    @Query("SELECT * FROM recurrence_occurrences") fun observeOccurrences(): Flow<List<RecurrenceOccurrenceEntity>>
    @Query("SELECT * FROM recurrence_occurrences WHERE ruleId = :id AND day = :day") suspend fun occurrence(id: String, day: Long): RecurrenceOccurrenceEntity?
    @Insert suspend fun record(occurrence: RecurrenceOccurrenceEntity)
    @Query("SELECT * FROM recurrence_person ORDER BY ruleId, personId") suspend fun assignments(): List<RecurrencePersonEntity>
    @Query("SELECT personId FROM recurrence_person WHERE ruleId = :id") suspend fun people(id: String): List<String>
    @Insert suspend fun assign(people: List<RecurrencePersonEntity>)
    @Query("DELETE FROM recurrence_person WHERE ruleId = :id") suspend fun clearPeople(id: String)
    @Query("DELETE FROM recurrence_occurrences") suspend fun deleteOccurrences()
    @Query("DELETE FROM recurrence_person") suspend fun deleteAssignments()
    @Query("DELETE FROM recurrence_rules") suspend fun deleteRules()
}
