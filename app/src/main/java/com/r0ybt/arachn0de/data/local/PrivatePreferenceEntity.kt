package com.r0ybt.arachn0de.data.local

import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** Private settings share the database transaction used by backup restoration. */
@Entity(tableName="private_preferences")
internal data class PrivatePreferenceEntity(@PrimaryKey val name:String,val payload:String)
@Dao internal interface PrivatePreferenceDao {
    @Query("SELECT * FROM private_preferences ORDER BY name") suspend fun all():List<PrivatePreferenceEntity>
    @Query("SELECT * FROM private_preferences WHERE name = :name") suspend fun get(name:String):PrivatePreferenceEntity?
    @Insert(onConflict=OnConflictStrategy.REPLACE) suspend fun save(row:PrivatePreferenceEntity)
    @Query("DELETE FROM private_preferences") suspend fun clear()
}
internal object SecurityMigration25To26:Migration(25,26) {
    override fun migrate(db:SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS private_preferences (name TEXT NOT NULL PRIMARY KEY, payload TEXT NOT NULL)")
    }
}
