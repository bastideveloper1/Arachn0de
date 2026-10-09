package com.r0ybt.arachn0de.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "saved_templates")
internal data class SavedTemplateEntity(@PrimaryKey val id: String, val name: String, val payload: String)
@Dao
internal interface SavedTemplateDao {
    @Query("SELECT * FROM saved_templates ORDER BY name COLLATE NOCASE, id") fun observe(): Flow<List<SavedTemplateEntity>>
    @Query("SELECT * FROM saved_templates ORDER BY id") suspend fun all(): List<SavedTemplateEntity>
    @Query("SELECT * FROM saved_templates WHERE id=:id") suspend fun get(id: String): SavedTemplateEntity?
    @Insert suspend fun insert(row: SavedTemplateEntity)
    @Update suspend fun update(row: SavedTemplateEntity): Int
    @Query("DELETE FROM saved_templates WHERE id=:id") suspend fun delete(id: String)
    @Query("DELETE FROM saved_templates") suspend fun clear()
}

internal object SavedTemplatesMigration24To25 : androidx.room.migration.Migration(24,25) {
    override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
        for ((table, owner) in listOf("node_technologies" to "nodeId", "project_technologies" to "projectId")) {
            db.execSQL("ALTER TABLE $table ADD COLUMN position INTEGER NOT NULL DEFAULT 0")
            // Recover the catalog order that the previous UI actually displayed.
            db.execSQL("UPDATE $table SET position=(SELECT COUNT(*) FROM $table AS links JOIN technologies AS t ON t.id=links.technologyId JOIN technologies AS current ON current.id=$table.technologyId WHERE links.$owner=$table.$owner AND (t.name COLLATE NOCASE < current.name COLLATE NOCASE OR (t.name COLLATE NOCASE = current.name COLLATE NOCASE AND t.id < current.id)))")
        }
        db.execSQL("CREATE TABLE IF NOT EXISTS saved_templates (id TEXT NOT NULL PRIMARY KEY, name TEXT NOT NULL, payload TEXT NOT NULL)")
    }
}
