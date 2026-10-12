package com.r0ybt.arachn0de.data.repository

import androidx.room.withTransaction
import com.r0ybt.arachn0de.data.local.*
import kotlinx.coroutines.flow.combine
import java.util.UUID

internal enum class ObjectiveAcceptance(val label:String) { NO_CRITERIA("Sin criterios definidos"), PENDING("Con criterios pendientes"), ACCEPTED("Todos los criterios validados") }
internal fun objectiveAcceptance(criteria:List<AcceptanceCriterionEntity>)=when {
    criteria.isEmpty()->ObjectiveAcceptance.NO_CRITERIA
    criteria.all {it.validated}->ObjectiveAcceptance.ACCEPTED
    else->ObjectiveAcceptance.PENDING
}
internal data class PlanningState(val charter:ProjectCharterEntity?=null,val objectives:List<ManagementObjectiveEntity> = emptyList(),val criteria:List<AcceptanceCriterionEntity> = emptyList())

/** Reads and writes only the current session's database. No task/Sprint operations. */
internal class PlanningRepository(private val db:Arachn0deDatabase,private val now:()->Long=System::currentTimeMillis) {
    val participation=ParticipationRepository(db)
    private val dao get()=db.planningDao()
    fun observe(id:String)=combine(dao.observeCharter(id),dao.observeObjectives(id),dao.observeCriteria(id),db.nodeDao().observeAllNodes(),db.managementLayerDao().observe()) {charter,objectives,criteria,nodes,layers->
        val byId=nodes.associateBy {it.id};val fields=layers.filter {it.contextId==id}.associate {it.kind to byId[it.nodeId]?.description.orEmpty()}
        val effective=if("PURPOSE" in fields) ProjectCharterEntity(id,fields["PURPOSE"].orEmpty(),fields["SCOPE"].orEmpty(),fields["EXCLUSIONS"].orEmpty(),fields["DELIVERABLES"].orEmpty(),fields["SUCCESS"].orEmpty()) else charter
        PlanningState(effective,objectives.map {o->byId[o.id]?.takeIf {layers.any {it.nodeId==o.id && it.kind=="OBJECTIVE:"+o.id}}?.let {o.copy(title=it.title,description=it.description)} ?: o},criteria)
    }
    private suspend fun active(id:String) {require(db.managementContextDao().all().any {it.id==id && it.enabled}) {"La gestión está desactivada o el contexto ya no existe."}}
    suspend fun saveCharter(row:ProjectCharterEntity)=db.withTransaction {active(row.contextId);dao.save(row);syncCharter(row)}
    private suspend fun syncCharter(row:ProjectCharterEntity) {
        val values=mapOf("PURPOSE" to row.purpose,"SCOPE" to row.scope,"EXCLUSIONS" to row.exclusions,"DELIVERABLES" to row.deliverables,"SUCCESS" to row.successConditions)
        db.managementLayerDao().all().filter {it.contextId==row.contextId && it.kind in values}.forEach {mapping->
            val node=requireNotNull(db.nodeDao().getById(mapping.nodeId));db.nodeDao().updateContent(node.id,node.title,values.getValue(mapping.kind),now())
        }
    }
    suspend fun createObjective(contextId:String,title:String,description:String=""):ManagementObjectiveEntity=db.withTransaction {
        active(contextId);require(title.isNotBlank()) {"Escribe un título."}
        val rows=dao.objectives().filter {it.contextId==contextId};require(rows.size<Int.MAX_VALUE)
        rows.forEachIndexed {index,row->if(row.position!=index) dao.save(row.copy(position=index))}
        ManagementObjectiveEntity(UUID.randomUUID().toString(),contextId,title.trim(),description,rows.size).also {dao.save(it);ManagementCascadeRepository(db).ensure(contextId)}
    }
    suspend fun editObjective(id:String,title:String,description:String)=db.withTransaction {
        val row=requireNotNull(dao.objective(id)) {"El objetivo ya no existe."};active(row.contextId)
        require(title.isNotBlank()) {"Escribe un título."};dao.save(row.copy(title=title.trim(),description=description));db.managementLayerDao().all().firstOrNull {it.kind=="OBJECTIVE:"+id}?.let {db.nodeDao().updateContent(it.nodeId,title.trim(),description,now())}
    }
    suspend fun createCriterion(objectiveId:String,description:String):AcceptanceCriterionEntity=db.withTransaction {
        val objective=requireNotNull(dao.objective(objectiveId)) {"El objetivo ya no existe."};active(objective.contextId)
        require(description.isNotBlank()) {"Escribe un criterio verificable."}
        val rows=dao.criteria().filter {it.objectiveId==objectiveId};require(rows.size<Int.MAX_VALUE)
        rows.forEachIndexed {index,row->if(row.position!=index) dao.save(row.copy(position=index))}
        AcceptanceCriterionEntity(UUID.randomUUID().toString(),objectiveId,description.trim(),rows.size).also {dao.save(it)}
    }
    suspend fun editCriterion(id:String,description:String)=db.withTransaction {
        val row=requireNotNull(dao.criterion(id)) {"El criterio ya no existe."};active(requireNotNull(dao.objective(row.objectiveId)).contextId)
        require(description.isNotBlank()) {"Escribe un criterio verificable."};dao.save(row.copy(description=description.trim()))
    }
    suspend fun validateCriterion(id:String,validated:Boolean)=db.withTransaction {
        val row=requireNotNull(dao.criterion(id)) {"El criterio ya no existe."};active(requireNotNull(dao.objective(row.objectiveId)).contextId)
        if(row.validated!=validated) dao.save(row.copy(validated=validated,validatedAt=if(validated) now().also {require(it>=0)} else null))
    }
    suspend fun reorderObjectives(contextId:String,ids:List<String>)=db.withTransaction {
        active(contextId);val rows=dao.objectives().filter {it.contextId==contextId}.associateBy {it.id}
        require(ids.size==rows.size && ids.toSet()==rows.keys) {"Los objetivos cambiaron. Revisa el orden."}
        ids.forEachIndexed {index,id->dao.save(rows.getValue(id).copy(position=index))}
    }
    suspend fun reorderCriteria(objectiveId:String,ids:List<String>)=db.withTransaction {
        active(requireNotNull(dao.objective(objectiveId)).contextId);val rows=dao.criteria().filter {it.objectiveId==objectiveId}.associateBy {it.id}
        require(ids.size==rows.size && ids.toSet()==rows.keys) {"Los criterios cambiaron. Revisa el orden."}
        ids.forEachIndexed {index,id->dao.save(rows.getValue(id).copy(position=index))}
    }
    /** UI must acknowledge deletion; cascade removes only this objective's criteria. */
    suspend fun deleteObjective(id:String)=db.withTransaction {val row=requireNotNull(dao.objective(id));active(row.contextId);require(db.nodeDao().getById(id)?.let {db.nodeDao().hasChildren(it.projectId,id)}!=true) {"El objetivo contiene descendientes; consérvalos o muévelos antes de eliminarlo."};dao.deleteObjective(id);db.nodeDao().delete(id)}
    suspend fun deleteCriterion(id:String)=db.withTransaction {val row=requireNotNull(dao.criterion(id));active(requireNotNull(dao.objective(row.objectiveId)).contextId);dao.deleteCriterion(id)}
}
