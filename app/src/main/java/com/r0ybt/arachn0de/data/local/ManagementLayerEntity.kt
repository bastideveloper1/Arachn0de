package com.r0ybt.arachn0de.data.local

import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

/** Persistent semantics, independent of editable names and of project conversion. */
@Entity(tableName="management_layers", foreignKeys=[
    ForeignKey(entity=NodeEntity::class,parentColumns=["id"],childColumns=["nodeId"],onDelete=ForeignKey.CASCADE),
    ForeignKey(entity=ManagementContextEntity::class,parentColumns=["id"],childColumns=["contextId"],onDelete=ForeignKey.CASCADE)
], indices=[Index(value=["contextId","kind"],unique=true)])
data class ManagementLayerEntity(@PrimaryKey val nodeId:String,val contextId:String,val kind:String)

@Dao interface ManagementLayerDao {
    @Query("SELECT * FROM management_layers ORDER BY contextId,kind") suspend fun all():List<ManagementLayerEntity>
    @Query("SELECT * FROM management_layers ORDER BY contextId,kind") fun observe():Flow<List<ManagementLayerEntity>>
    @Insert(onConflict=OnConflictStrategy.ABORT) suspend fun insert(row:ManagementLayerEntity)
    @Query("DELETE FROM management_layers") suspend fun clear()
}
object CascadeMigration36To37:Migration(36,37) {
    override fun migrate(db:SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE management_layers (nodeId TEXT NOT NULL PRIMARY KEY, contextId TEXT NOT NULL, kind TEXT NOT NULL, FOREIGN KEY(nodeId) REFERENCES nodes(id) ON UPDATE NO ACTION ON DELETE CASCADE, FOREIGN KEY(contextId) REFERENCES management_contexts(id) ON UPDATE NO ACTION ON DELETE CASCADE)")
        db.execSQL("CREATE TABLE sprint_stories (sprintId TEXT NOT NULL, storyId TEXT NOT NULL, PRIMARY KEY(sprintId,storyId), FOREIGN KEY(sprintId) REFERENCES nodes(id) ON UPDATE NO ACTION ON DELETE CASCADE, FOREIGN KEY(storyId) REFERENCES nodes(id) ON UPDATE NO ACTION ON DELETE CASCADE)")
        db.execSQL("CREATE UNIQUE INDEX index_sprint_stories_storyId ON sprint_stories(storyId)")
        db.execSQL("CREATE TABLE objective_work (objectiveId TEXT NOT NULL, nodeId TEXT NOT NULL, PRIMARY KEY(objectiveId,nodeId), FOREIGN KEY(objectiveId) REFERENCES management_objectives(id) ON UPDATE NO ACTION ON DELETE CASCADE, FOREIGN KEY(nodeId) REFERENCES nodes(id) ON UPDATE NO ACTION ON DELETE CASCADE)")
        db.execSQL("CREATE INDEX index_objective_work_nodeId ON objective_work(nodeId)")
        db.execSQL("CREATE TABLE local_identity (slot TEXT NOT NULL PRIMARY KEY, personId TEXT NOT NULL, FOREIGN KEY(personId) REFERENCES persons(id) ON UPDATE NO ACTION ON DELETE CASCADE)")
        db.execSQL("CREATE UNIQUE INDEX index_local_identity_personId ON local_identity(personId)")
        db.execSQL("CREATE UNIQUE INDEX index_management_layers_contextId_kind ON management_layers(contextId,kind)")
    }
}
