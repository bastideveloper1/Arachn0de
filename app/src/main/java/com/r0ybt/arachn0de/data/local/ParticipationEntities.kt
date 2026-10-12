package com.r0ybt.arachn0de.data.local

import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName="management_participants",primaryKeys=["contextId","personId"],foreignKeys=[
    ForeignKey(entity=ManagementContextEntity::class,parentColumns=["id"],childColumns=["contextId"],onDelete=ForeignKey.CASCADE),
    ForeignKey(entity=PersonEntity::class,parentColumns=["id"],childColumns=["personId"],onDelete=ForeignKey.CASCADE)
],indices=[Index("personId")])
data class ManagementParticipantEntity(val contextId:String,val personId:String)

@Entity(tableName="participant_roles",primaryKeys=["contextId","personId","role"],foreignKeys=[
    ForeignKey(entity=ManagementParticipantEntity::class,parentColumns=["contextId","personId"],childColumns=["contextId","personId"],onDelete=ForeignKey.CASCADE)
],indices=[Index(value=["contextId","personId"])])
data class ParticipantRoleEntity(val contextId:String,val personId:String,val role:String)

@Entity(tableName="objective_responsibilities",primaryKeys=["contextId","personId","objectiveId","role"],foreignKeys=[
    ForeignKey(entity=ManagementParticipantEntity::class,parentColumns=["contextId","personId"],childColumns=["contextId","personId"],onDelete=ForeignKey.CASCADE),
    ForeignKey(entity=ManagementObjectiveEntity::class,parentColumns=["id"],childColumns=["objectiveId"],onDelete=ForeignKey.CASCADE)
],indices=[Index(value=["contextId","personId"]),Index("objectiveId")])
data class ObjectiveResponsibilityEntity(val contextId:String,val personId:String,val objectiveId:String,val role:String)

@Entity(tableName="node_responsibilities",primaryKeys=["contextId","personId","nodeId","role"],foreignKeys=[
    ForeignKey(entity=ManagementParticipantEntity::class,parentColumns=["contextId","personId"],childColumns=["contextId","personId"],onDelete=ForeignKey.CASCADE),
    ForeignKey(entity=NodeEntity::class,parentColumns=["id"],childColumns=["nodeId"],onDelete=ForeignKey.CASCADE)
],indices=[Index(value=["contextId","personId"]),Index("nodeId")])
data class NodeResponsibilityEntity(val contextId:String,val personId:String,val nodeId:String,val role:String)

@Dao interface ParticipationDao {
    @Query("SELECT * FROM management_participants ORDER BY contextId,personId") suspend fun participants():List<ManagementParticipantEntity>
    @Query("SELECT * FROM management_participants ORDER BY contextId,personId") fun observeParticipants():Flow<List<ManagementParticipantEntity>>
    @Insert(onConflict=OnConflictStrategy.IGNORE) suspend fun insert(row:ManagementParticipantEntity)
    @Query("SELECT * FROM participant_roles ORDER BY contextId,personId,role") suspend fun roles():List<ParticipantRoleEntity>
    @Query("SELECT * FROM participant_roles ORDER BY contextId,personId") fun observeRoles():Flow<List<ParticipantRoleEntity>>
    @Insert(onConflict=OnConflictStrategy.IGNORE) suspend fun insert(row:ParticipantRoleEntity)
    @Query("SELECT * FROM objective_responsibilities ORDER BY contextId,personId,role") suspend fun objectives():List<ObjectiveResponsibilityEntity>
    @Query("SELECT * FROM objective_responsibilities ORDER BY contextId,personId") fun observeObjectives():Flow<List<ObjectiveResponsibilityEntity>>
    @Insert(onConflict=OnConflictStrategy.IGNORE) suspend fun insert(row:ObjectiveResponsibilityEntity)
    @Query("SELECT * FROM node_responsibilities ORDER BY contextId,personId,role") suspend fun nodes():List<NodeResponsibilityEntity>
    @Query("SELECT * FROM node_responsibilities ORDER BY contextId,personId") fun observeNodes():Flow<List<NodeResponsibilityEntity>>
    @Insert(onConflict=OnConflictStrategy.IGNORE) suspend fun insert(row:NodeResponsibilityEntity)
    @Query("DELETE FROM management_participants WHERE contextId=:context AND personId=:person") suspend fun remove(context:String,person:String)
    @Query("DELETE FROM participant_roles WHERE contextId=:context AND personId=:person AND role=:role") suspend fun removeRole(context:String,person:String,role:String)
    @Query("DELETE FROM objective_responsibilities WHERE contextId=:context AND personId=:person AND objectiveId=:target AND role=:role") suspend fun removeObjective(context:String,person:String,target:String,role:String)
    @Query("DELETE FROM node_responsibilities WHERE contextId=:context AND personId=:person AND nodeId=:target AND role=:role") suspend fun removeNode(context:String,person:String,target:String,role:String)
}

object ParticipationMigration31To32:Migration(31,32) {
    override fun migrate(db:SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE management_participants (contextId TEXT NOT NULL, personId TEXT NOT NULL, PRIMARY KEY(contextId,personId), FOREIGN KEY(contextId) REFERENCES management_contexts(id) ON UPDATE NO ACTION ON DELETE CASCADE, FOREIGN KEY(personId) REFERENCES persons(id) ON UPDATE NO ACTION ON DELETE CASCADE)")
        db.execSQL("CREATE INDEX index_management_participants_personId ON management_participants(personId)")
        db.execSQL("CREATE TABLE participant_roles (contextId TEXT NOT NULL, personId TEXT NOT NULL, role TEXT NOT NULL, PRIMARY KEY(contextId,personId,role), FOREIGN KEY(contextId,personId) REFERENCES management_participants(contextId,personId) ON UPDATE NO ACTION ON DELETE CASCADE)")
        db.execSQL("CREATE INDEX index_participant_roles_contextId_personId ON participant_roles(contextId,personId)")
        db.execSQL("CREATE TABLE objective_responsibilities (contextId TEXT NOT NULL, personId TEXT NOT NULL, objectiveId TEXT NOT NULL, role TEXT NOT NULL, PRIMARY KEY(contextId,personId,objectiveId,role), FOREIGN KEY(contextId,personId) REFERENCES management_participants(contextId,personId) ON UPDATE NO ACTION ON DELETE CASCADE, FOREIGN KEY(objectiveId) REFERENCES management_objectives(id) ON UPDATE NO ACTION ON DELETE CASCADE)")
        db.execSQL("CREATE INDEX index_objective_responsibilities_contextId_personId ON objective_responsibilities(contextId,personId)")
        db.execSQL("CREATE INDEX index_objective_responsibilities_objectiveId ON objective_responsibilities(objectiveId)")
        db.execSQL("CREATE TABLE node_responsibilities (contextId TEXT NOT NULL, personId TEXT NOT NULL, nodeId TEXT NOT NULL, role TEXT NOT NULL, PRIMARY KEY(contextId,personId,nodeId,role), FOREIGN KEY(contextId,personId) REFERENCES management_participants(contextId,personId) ON UPDATE NO ACTION ON DELETE CASCADE, FOREIGN KEY(nodeId) REFERENCES nodes(id) ON UPDATE NO ACTION ON DELETE CASCADE)")
        db.execSQL("CREATE INDEX index_node_responsibilities_contextId_personId ON node_responsibilities(contextId,personId)")
        db.execSQL("CREATE INDEX index_node_responsibilities_nodeId ON node_responsibilities(nodeId)")
        install(db)
    }
    fun install(db:SupportSQLiteDatabase) {
        for(table in listOf("participant_roles","objective_responsibilities","node_responsibilities")) for(event in listOf("INSERT","UPDATE")) {
            val target=when(table) {
                "objective_responsibilities" -> " OR NOT EXISTS(SELECT 1 FROM management_objectives WHERE id=NEW.objectiveId AND contextId=NEW.contextId)"
                "node_responsibilities" -> " OR NOT EXISTS(SELECT 1 FROM nodes n WHERE n.id=NEW.nodeId AND (n.purpose='ACTION' OR EXISTS(SELECT 1 FROM management_roles r WHERE r.nodeId=n.id AND r.contextId=NEW.contextId))) OR COALESCE(${BacklogMigration30To31.scope("NEW.nodeId")},'')!=NEW.contextId"
                else -> ""
            }
            db.execSQL("CREATE TRIGGER IF NOT EXISTS ${table}_valid_${event.lowercase()} BEFORE $event ON $table WHEN NEW.role NOT IN ('PROJECT_LEAD','EXECUTOR','SUPERVISOR','VALIDATOR')$target BEGIN SELECT RAISE(ABORT,'Responsabilidad incompatible'); END")
        }
    }
}
