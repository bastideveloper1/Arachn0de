package com.r0ybt.arachn0de.data.local

import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName="management_roles",foreignKeys=[
    ForeignKey(entity=ManagementContextEntity::class,parentColumns=["id"],childColumns=["contextId"],onDelete=ForeignKey.CASCADE),
    ForeignKey(entity=NodeEntity::class,parentColumns=["id"],childColumns=["nodeId"],onDelete=ForeignKey.CASCADE)
],indices=[Index("contextId")])
data class ManagementRoleEntity(@PrimaryKey val nodeId:String,val contextId:String,val role:String)
@Entity(tableName="objective_epics",primaryKeys=["objectiveId","epicId"],foreignKeys=[
    ForeignKey(entity=ManagementObjectiveEntity::class,parentColumns=["id"],childColumns=["objectiveId"],onDelete=ForeignKey.CASCADE),
    ForeignKey(entity=ManagementRoleEntity::class,parentColumns=["nodeId"],childColumns=["epicId"],onDelete=ForeignKey.CASCADE)
],indices=[Index("epicId")])
data class ObjectiveEpicEntity(val objectiveId:String,val epicId:String)
@Entity(tableName="epic_stories",primaryKeys=["epicId","storyId"],foreignKeys=[
    ForeignKey(entity=ManagementRoleEntity::class,parentColumns=["nodeId"],childColumns=["epicId"],onDelete=ForeignKey.CASCADE),
    ForeignKey(entity=ManagementRoleEntity::class,parentColumns=["nodeId"],childColumns=["storyId"],onDelete=ForeignKey.CASCADE)
],indices=[Index("storyId")])
data class EpicStoryEntity(val epicId:String,val storyId:String)
@Entity(tableName="story_tasks",primaryKeys=["storyId","taskId"],foreignKeys=[
    ForeignKey(entity=ManagementRoleEntity::class,parentColumns=["nodeId"],childColumns=["storyId"],onDelete=ForeignKey.CASCADE),
    ForeignKey(entity=NodeEntity::class,parentColumns=["id"],childColumns=["taskId"],onDelete=ForeignKey.CASCADE)
],indices=[Index("taskId")])
data class StoryTaskEntity(val storyId:String,val taskId:String)
@Entity(tableName="backlog_entries",foreignKeys=[
    ForeignKey(entity=ManagementContextEntity::class,parentColumns=["id"],childColumns=["contextId"],onDelete=ForeignKey.CASCADE),
    ForeignKey(entity=NodeEntity::class,parentColumns=["id"],childColumns=["nodeId"],onDelete=ForeignKey.CASCADE)
],indices=[Index("contextId")])
data class BacklogEntryEntity(@PrimaryKey val nodeId:String,val contextId:String,val position:Int)

@Dao interface BacklogDao {
    @Query("SELECT * FROM management_roles ORDER BY nodeId") suspend fun roles():List<ManagementRoleEntity>
    @Query("SELECT * FROM objective_epics ORDER BY objectiveId,epicId") suspend fun objectiveEpics():List<ObjectiveEpicEntity>
    @Query("SELECT * FROM epic_stories ORDER BY epicId,storyId") suspend fun epicStories():List<EpicStoryEntity>
    @Query("SELECT * FROM story_tasks ORDER BY storyId,taskId") suspend fun storyTasks():List<StoryTaskEntity>
    @Query("SELECT * FROM backlog_entries ORDER BY contextId,position,nodeId") suspend fun entries():List<BacklogEntryEntity>
    @Query("SELECT * FROM management_roles ORDER BY nodeId") fun observeRoles():Flow<List<ManagementRoleEntity>>
    @Query("SELECT * FROM objective_epics ORDER BY objectiveId,epicId") fun observeObjectiveEpics():Flow<List<ObjectiveEpicEntity>>
    @Query("SELECT * FROM epic_stories ORDER BY epicId,storyId") fun observeEpicStories():Flow<List<EpicStoryEntity>>
    @Query("SELECT * FROM story_tasks ORDER BY storyId,taskId") fun observeStoryTasks():Flow<List<StoryTaskEntity>>
    @Query("SELECT * FROM backlog_entries ORDER BY contextId,position,nodeId") fun observeEntries():Flow<List<BacklogEntryEntity>>
    @Upsert suspend fun save(row:ManagementRoleEntity)
    @Upsert suspend fun save(row:BacklogEntryEntity)
    @Insert(onConflict=OnConflictStrategy.IGNORE) suspend fun insert(row:ObjectiveEpicEntity)
    @Insert(onConflict=OnConflictStrategy.IGNORE) suspend fun insert(row:EpicStoryEntity)
    @Insert(onConflict=OnConflictStrategy.IGNORE) suspend fun insert(row:StoryTaskEntity)
    @Query("DELETE FROM management_roles WHERE nodeId=:id") suspend fun deleteRole(id:String)
    @Query("DELETE FROM backlog_entries WHERE nodeId=:id") suspend fun deleteEntry(id:String)
    @Query("DELETE FROM objective_epics WHERE objectiveId=:objective AND epicId=:epic") suspend fun unlinkObjective(objective:String,epic:String)
    @Query("DELETE FROM epic_stories WHERE epicId=:epic AND storyId=:story") suspend fun unlinkEpic(epic:String,story:String)
    @Query("DELETE FROM story_tasks WHERE storyId=:story AND taskId=:task") suspend fun unlinkTask(story:String,task:String)
}

object BacklogMigration30To31:Migration(30,31) {
    override fun migrate(db:SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE management_roles (nodeId TEXT NOT NULL PRIMARY KEY, contextId TEXT NOT NULL, role TEXT NOT NULL, FOREIGN KEY(contextId) REFERENCES management_contexts(id) ON UPDATE NO ACTION ON DELETE CASCADE, FOREIGN KEY(nodeId) REFERENCES nodes(id) ON UPDATE NO ACTION ON DELETE CASCADE)")
        db.execSQL("CREATE INDEX index_management_roles_contextId ON management_roles(contextId)")
        db.execSQL("CREATE TABLE objective_epics (objectiveId TEXT NOT NULL, epicId TEXT NOT NULL, PRIMARY KEY(objectiveId,epicId), FOREIGN KEY(objectiveId) REFERENCES management_objectives(id) ON UPDATE NO ACTION ON DELETE CASCADE, FOREIGN KEY(epicId) REFERENCES management_roles(nodeId) ON UPDATE NO ACTION ON DELETE CASCADE)")
        db.execSQL("CREATE INDEX index_objective_epics_epicId ON objective_epics(epicId)")
        db.execSQL("CREATE TABLE epic_stories (epicId TEXT NOT NULL, storyId TEXT NOT NULL, PRIMARY KEY(epicId,storyId), FOREIGN KEY(epicId) REFERENCES management_roles(nodeId) ON UPDATE NO ACTION ON DELETE CASCADE, FOREIGN KEY(storyId) REFERENCES management_roles(nodeId) ON UPDATE NO ACTION ON DELETE CASCADE)")
        db.execSQL("CREATE INDEX index_epic_stories_storyId ON epic_stories(storyId)")
        db.execSQL("CREATE TABLE story_tasks (storyId TEXT NOT NULL, taskId TEXT NOT NULL, PRIMARY KEY(storyId,taskId), FOREIGN KEY(storyId) REFERENCES management_roles(nodeId) ON UPDATE NO ACTION ON DELETE CASCADE, FOREIGN KEY(taskId) REFERENCES nodes(id) ON UPDATE NO ACTION ON DELETE CASCADE)")
        db.execSQL("CREATE INDEX index_story_tasks_taskId ON story_tasks(taskId)")
        db.execSQL("CREATE TABLE backlog_entries (nodeId TEXT NOT NULL PRIMARY KEY, contextId TEXT NOT NULL, position INTEGER NOT NULL, FOREIGN KEY(contextId) REFERENCES management_contexts(id) ON UPDATE NO ACTION ON DELETE CASCADE, FOREIGN KEY(nodeId) REFERENCES nodes(id) ON UPDATE NO ACTION ON DELETE CASCADE)")
        db.execSQL("CREATE INDEX index_backlog_entries_contextId ON backlog_entries(contextId)")
        install(db)
    }
    internal fun scope(node:String)="""(WITH RECURSIVE ancestors(id,parentId,projectId,depth) AS (
        SELECT id,parentId,projectId,0 FROM nodes WHERE id=$node UNION ALL
        SELECT n.id,n.parentId,n.projectId,a.depth+1 FROM nodes n JOIN ancestors a ON n.id=a.parentId)
        SELECT COALESCE((SELECT c.id FROM ancestors a JOIN conversion_roots r ON r.nodeId=a.id JOIN management_contexts c ON c.id=r.id ORDER BY a.depth LIMIT 1),
        (SELECT c.id FROM nodes n JOIN conversion_roots r ON r.projectId=n.projectId JOIN management_contexts c ON c.id=r.id WHERE n.id=$node)))"""
    fun install(db:SupportSQLiteDatabase) {
        val checks=mapOf(
            "management_roles" to "NEW.role NOT IN ('EPIC','STORY') OR COALESCE((SELECT purpose FROM nodes WHERE id=NEW.nodeId),'')!='LAYER' OR COALESCE(${scope("NEW.nodeId")},'')!=NEW.contextId OR (NEW.role!='EPIC' AND (EXISTS(SELECT 1 FROM objective_epics WHERE epicId=NEW.nodeId) OR EXISTS(SELECT 1 FROM epic_stories WHERE epicId=NEW.nodeId))) OR (NEW.role!='STORY' AND (EXISTS(SELECT 1 FROM epic_stories WHERE storyId=NEW.nodeId) OR EXISTS(SELECT 1 FROM story_tasks WHERE storyId=NEW.nodeId) OR EXISTS(SELECT 1 FROM backlog_entries WHERE nodeId=NEW.nodeId)))",
            "objective_epics" to "NOT EXISTS(SELECT 1 FROM management_objectives o JOIN management_roles r ON r.contextId=o.contextId WHERE o.id=NEW.objectiveId AND r.nodeId=NEW.epicId AND r.role='EPIC')",
            "epic_stories" to "NOT EXISTS(SELECT 1 FROM management_roles e JOIN management_roles s ON e.contextId=s.contextId WHERE e.nodeId=NEW.epicId AND s.nodeId=NEW.storyId AND e.role='EPIC' AND s.role='STORY')",
            "story_tasks" to "NOT EXISTS(SELECT 1 FROM management_roles s JOIN nodes t ON t.id=NEW.taskId WHERE s.nodeId=NEW.storyId AND s.role='STORY' AND t.purpose='ACTION' AND COALESCE(${scope("NEW.taskId")},'')=s.contextId)",
            "backlog_entries" to "NEW.position<0 OR COALESCE(${scope("NEW.nodeId")},'')!=NEW.contextId OR NOT EXISTS(SELECT 1 FROM nodes n WHERE n.id=NEW.nodeId AND (n.purpose='ACTION' OR EXISTS(SELECT 1 FROM management_roles r WHERE r.nodeId=n.id AND r.contextId=NEW.contextId AND r.role='STORY')))"
        )
        for((table,check) in checks) for(event in listOf("INSERT","UPDATE")) db.execSQL("CREATE TRIGGER IF NOT EXISTS ${table}_valid_${event.lowercase()} BEFORE $event ON $table WHEN $check BEGIN SELECT RAISE(ABORT,'Relación de gestión incompatible'); END")
    }
}
