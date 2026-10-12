package com.r0ybt.arachn0de.data.local

import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

/** One external membership per task; direct membership remains the physical parent. */
@Entity(tableName="sprint_links",foreignKeys=[
    ForeignKey(entity=NodeEntity::class,parentColumns=["id"],childColumns=["taskId"],onDelete=ForeignKey.CASCADE),
    ForeignKey(entity=NodeEntity::class,parentColumns=["id"],childColumns=["sprintId"],onDelete=ForeignKey.CASCADE)
],indices=[Index("sprintId")])
data class SprintLinkEntity(@PrimaryKey val taskId:String,val sprintId:String)

@Dao interface SprintLinkDao {
    @Query("SELECT * FROM sprint_links ORDER BY taskId") suspend fun all():List<SprintLinkEntity>
    @Query("SELECT * FROM sprint_links ORDER BY taskId") fun observe():Flow<List<SprintLinkEntity>>
    @Query("SELECT * FROM sprint_links WHERE taskId=:id") suspend fun task(id:String):SprintLinkEntity?
    @Insert suspend fun insert(row:SprintLinkEntity)
    @Query("DELETE FROM sprint_links WHERE taskId=:id") suspend fun delete(id:String)
    @Query("DELETE FROM sprint_links") suspend fun clear()
}

object SprintLinkMigration27To28:Migration(27,28) {
    override fun migrate(db:SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS sprint_links (taskId TEXT NOT NULL PRIMARY KEY, sprintId TEXT NOT NULL, FOREIGN KEY(taskId) REFERENCES nodes(id) ON DELETE CASCADE, FOREIGN KEY(sprintId) REFERENCES nodes(id) ON DELETE CASCADE)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_sprint_links_sprintId ON sprint_links(sprintId)")
        SprintLinkInvariants.install(db)
    }
}
internal object SprintLinkInvariants {
    fun install(db:SupportSQLiteDatabase) {
        for((suffix,event) in listOf("insert" to "INSERT","update" to "UPDATE")) db.execSQL("""
            CREATE TRIGGER IF NOT EXISTS sprint_links_$suffix BEFORE $event ON sprint_links
            WHEN NOT EXISTS (SELECT 1 FROM nodes t JOIN nodes s ON s.id=NEW.sprintId
                WHERE t.id=NEW.taskId AND t.id!=s.id AND t.purpose='ACTION' AND s.purpose='LAYER'
                AND s.sprintMode=1 AND t.projectId=s.projectId
                AND NOT EXISTS(SELECT 1 FROM nodes p WHERE p.id=t.parentId AND p.sprintMode=1))
            BEGIN SELECT RAISE(ABORT,'Vínculo Sprint incompatible'); END
        """.trimIndent())
        db.execSQL("""
            CREATE TRIGGER IF NOT EXISTS nodes_sprint_links_update BEFORE UPDATE ON nodes
            WHEN EXISTS(SELECT 1 FROM sprint_links l JOIN nodes s ON s.id=l.sprintId
                WHERE l.taskId=NEW.id AND (NEW.purpose!='ACTION' OR NEW.projectId!=s.projectId
                    OR EXISTS(SELECT 1 FROM nodes p WHERE p.id=NEW.parentId AND p.sprintMode=1)))
                OR EXISTS(SELECT 1 FROM sprint_links l JOIN nodes t ON t.id=l.taskId
                    WHERE l.sprintId=NEW.id AND (NEW.purpose!='LAYER' OR NEW.sprintMode!=1 OR NEW.projectId!=t.projectId))
            BEGIN SELECT RAISE(ABORT,'Desvincula las tareas antes de cambiar el Sprint o el proyecto'); END
        """.trimIndent())
        db.execSQL("""
            CREATE TRIGGER IF NOT EXISTS nodes_sprint_links_delete BEFORE DELETE ON nodes
            WHEN EXISTS(SELECT 1 FROM sprint_links l WHERE l.sprintId=OLD.id)
            BEGIN SELECT RAISE(ABORT,'Desvincula las tareas externas antes de eliminar el Sprint'); END
        """.trimIndent())
    }
}
