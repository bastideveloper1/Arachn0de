package com.r0ybt.arachn0de.data.local

import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

/** Original captures survive trip deletion. Invalidation never rewrites their payload. */
@Entity(tableName = "metro_learning_events", indices = [Index(value = ["sessionId", "sequence"], unique = true)])
internal data class MetroLearningEventEntity(
    @PrimaryKey val id: String,
    val sessionId: String,
    val sequence: Long,
    val payload: String,
    val invalidReason: String? = null,
)

/** Device-local clock provenance, regenerated on restore; not user learning data. */
@Entity(tableName = "metro_learning_clock")
internal data class MetroLearningClockEntity(@PrimaryKey val id: Int = 1, val domain: String)

@Dao
internal interface MetroLearningDao {
    @Query("SELECT * FROM metro_learning_events ORDER BY rowid")
    suspend fun events(): List<MetroLearningEventEntity>
    @Query("SELECT * FROM metro_learning_events ORDER BY rowid")
    fun observeEvents(): Flow<List<MetroLearningEventEntity>>
    @Query("SELECT * FROM metro_learning_events WHERE sessionId=:session ORDER BY sequence")
    suspend fun events(session: String): List<MetroLearningEventEntity>
    @Insert suspend fun insert(event: MetroLearningEventEntity)
    @Query("UPDATE metro_learning_events SET invalidReason=:reason WHERE id=:id AND invalidReason IS NULL")
    suspend fun invalidate(id: String, reason: String)
    @Query("DELETE FROM metro_learning_events") suspend fun clearEvents()
    @Query("SELECT * FROM metro_learning_clock WHERE id=1") suspend fun clock(): MetroLearningClockEntity?
    @Insert suspend fun clock(clock: MetroLearningClockEntity)
    @Query("DELETE FROM metro_learning_clock") suspend fun clearClock()
}

internal object MetroLearningMigration38To39 : Migration(38, 39) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE metro_learning_events (id TEXT NOT NULL PRIMARY KEY, sessionId TEXT NOT NULL, sequence INTEGER NOT NULL, payload TEXT NOT NULL, invalidReason TEXT)")
        db.execSQL("CREATE UNIQUE INDEX index_metro_learning_events_sessionId_sequence ON metro_learning_events(sessionId, sequence)")
        db.execSQL("CREATE TABLE metro_learning_clock (id INTEGER NOT NULL PRIMARY KEY, domain TEXT NOT NULL)")
    }
}
