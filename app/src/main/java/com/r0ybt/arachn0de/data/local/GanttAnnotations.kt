package com.r0ybt.arachn0de.data.local

import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

/** Calendar day and normalized lane position survive density, zoom and orientation changes. */
@Entity(tableName="gantt_annotations",foreignKeys=[ForeignKey(entity=ManagementContextEntity::class,parentColumns=["id"],childColumns=["contextId"],onDelete=ForeignKey.CASCADE)],indices=[Index("contextId")])
data class GanttAnnotationEntity(@PrimaryKey val id:String,val contextId:String,val text:String,val epochDay:Long,val lane:Double)
internal fun validateGanttAnnotations(rows:List<GanttAnnotationEntity>,contexts:Set<String>) {
    require(rows.map {it.id}.distinct().size==rows.size) {"Anotaciones duplicadas."}
    rows.forEach {require(it.id.isNotBlank() && it.id.length<=200 && it.contextId in contexts && it.text.isNotBlank() && it.text.length<=4000 && it.epochDay in 0L..2932896L && it.lane.isFinite() && it.lane in 0.0..1.0) {"Anotación Gantt inválida."}}
}
@Dao interface GanttAnnotationDao {
    @Query("SELECT * FROM gantt_annotations ORDER BY epochDay,id") suspend fun all():List<GanttAnnotationEntity>
    @Query("SELECT * FROM gantt_annotations WHERE contextId=:context ORDER BY epochDay,id") fun observe(context:String):Flow<List<GanttAnnotationEntity>>
    @Upsert suspend fun save(row:GanttAnnotationEntity)
    @Query("DELETE FROM gantt_annotations WHERE id=:id AND contextId=:context") suspend fun delete(id:String,context:String)
    @Query("DELETE FROM gantt_annotations") suspend fun clear()
}
object GanttMigration37To38:Migration(37,38) {
    override fun migrate(db:SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE gantt_annotations (id TEXT NOT NULL PRIMARY KEY, contextId TEXT NOT NULL, text TEXT NOT NULL, epochDay INTEGER NOT NULL, lane REAL NOT NULL, FOREIGN KEY(contextId) REFERENCES management_contexts(id) ON UPDATE NO ACTION ON DELETE CASCADE)")
        db.execSQL("CREATE INDEX index_gantt_annotations_contextId ON gantt_annotations(contextId)")
    }
}
