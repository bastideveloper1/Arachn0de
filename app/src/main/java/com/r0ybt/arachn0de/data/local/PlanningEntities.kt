package com.r0ybt.arachn0de.data.local

import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName="project_charters",foreignKeys=[ForeignKey(entity=ManagementContextEntity::class,parentColumns=["id"],childColumns=["contextId"],onDelete=ForeignKey.CASCADE)])
data class ProjectCharterEntity(@PrimaryKey val contextId:String,val purpose:String="",val scope:String="",val exclusions:String="",val deliverables:String="",val successConditions:String="")
@Entity(tableName="management_objectives",foreignKeys=[ForeignKey(entity=ManagementContextEntity::class,parentColumns=["id"],childColumns=["contextId"],onDelete=ForeignKey.CASCADE)],indices=[Index("contextId")])
data class ManagementObjectiveEntity(@PrimaryKey val id:String,val contextId:String,val title:String,val description:String="",val position:Int)
@Entity(tableName="acceptance_criteria",foreignKeys=[ForeignKey(entity=ManagementObjectiveEntity::class,parentColumns=["id"],childColumns=["objectiveId"],onDelete=ForeignKey.CASCADE)],indices=[Index("objectiveId")])
data class AcceptanceCriterionEntity(@PrimaryKey val id:String,val objectiveId:String,val description:String,val position:Int,val validated:Boolean=false,val validatedAt:Long?=null)

@Dao interface PlanningDao {
    @Query("SELECT * FROM project_charters ORDER BY contextId") suspend fun charters():List<ProjectCharterEntity>
    @Query("SELECT * FROM management_objectives ORDER BY contextId, position, id") suspend fun objectives():List<ManagementObjectiveEntity>
    @Query("SELECT * FROM acceptance_criteria ORDER BY objectiveId, position, id") suspend fun criteria():List<AcceptanceCriterionEntity>
    @Query("SELECT * FROM project_charters WHERE contextId=:id") fun observeCharter(id:String):Flow<ProjectCharterEntity?>
    @Query("SELECT * FROM management_objectives WHERE contextId=:id ORDER BY position,id") fun observeObjectives(id:String):Flow<List<ManagementObjectiveEntity>>
    @Query("SELECT c.* FROM acceptance_criteria c JOIN management_objectives o ON o.id=c.objectiveId WHERE o.contextId=:id ORDER BY c.position,c.id") fun observeCriteria(id:String):Flow<List<AcceptanceCriterionEntity>>
    @Query("SELECT * FROM management_objectives WHERE id=:id") suspend fun objective(id:String):ManagementObjectiveEntity?
    @Query("SELECT * FROM acceptance_criteria WHERE id=:id") suspend fun criterion(id:String):AcceptanceCriterionEntity?
    @Upsert suspend fun save(row:ProjectCharterEntity)
    @Upsert suspend fun save(row:ManagementObjectiveEntity)
    @Upsert suspend fun save(row:AcceptanceCriterionEntity)
    @Query("DELETE FROM management_objectives WHERE id=:id") suspend fun deleteObjective(id:String)
    @Query("DELETE FROM acceptance_criteria WHERE id=:id") suspend fun deleteCriterion(id:String)
}

object PlanningMigration29To30:Migration(29,30) {
    override fun migrate(db:SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE project_charters (contextId TEXT NOT NULL PRIMARY KEY, purpose TEXT NOT NULL, scope TEXT NOT NULL, exclusions TEXT NOT NULL, deliverables TEXT NOT NULL, successConditions TEXT NOT NULL, FOREIGN KEY(contextId) REFERENCES management_contexts(id) ON UPDATE NO ACTION ON DELETE CASCADE)")
        db.execSQL("CREATE TABLE management_objectives (id TEXT NOT NULL PRIMARY KEY, contextId TEXT NOT NULL, title TEXT NOT NULL, description TEXT NOT NULL, position INTEGER NOT NULL, FOREIGN KEY(contextId) REFERENCES management_contexts(id) ON UPDATE NO ACTION ON DELETE CASCADE)")
        db.execSQL("CREATE INDEX index_management_objectives_contextId ON management_objectives(contextId)")
        db.execSQL("CREATE TABLE acceptance_criteria (id TEXT NOT NULL PRIMARY KEY, objectiveId TEXT NOT NULL, description TEXT NOT NULL, position INTEGER NOT NULL, validated INTEGER NOT NULL, validatedAt INTEGER, FOREIGN KEY(objectiveId) REFERENCES management_objectives(id) ON UPDATE NO ACTION ON DELETE CASCADE)")
        db.execSQL("CREATE INDEX index_acceptance_criteria_objectiveId ON acceptance_criteria(objectiveId)")
        install(db)
    }
    fun install(db:SupportSQLiteDatabase) {
        for(event in listOf("INSERT","UPDATE")) {
            db.execSQL("CREATE TRIGGER IF NOT EXISTS objective_valid_${event.lowercase()} BEFORE $event ON management_objectives WHEN length(trim(NEW.title))=0 OR NEW.position<0 BEGIN SELECT RAISE(ABORT,'Objetivo inválido'); END")
            db.execSQL("CREATE TRIGGER IF NOT EXISTS criterion_valid_${event.lowercase()} BEFORE $event ON acceptance_criteria WHEN length(trim(NEW.description))=0 OR NEW.position<0 OR NEW.validated NOT IN (0,1) OR (NEW.validated=1)!=(NEW.validatedAt IS NOT NULL) OR NEW.validatedAt<0 BEGIN SELECT RAISE(ABORT,'Criterio inválido'); END")
        }
    }
}
