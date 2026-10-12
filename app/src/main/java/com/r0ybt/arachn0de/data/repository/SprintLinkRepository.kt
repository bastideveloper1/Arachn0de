package com.r0ybt.arachn0de.data.repository

import androidx.room.withTransaction
import com.r0ybt.arachn0de.data.local.*
import com.r0ybt.arachn0de.domain.model.WorkState

internal class SprintLinkConflictException(message:String):IllegalArgumentException(message)
internal inline fun requireSprintLink(condition:Boolean,message:()->String={"Vínculo Sprint incompatible."}) {if(!condition) throw SprintLinkConflictException(message())}

internal class SprintLinkRepository(private val db:Arachn0deDatabase) {
    val links get()=db.sprintLinkDao().observe()
    suspend fun link(sprintId:String,taskIds:Set<String>,recordScope:Boolean=true)=db.withTransaction {
        val sprint=requireNotNull(db.nodeDao().getById(sprintId)) {"El Sprint ya no existe."}
        requireSprintLink(sprint.purpose=="LAYER" && sprint.sprintMode) {"La capa no tiene modo Sprint activo."}
        for(id in taskIds) {
            val task=requireNotNull(db.nodeDao().getById(id)) {"La tarea ya no existe."}
            requireSprintLink(task.id!=sprint.id && task.purpose=="ACTION" && task.projectId==sprint.projectId) {"Selecciona tareas del mismo proyecto."}
            val parent=task.parentId?.let {db.nodeDao().getById(it)}
            requireSprintLink(parent?.sprintMode!=true || parent.id==sprintId) {"La tarea ya pertenece directamente a otro Sprint."}
            val previous=db.sprintLinkDao().task(id)
            requireSprintLink(previous==null || previous.sprintId==sprintId) {"La tarea ya está vinculada a otro Sprint. Desvincúlala primero."}
            if(parent?.id==sprintId || previous!=null) continue
            db.sprintLinkDao().insert(SprintLinkEntity(id,sprintId))
            if(task.workState==null) check(db.nodeDao().setWorkState(id,WorkState.fromCompletion(task.isCompleted).name,task.isCompleted,System.currentTimeMillis())==1)
        }
        IterationRepository(db).validate();if(recordScope) IterationRepository(db).recordScope()
    }
    /** Explicit acknowledgement of the exact phase: a concurrent phase change requires reconfirmation. */
    suspend fun unlink(sprintId:String,taskId:String,discardPhase:WorkState)=db.withTransaction {
        val row=requireNotNull(db.sprintLinkDao().task(taskId)) {"La tarea ya no está vinculada."}
        requireSprintLink(row.sprintId==sprintId)
        val task=requireNotNull(db.nodeDao().getById(taskId))
        requireSprintLink(task.workState==discardPhase.name) {"La fase cambió. Revisa y confirma de nuevo la desvinculación."}
        db.sprintLinkDao().delete(taskId)
        check(db.nodeDao().setWorkState(taskId,null,task.isCompleted,System.currentTimeMillis())==1)
    }
    /** Preflight on the complete proposed tree; traditional conversions retain their guard. */
    suspend fun prepareConversion(ids:Set<String>,tree:List<NodeEntity>,roots:List<ConversionRootEntity>):Set<String> {
        val affected=db.sprintLinkDao().all().filter {it.taskId in ids || it.sprintId in ids}
        if(affected.isEmpty()) return emptySet()
        val original=db.backupDao().nodes();val contexts=db.managementContextDao().all()
        val before=contextOwners(original,db.conversionDao().roots(),contexts)
        val after=contextOwners(tree,roots,contexts);val nodes=tree.associateBy {it.id}
        for(link in affected) {
            val context=before[link.sprintId]
            requireSprintLink(link.taskId in ids && link.sprintId in ids && context!=null && before[link.taskId]==context && after[link.taskId]==context && after[link.sprintId]==context) {"La conversión separaría vínculos Sprint o cambiaría su contexto gestionado. No se modificaron datos."}
            val sprint=nodes[link.sprintId];val task=nodes[link.taskId]
            requireSprintLink(sprint?.sprintMode==true && sprint.purpose=="LAYER" && task?.purpose=="ACTION" && task.projectId==sprint.projectId && nodes[task.parentId]?.sprintMode!=true) {"La conversión invalidaría el Sprint o sus tareas vinculadas."}
        }
        val protected=affected.mapTo(hashSetOf()) {it.taskId}
        val sprints=affected.mapTo(hashSetOf()) {it.sprintId}
        original.filter {it.parentId in sprints && it.purpose=="ACTION"}.forEach {protected.add(it.id)}
        return protected
    }
    suspend fun rejectConversion(ids:Set<String>) {
        requireSprintLink(db.sprintLinkDao().all().none {it.taskId in ids || it.sprintId in ids}) {"Desvincula las tareas externas antes de convertir esta jerarquía; las tareas se conservarán."}
    }
    suspend fun rejectDeletion(ids:Set<String>) {
        requireSprintLink(db.sprintLinkDao().all().none {it.sprintId in ids && it.taskId !in ids}) {"Desvincula las tareas externas antes de eliminar esta capa; sus fases requieren confirmación."}
        // Both endpoints are intentionally being deleted: remove joins before deleting/detaching nodes.
        db.sprintLinkDao().all().filter {it.taskId in ids}.forEach {db.sprintLinkDao().delete(it.taskId)}
    }
}
