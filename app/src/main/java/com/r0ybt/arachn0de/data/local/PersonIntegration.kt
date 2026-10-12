package com.r0ybt.arachn0de.data.local

import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName="person_technologies",primaryKeys=["personId","technologyId"],foreignKeys=[
    ForeignKey(entity=PersonEntity::class,parentColumns=["id"],childColumns=["personId"],onDelete=ForeignKey.CASCADE),
    ForeignKey(entity=TechnologyEntity::class,parentColumns=["id"],childColumns=["technologyId"],onDelete=ForeignKey.CASCADE)
],indices=[Index("technologyId")])
data class PersonTechnologyEntity(val personId:String,val technologyId:String,val proficiency:String)
internal enum class PersonProficiency(val label:String) {UNEVALUATED("Sin evaluar"),BASIC("Básico"),INTERMEDIATE("Intermedio"),ADVANCED("Avanzado"),EXPERT("Experto")}
internal fun validatePersonTechnologies(people:Set<String>,technologies:Set<String>,rows:List<PersonTechnologyEntity>) {
    require(rows.map {it.personId to it.technologyId}.distinct().size==rows.size) {"Tecnología de Persona duplicada."}
    require(rows.all {it.personId in people && it.technologyId in technologies && it.proficiency in PersonProficiency.entries.map {p->p.name}}) {"La asociación de Tecnología no es válida."}
}
internal data class PersonWorkRow(val projectId:String,val projectName:String,val targetId:String,val title:String,val kind:String,val nodeId:String?,val contextId:String?,val role:String,val completed:Boolean?,val phase:String?,val contextEnabled:Boolean?,val scopeName:String?)
@Dao internal interface PersonIntegrationDao {
    @Query("SELECT * FROM person_technologies ORDER BY personId,technologyId") fun observeTechnologies():Flow<List<PersonTechnologyEntity>>
    @Query("SELECT * FROM person_technologies ORDER BY personId,technologyId") suspend fun technologies():List<PersonTechnologyEntity>
    @Upsert suspend fun save(row:PersonTechnologyEntity)
    @Query("DELETE FROM person_technologies WHERE personId=:person AND technologyId=:technology") suspend fun remove(person:String,technology:String)
    @Query("""
        SELECT p.id projectId,p.name projectName,n.id targetId,n.title title,
            CASE WHEN mr.role IS NOT NULL THEN mr.role WHEN n.sprintMode=1 THEN 'SPRINT' ELSE n.purpose END kind,
            n.id nodeId,NULL contextId,'TRADITIONAL' role,
            CASE WHEN n.purpose='ACTION' THEN n.isCompleted ELSE NULL END completed,n.workState phase,NULL contextEnabled,NULL scopeName
        FROM node_person np JOIN nodes n ON n.id=np.nodeId JOIN projects p ON p.id=n.projectId LEFT JOIN management_roles mr ON mr.nodeId=n.id WHERE np.personId=:person
        UNION ALL
        SELECT p.id,p.name,p.id,p.name,'PROJECT',NULL,mp.contextId,COALESCE(pr.role,'MEMBER'),NULL,NULL,mc.enabled,COALESCE(root.title,p.name)
        FROM management_participants mp JOIN conversion_roots cr ON cr.id=mp.contextId JOIN management_contexts mc ON mc.id=mp.contextId
        LEFT JOIN nodes root ON root.id=cr.nodeId JOIN projects p ON p.id=COALESCE(cr.projectId,root.projectId)
        LEFT JOIN participant_roles pr ON pr.contextId=mp.contextId AND pr.personId=mp.personId WHERE mp.personId=:person
        UNION ALL
        SELECT p.id,p.name,n.id,n.title,CASE WHEN mr.role IS NOT NULL THEN mr.role WHEN n.sprintMode=1 THEN 'SPRINT' ELSE n.purpose END,n.id,nr.contextId,nr.role,
            CASE WHEN n.purpose='ACTION' THEN n.isCompleted ELSE NULL END,n.workState,mc.enabled,(SELECT COALESCE(r.title,p.name) FROM conversion_roots c LEFT JOIN nodes r ON r.id=c.nodeId WHERE c.id=nr.contextId)
        FROM node_responsibilities nr JOIN nodes n ON n.id=nr.nodeId JOIN projects p ON p.id=n.projectId JOIN management_contexts mc ON mc.id=nr.contextId LEFT JOIN management_roles mr ON mr.nodeId=n.id WHERE nr.personId=:person
        UNION ALL
        SELECT p.id,p.name,o.id,o.title,'OBJECTIVE',NULL,o.contextId,orr.role,NULL,NULL,mc.enabled,COALESCE(root.title,p.name)
        FROM objective_responsibilities orr JOIN management_objectives o ON o.id=orr.objectiveId JOIN conversion_roots cr ON cr.id=o.contextId JOIN management_contexts mc ON mc.id=o.contextId
        LEFT JOIN nodes root ON root.id=cr.nodeId JOIN projects p ON p.id=COALESCE(cr.projectId,root.projectId) WHERE orr.personId=:person
        ORDER BY projectName,projectId,kind,title,targetId,role
    """) fun observeWork(person:String):Flow<List<PersonWorkRow>>
}
internal object PersonIntegrationMigration34To35:Migration(34,35) {
    override fun migrate(db:SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE person_technologies (personId TEXT NOT NULL, technologyId TEXT NOT NULL, proficiency TEXT NOT NULL, PRIMARY KEY(personId,technologyId), FOREIGN KEY(personId) REFERENCES persons(id) ON DELETE CASCADE, FOREIGN KEY(technologyId) REFERENCES technologies(id) ON DELETE CASCADE)")
        db.execSQL("CREATE INDEX index_person_technologies_technologyId ON person_technologies(technologyId)")
    }
}
