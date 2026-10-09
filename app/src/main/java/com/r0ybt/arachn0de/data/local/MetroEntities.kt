package com.r0ybt.arachn0de.data.local

import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName="metro_preferences")
internal data class MetroPreferencesEntity(@PrimaryKey val id: Int=1, val payload: String)
@Entity(tableName="metro_journeys", foreignKeys=[
    ForeignKey(entity=NodeEntity::class,parentColumns=["id"],childColumns=["nodeId"],onDelete=ForeignKey.SET_NULL),
    ForeignKey(entity=PersonEntity::class,parentColumns=["id"],childColumns=["personId"],onDelete=ForeignKey.SET_NULL)
],indices=[Index("nodeId",unique=true),Index("personId")])
internal data class MetroJourneyEntity(@PrimaryKey val id: String,val nodeId: String?=null,val personId: String?=null,val enabled: Boolean=true,val payload: String,val revision: Long=0)
@Dao internal interface MetroDao {
    @Query("SELECT * FROM metro_preferences WHERE id=1") suspend fun preferences(): MetroPreferencesEntity?
    @Query("SELECT * FROM metro_preferences WHERE id=1") fun observePreferences(): Flow<MetroPreferencesEntity?>
    @Upsert suspend fun preferences(value: MetroPreferencesEntity)
    @Query("SELECT * FROM metro_journeys ORDER BY id") suspend fun journeys(): List<MetroJourneyEntity>
    @Query("SELECT * FROM metro_journeys ORDER BY id") fun observeJourneys(): Flow<List<MetroJourneyEntity>>
    @Query("SELECT * FROM metro_journeys WHERE id=:id") suspend fun journey(id: String): MetroJourneyEntity?
    @Query("SELECT * FROM metro_journeys WHERE nodeId=:id") suspend fun forNode(id: String): MetroJourneyEntity?
    @Upsert suspend fun save(value: MetroJourneyEntity)
    @Query("DELETE FROM metro_journeys WHERE id=:id") suspend fun delete(id: String)
    @Query("DELETE FROM metro_journeys") suspend fun clearJourneys()
    @Query("DELETE FROM metro_preferences") suspend fun clearPreferences()
}
internal object MetroMigration23To24 : Migration(23,24) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE metro_preferences (id INTEGER NOT NULL PRIMARY KEY, payload TEXT NOT NULL)")
        db.execSQL("CREATE TABLE metro_journeys (id TEXT NOT NULL PRIMARY KEY, nodeId TEXT, personId TEXT, enabled INTEGER NOT NULL, payload TEXT NOT NULL, revision INTEGER NOT NULL, FOREIGN KEY(nodeId) REFERENCES nodes(id) ON DELETE SET NULL, FOREIGN KEY(personId) REFERENCES persons(id) ON DELETE SET NULL)")
        db.execSQL("CREATE UNIQUE INDEX index_metro_journeys_nodeId ON metro_journeys(nodeId)")
        db.execSQL("CREATE INDEX index_metro_journeys_personId ON metro_journeys(personId)")
    }
}
