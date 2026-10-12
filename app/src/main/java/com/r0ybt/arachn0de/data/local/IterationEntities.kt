package com.r0ybt.arachn0de.data.local

import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName="iteration_plans",foreignKeys=[
    ForeignKey(entity=ManagementContextEntity::class,parentColumns=["id"],childColumns=["contextId"],onDelete=ForeignKey.CASCADE),
    ForeignKey(entity=NodeEntity::class,parentColumns=["id"],childColumns=["sprintId"],onDelete=ForeignKey.RESTRICT)
],indices=[Index("contextId"),Index(value=["sprintId"],unique=true)])
data class IterationPlanEntity(@PrimaryKey val id:String,val contextId:String,val sprintId:String,val goal:String,val startAt:Long?,val endAt:Long?,val notes:String,val state:String)
@Entity(tableName="iteration_cycles",foreignKeys=[ForeignKey(entity=IterationPlanEntity::class,parentColumns=["id"],childColumns=["planId"],onDelete=ForeignKey.CASCADE)],indices=[Index(value=["planId","number"],unique=true)])
data class IterationCycleEntity(@PrimaryKey val id:String,val planId:String,val number:Int,val startedAt:Long,val closedAt:Long?)
@Entity(tableName="iteration_commitments",primaryKeys=["cycleId","taskId"],foreignKeys=[
    ForeignKey(entity=IterationCycleEntity::class,parentColumns=["id"],childColumns=["cycleId"],onDelete=ForeignKey.CASCADE),
    ForeignKey(entity=NodeEntity::class,parentColumns=["id"],childColumns=["taskId"],onDelete=ForeignKey.RESTRICT)
],indices=[Index("taskId")])
data class IterationCommitmentEntity(val cycleId:String,val taskId:String,val initial:Boolean,val addedAt:Long)
@Entity(tableName="iteration_closures",primaryKeys=["cycleId","taskId"],foreignKeys=[
    ForeignKey(entity=IterationCycleEntity::class,parentColumns=["id"],childColumns=["cycleId"],onDelete=ForeignKey.CASCADE),
    ForeignKey(entity=NodeEntity::class,parentColumns=["id"],childColumns=["taskId"],onDelete=ForeignKey.RESTRICT)
],indices=[Index("taskId")])
data class IterationClosureEntity(val cycleId:String,val taskId:String,val inScope:Boolean,val phaseAtClose:String?,val completedAtClose:Boolean)

@Dao interface IterationDao {
    @Query("SELECT * FROM iteration_plans ORDER BY id") suspend fun plans():List<IterationPlanEntity>
    @Query("SELECT * FROM iteration_plans ORDER BY id") fun observePlans():Flow<List<IterationPlanEntity>>
    @Query("SELECT * FROM iteration_cycles ORDER BY planId,number") suspend fun cycles():List<IterationCycleEntity>
    @Query("SELECT * FROM iteration_cycles ORDER BY planId,number") fun observeCycles():Flow<List<IterationCycleEntity>>
    @Query("SELECT * FROM iteration_commitments ORDER BY cycleId,taskId") suspend fun commitments():List<IterationCommitmentEntity>
    @Query("SELECT * FROM iteration_commitments ORDER BY cycleId,taskId") fun observeCommitments():Flow<List<IterationCommitmentEntity>>
    @Query("SELECT * FROM iteration_closures ORDER BY cycleId,taskId") suspend fun closures():List<IterationClosureEntity>
    @Query("SELECT * FROM iteration_closures ORDER BY cycleId,taskId") fun observeClosures():Flow<List<IterationClosureEntity>>
    @Upsert suspend fun save(row:IterationPlanEntity)
    @Insert suspend fun insert(row:IterationCycleEntity)
    @Insert(onConflict=OnConflictStrategy.IGNORE) suspend fun insert(row:IterationCommitmentEntity)
    @Insert suspend fun insert(row:IterationClosureEntity)
    @Query("UPDATE iteration_cycles SET closedAt=:time WHERE id=:id AND closedAt IS NULL") suspend fun close(id:String,time:Long):Int
    @Query("DELETE FROM iteration_plans") suspend fun clear()
}

object IterationMigration32To33:Migration(32,33) {
    override fun migrate(db:SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE iteration_plans (id TEXT NOT NULL PRIMARY KEY, contextId TEXT NOT NULL, sprintId TEXT NOT NULL, goal TEXT NOT NULL, startAt INTEGER, endAt INTEGER, notes TEXT NOT NULL, state TEXT NOT NULL, FOREIGN KEY(contextId) REFERENCES management_contexts(id) ON UPDATE NO ACTION ON DELETE CASCADE, FOREIGN KEY(sprintId) REFERENCES nodes(id) ON UPDATE NO ACTION ON DELETE RESTRICT)")
        db.execSQL("CREATE INDEX index_iteration_plans_contextId ON iteration_plans(contextId)")
        db.execSQL("CREATE UNIQUE INDEX index_iteration_plans_sprintId ON iteration_plans(sprintId)")
        db.execSQL("CREATE TABLE iteration_cycles (id TEXT NOT NULL PRIMARY KEY, planId TEXT NOT NULL, number INTEGER NOT NULL, startedAt INTEGER NOT NULL, closedAt INTEGER, FOREIGN KEY(planId) REFERENCES iteration_plans(id) ON UPDATE NO ACTION ON DELETE CASCADE)")
        db.execSQL("CREATE UNIQUE INDEX index_iteration_cycles_planId_number ON iteration_cycles(planId,number)")
        db.execSQL("CREATE TABLE iteration_commitments (cycleId TEXT NOT NULL, taskId TEXT NOT NULL, initial INTEGER NOT NULL, addedAt INTEGER NOT NULL, PRIMARY KEY(cycleId,taskId), FOREIGN KEY(cycleId) REFERENCES iteration_cycles(id) ON UPDATE NO ACTION ON DELETE CASCADE, FOREIGN KEY(taskId) REFERENCES nodes(id) ON UPDATE NO ACTION ON DELETE RESTRICT)")
        db.execSQL("CREATE INDEX index_iteration_commitments_taskId ON iteration_commitments(taskId)")
        db.execSQL("CREATE TABLE iteration_closures (cycleId TEXT NOT NULL, taskId TEXT NOT NULL, inScope INTEGER NOT NULL, phaseAtClose TEXT, completedAtClose INTEGER NOT NULL, PRIMARY KEY(cycleId,taskId), FOREIGN KEY(cycleId) REFERENCES iteration_cycles(id) ON UPDATE NO ACTION ON DELETE CASCADE, FOREIGN KEY(taskId) REFERENCES nodes(id) ON UPDATE NO ACTION ON DELETE RESTRICT)")
        db.execSQL("CREATE INDEX index_iteration_closures_taskId ON iteration_closures(taskId)")
        install(db)
    }
    fun install(db:SupportSQLiteDatabase) {
        val checks=mapOf(
            "iteration_plans" to "NEW.state NOT IN ('PREPARED','ACTIVE','CLOSED') OR NEW.startAt<0 OR NEW.endAt<0 OR NEW.endAt<NEW.startAt OR NOT EXISTS(SELECT 1 FROM nodes WHERE id=NEW.sprintId AND purpose='LAYER' AND sprintMode=1) OR COALESCE(${BacklogMigration30To31.scope("NEW.sprintId")},'')!=NEW.contextId",
            "iteration_cycles" to "NEW.number<1 OR NEW.startedAt<0 OR NEW.closedAt<NEW.startedAt",
            "iteration_commitments" to "NEW.initial NOT IN (0,1) OR NEW.addedAt<0 OR NOT EXISTS(SELECT 1 FROM iteration_cycles c JOIN iteration_plans p ON p.id=c.planId JOIN nodes n ON n.id=NEW.taskId WHERE c.id=NEW.cycleId AND n.purpose='ACTION' AND COALESCE(${BacklogMigration30To31.scope("NEW.taskId")},'')=p.contextId AND NEW.addedAt>=c.startedAt)",
            "iteration_closures" to "NEW.inScope NOT IN (0,1) OR NEW.completedAtClose NOT IN (0,1) OR (NEW.phaseAtClose IS NOT NULL AND NEW.phaseAtClose NOT IN ('UNPLANNED','PLANNED','DOING','DONE','VALIDATED')) OR NOT EXISTS(SELECT 1 FROM iteration_commitments WHERE cycleId=NEW.cycleId AND taskId=NEW.taskId)"
        )
        for((table,check) in checks) for(event in listOf("INSERT","UPDATE")) db.execSQL("CREATE TRIGGER IF NOT EXISTS ${table}_valid_${event.lowercase()} BEFORE $event ON $table WHEN $check BEGIN SELECT RAISE(ABORT,'Planificación de iteración incompatible'); END")
    }
}
