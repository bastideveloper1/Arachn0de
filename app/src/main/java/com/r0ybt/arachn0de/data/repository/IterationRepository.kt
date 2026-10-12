package com.r0ybt.arachn0de.data.repository

import androidx.room.withTransaction
import com.r0ybt.arachn0de.data.local.*
import com.r0ybt.arachn0de.domain.model.WorkState
import com.r0ybt.arachn0de.ui.state.sprintProgressPercentage
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.Dispatchers
import java.util.UUID

internal data class IterationRecords(
    val plans:List<IterationPlanEntity> = emptyList(),val cycles:List<IterationCycleEntity> = emptyList(),
    val commitments:List<IterationCommitmentEntity> = emptyList(),val closures:List<IterationClosureEntity> = emptyList()
) {
    fun validate(tree:List<NodeEntity>,roots:List<ConversionRootEntity>,contexts:List<ManagementContextEntity>,links:List<SprintLinkEntity>) {
        fun valid(ok:Boolean) {require(ok) {"La operación invalidaría la planificación o referencias históricas de una iteración. Se conservan los datos; revisa el contexto y las referencias."}}
        val nodes=tree.associateBy {it.id};val owners=contextOwners(tree,roots,contexts);val byPlan=plans.associateBy {it.id};val byCycle=cycles.associateBy {it.id}
        val histories=cycles.groupBy {it.planId};val scopeByCycle=commitments.groupBy {it.cycleId};val closureByCycle=closures.groupBy {it.cycleId}
        val refKeys=commitments.mapTo(hashSetOf()) {it.cycleId to it.taskId};val contextIds=contexts.mapTo(hashSetOf()) {it.id}
        val direct=tree.filter {it.purpose=="ACTION"}.groupBy {it.parentId};val external=links.groupBy {it.sprintId}
        valid(byPlan.size==plans.size && plans.map {it.sprintId}.distinct().size==plans.size && byCycle.size==cycles.size && commitments.map {it.cycleId to it.taskId}.distinct().size==commitments.size && closures.map {it.cycleId to it.taskId}.distinct().size==closures.size)
        plans.forEach {p->
            valid(p.id.isNotBlank() && p.id.length<=200 && p.state in listOf("PREPARED","ACTIVE","CLOSED") && nodes[p.sprintId]?.let {it.purpose=="LAYER" && it.sprintMode}==true && owners[p.sprintId]==p.contextId && p.contextId in contextIds)
            valid((p.startAt==null || p.startAt>=0) && (p.endAt==null || p.endAt>=0) && (p.startAt==null || p.endAt==null || p.startAt<=p.endAt))
            val history=histories[p.id].orEmpty().sortedBy {it.number}
            valid(history.map {it.number}==(1..history.size).toList())
            valid(if(p.state=="PREPARED") history.isEmpty() else history.isNotEmpty() && history.dropLast(1).all {it.closedAt!=null} && (history.last().closedAt==null)==(p.state=="ACTIVE"))
            history.zipWithNext().forEach {(a,b)->valid(a.closedAt!=null && a.closedAt<=b.startedAt)}
            (direct[p.sprintId].orEmpty()+external[p.sprintId].orEmpty().mapNotNull {nodes[it.taskId]}).forEach {valid(owners[it.id]==p.contextId)}
        }
        cycles.forEach {valid(it.id.isNotBlank() && it.id.length<=200 && it.planId in byPlan && it.number>0 && it.startedAt>=0 && (it.closedAt==null || it.closedAt>=it.startedAt))}
        commitments.forEach {ref->val cycle=byCycle[ref.cycleId];val p=byPlan[cycle?.planId];valid(cycle!=null && p!=null && nodes[ref.taskId]?.purpose=="ACTION" && owners[ref.taskId]==p?.contextId && ref.addedAt>=requireNotNull(cycle).startedAt && (cycle.closedAt==null || ref.addedAt<=cycle.closedAt) && (!ref.initial || ref.addedAt==cycle.startedAt))}
        closures.forEach {row->valid(byCycle[row.cycleId]?.closedAt!=null && row.cycleId to row.taskId in refKeys && (row.phaseAtClose==null || row.phaseAtClose in WorkState.entries.map {it.name}) && (!row.inScope || row.phaseAtClose!=null))}
        cycles.forEach {cycle->val refs=scopeByCycle[cycle.id].orEmpty().mapTo(hashSetOf()) {it.taskId};val closed=closureByCycle[cycle.id].orEmpty().mapTo(hashSetOf()) {it.taskId};valid(if(cycle.closedAt==null) closed.isEmpty() else closed==refs)}
    }
}
internal fun members(tree:List<NodeEntity>,links:List<SprintLinkEntity>,sprint:String):List<NodeEntity> {
    val external=links.filter {it.sprintId==sprint}.mapTo(hashSetOf()) {it.taskId}
    return tree.filter {it.purpose=="ACTION" && (it.parentId==sprint || it.id in external)}.distinctBy {it.id}
}
internal data class IterationState(val records:IterationRecords=IterationRecords(),val work:BacklogState=BacklogState(),val people:ParticipationState=ParticipationState(),val links:List<SprintLinkEntity> = emptyList()) {
    fun current(sprint:String)=members(work.nodes,links,sprint)
    fun progress(sprint:String):Double? {val parents=work.nodes.mapTo(hashSetOf()) {it.parentId};return sprintProgressPercentage(work.nodes.map {it.toNode(it.id in parents)},sprint,links.filter {it.sprintId==sprint}.mapTo(hashSetOf()) {it.taskId})}
}
internal class IterationRepository(private val db:Arachn0deDatabase,private val now:()->Long=System::currentTimeMillis) {
    private val dao get()=db.iterationDao()
    suspend fun records()=IterationRecords(dao.plans(),dao.cycles(),dao.commitments(),dao.closures())
    val assignments get()=db.personDao().observeAllAssignments()
    val relations=CascadeRelationsRepository(db)
    fun observe(context:String)=combine(combine(dao.observePlans(),dao.observeCycles(),dao.observeCommitments(),dao.observeClosures(),::IterationRecords),BacklogRepository(db).observe(context),ParticipationRepository(db).observe(context),db.sprintLinkDao().observe(),::IterationState).flowOn(Dispatchers.Default)
    suspend fun validate(tree:List<NodeEntity>?=null) {records().validate(tree ?: db.backupDao().nodes(),db.conversionDao().roots(),db.managementContextDao().all(),db.sprintLinkDao().all())}
    private suspend fun active(context:String) {require(db.managementContextDao().all().any {it.id==context && it.enabled}) {"La gestión está desactivada."}}
    private suspend fun plan(id:String)=requireNotNull(dao.plans().firstOrNull {it.id==id}) {"La iteración ya no existe."}.also {active(it.contextId)}
    suspend fun configure(context:String,sprint:String,goal:String="",startAt:Long?=null,endAt:Long?=null,notes:String=""):IterationPlanEntity=db.withTransaction {
        active(context);require((startAt==null || startAt>=0) && (endAt==null || endAt>=0) && (startAt==null || endAt==null || startAt<=endAt)) {"La fecha de término debe ser igual o posterior al inicio y ambas fechas válidas."};val existing=dao.plans().firstOrNull {it.sprintId==sprint}
        require(existing==null || existing.contextId==context) {"El Sprint ya tiene planificación en otro contexto."}
        val row=IterationPlanEntity(existing?.id ?: UUID.randomUUID().toString(),context,sprint,goal,startAt,endAt,notes,existing?.state ?: "PREPARED")
        dao.save(row);validate();row
    }
    suspend fun start(id:String,reopenConfirmed:Boolean=false)=db.withTransaction {
        val p=plan(id);require(p.state=="PREPARED" || (p.state=="CLOSED" && reopenConfirmed)) {"Para reabrir, confirma un nuevo ciclo. El historial anterior se conservará."}
        val history=dao.cycles().filter {it.planId==id};val time=maxOf(now(),history.lastOrNull()?.closedAt ?: 0)
        val cycle=IterationCycleEntity(UUID.randomUUID().toString(),id,history.size+1,time,null);dao.insert(cycle)
        dao.save(p.copy(state="ACTIVE"));CascadeRelationsRepository(db).syncStories()
        members(db.backupDao().nodes(),db.sprintLinkDao().all(),p.sprintId).forEach {dao.insert(IterationCommitmentEntity(cycle.id,it.id,true,time))}
        dao.save(p.copy(state="ACTIVE"));validate()
    }
    /** Append-only scope references, never another source of current membership or work state. */
    suspend fun recordScope() {
        CascadeRelationsRepository(db).syncStories()
        val plans=dao.plans().filter {it.state=="ACTIVE"};if(plans.isEmpty()) return
        val cycles=dao.cycles();val refs=dao.commitments().mapTo(hashSetOf()) {it.cycleId to it.taskId};val tree=db.backupDao().nodes();val links=db.sprintLinkDao().all()
        for(p in plans) {
            val cycle=requireNotNull(cycles.lastOrNull {it.planId==p.id && it.closedAt==null})
            members(tree,links,p.sprintId).filter {cycle.id to it.id !in refs}.forEach {dao.insert(IterationCommitmentEntity(cycle.id,it.id,false,maxOf(now(),cycle.startedAt)))}
        }
    }
    suspend fun add(id:String,tasks:Set<String>)=db.withTransaction {
        val p=plan(id);require(p.state!="CLOSED") {"Reabre la iteración antes de cambiar su selección."}
        val tree=db.backupDao().nodes();val owners=contextOwners(tree,db.conversionDao().roots(),db.managementContextDao().all());val available=tree.filter {it.purpose=="ACTION" && owners[it.id]==p.contextId}.mapTo(hashSetOf()) {it.id}
        require(tasks.all {it in available}) {"Selecciona tareas de este contexto."}
        SprintLinkRepository(db).link(p.sprintId,tasks);recordScope();validate()
    }
    suspend fun remove(id:String,task:String,discardPhase:WorkState)=db.withTransaction {
        val p=plan(id);require(p.state!="CLOSED") {"Reabre la iteración antes de cambiar su selección."}
        require(db.nodeDao().getById(task)?.parentId!=p.sprintId) {"Es una tarea directa del Sprint. Retirarla requiere moverla; la planificación conserva su ubicación."}
        SprintLinkRepository(db).unlink(p.sprintId,task,discardPhase);validate()
    }
    suspend fun close(id:String,expected:List<NodeEntity>)=db.withTransaction {
        val p=plan(id);require(p.state=="ACTIVE") {"Solo una iteración activa puede cerrarse."}
        val tree=db.backupDao().nodes();val current=members(tree,db.sprintLinkDao().all(),p.sprintId)
        require(current.sortedBy {it.id}==expected.sortedBy {it.id}) {"El trabajo cambió. Revisa de nuevo el resumen antes de cerrar."}
        recordScope();val cycle=requireNotNull(dao.cycles().lastOrNull {it.planId==id && it.closedAt==null});val time=maxOf(now(),cycle.startedAt,dao.commitments().filter {it.cycleId==cycle.id}.maxOfOrNull {it.addedAt} ?: 0)
        check(dao.close(cycle.id,time)==1)
        val byNode=tree.associateBy {it.id};val ids=current.mapTo(hashSetOf()) {it.id}
        dao.commitments().filter {it.cycleId==cycle.id}.forEach {ref->val node=requireNotNull(byNode[ref.taskId]);dao.insert(IterationClosureEntity(cycle.id,node.id,node.id in ids,if(node.id in ids) node.workState else null,node.isCompleted))}
        dao.save(p.copy(state="CLOSED"));validate()
    }
    suspend fun rejectDeletion(ids:Set<String>) {val r=records();require(r.plans.none {it.sprintId in ids} && r.commitments.none {it.taskId in ids}) {"La eliminación perdería planificación o referencias históricas de una iteración. No se eliminaron datos."}}
    suspend fun rejectDisable(sprint:String) {require(dao.plans().none {it.sprintId==sprint}) {"Este Sprint tiene planificación e historial. Su modo Sprint debe conservarse."}}
    suspend fun restore(r:IterationRecords) {r.plans.forEach {dao.save(it)};r.cycles.forEach {dao.insert(it)};r.commitments.forEach {dao.insert(it)};r.closures.forEach {dao.insert(it)}}
}
