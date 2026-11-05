package com.r0ybt.arachn0de.data.local

import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** Private settings share the database transaction used by backup restoration. */
@Entity(tableName="private_preferences")
internal data class PrivatePreferenceEntity(@PrimaryKey val name:String,val payload:String)
/** Read bounded rows: a valid image-rich skin library can exceed Android's CursorWindow. */
@Dao internal abstract class PrivatePreferenceDao {
    @Query("SELECT name FROM private_preferences ORDER BY name") abstract suspend fun names():List<String>
    @Query("SELECT length(CAST(payload AS BLOB)) FROM private_preferences WHERE name=:name") abstract suspend fun payloadLength(name:String):Int?
    @Query("SELECT substr(CAST(payload AS BLOB),:start,:count) FROM private_preferences WHERE name=:name") abstract suspend fun payloadPart(name:String,start:Int,count:Int):ByteArray?
    @Transaction open suspend fun all():List<PrivatePreferenceEntity> = names().map {name->checkNotNull(get(name))}
    @Query("SELECT name,payload FROM private_preferences WHERE name=:name AND length(CAST(payload AS BLOB))<=131072")
    abstract suspend fun small(name:String):PrivatePreferenceEntity?
    @Query("SELECT CASE WHEN :payload IS NULL THEN NOT EXISTS(SELECT 1 FROM private_preferences WHERE name=:name) ELSE EXISTS(SELECT 1 FROM private_preferences WHERE name=:name AND payload=:payload) END")
    abstract suspend fun matchesPayload(name:String,payload:String?):Boolean
    open suspend fun get(name:String):PrivatePreferenceEntity? = small(name) ?: large(name)
    @Transaction open suspend fun large(name:String):PrivatePreferenceEntity? {
        val length=payloadLength(name) ?: return null
        val bytes=java.io.ByteArrayOutputStream(length)
        var position=1
        while(position<=length) {
            val part=checkNotNull(payloadPart(name,position,128*1024)) {"La preferencia cambió durante su lectura."}
            check(part.isNotEmpty()) {"La preferencia está incompleta."}
            bytes.write(part)
            position+=part.size
        }
        // Decode once after joining byte chunks: UTF-8 emoji may cross a chunk boundary.
        return PrivatePreferenceEntity(name,bytes.toString(Charsets.UTF_8.name()))
    }
    @Insert(onConflict=OnConflictStrategy.REPLACE) abstract suspend fun save(row:PrivatePreferenceEntity)
    @Query("DELETE FROM private_preferences WHERE name = :name") abstract suspend fun delete(name:String)
    @Query("DELETE FROM private_preferences") abstract suspend fun clear()
}
internal object SecurityMigration25To26:Migration(25,26) {
    override fun migrate(db:SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS private_preferences (name TEXT NOT NULL PRIMARY KEY, payload TEXT NOT NULL)")
    }
}
