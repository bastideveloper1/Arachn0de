package com.r0ybt.arachn0de.data.local

import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

/** Context identity is the permanent conversion root, independent of its physical owner. */
@Entity(tableName="management_contexts",foreignKeys=[ForeignKey(entity=ConversionRootEntity::class,
    parentColumns=["id"],childColumns=["id"],onDelete=ForeignKey.CASCADE)])
data class ManagementContextEntity(@PrimaryKey val id:String,val enabled:Boolean,val methodology:String)

@Dao interface ManagementContextDao {
    @Query("SELECT * FROM management_contexts ORDER BY id") suspend fun all():List<ManagementContextEntity>
    @Query("SELECT * FROM management_contexts ORDER BY id") fun observe():Flow<List<ManagementContextEntity>>
    @Upsert suspend fun save(row:ManagementContextEntity)
    @Query("DELETE FROM management_contexts WHERE id=:id") suspend fun delete(id:String)
}

object ManagementMigration28To29:Migration(28,29) {
    override fun migrate(db:SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE management_contexts (id TEXT NOT NULL PRIMARY KEY, enabled INTEGER NOT NULL, methodology TEXT NOT NULL, FOREIGN KEY(id) REFERENCES conversion_roots(id) ON UPDATE NO ACTION ON DELETE CASCADE)")
        install(db)
    }
    fun install(db:SupportSQLiteDatabase) {
        for(event in listOf("INSERT","UPDATE")) db.execSQL("CREATE TRIGGER IF NOT EXISTS management_valid_${event.lowercase()} BEFORE $event ON management_contexts WHEN NEW.enabled NOT IN (0,1) OR NEW.methodology NOT IN ('AGILE','TRADITIONAL','HYBRID') BEGIN SELECT RAISE(ABORT,'Contexto de gestión inválido'); END")
    }
}
