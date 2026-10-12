package com.r0ybt.arachn0de.data.repository

import androidx.room.withTransaction
import com.r0ybt.arachn0de.data.local.*
import com.r0ybt.arachn0de.domain.model.WorkState

internal enum class KanbanScope(val label:String) { PROJECT("Proyecto"), BACKLOG("Backlog"), SPRINT("Iteración / Sprint") }

/** A projection of current task identities, not a board or a second state store. */
internal fun kanbanTasks(state:IterationState,context:String,scope:KanbanScope,sprint:String?=null,query:String=""):List<NodeEntity> {
    if(state.work.contexts.none {it.id==context && it.enabled}) return emptyList()
    val eligible=state.work.nodes.filter {it.purpose=="ACTION" && state.work.owners[it.id]==context}
    val selected=when(scope) {
        KanbanScope.PROJECT -> eligible.mapTo(hashSetOf()) {it.id}
        KanbanScope.BACKLOG -> state.work.records.entries.filter {it.contextId==context}.mapTo(hashSetOf()) {it.nodeId}
        KanbanScope.SPRINT -> if(state.work.nodes.any {it.id==sprint && it.sprintMode && state.work.owners[it.id]==context}) state.current(requireNotNull(sprint)).mapTo(hashSetOf()) {it.id} else emptySet()
    }
    val byId=state.work.nodes.associateBy {it.id};val search=query.trim()
    return eligible.distinctBy {it.id}.filter {it.id in selected && (search.isEmpty() || it.title.contains(search,true) || byId[it.parentId]?.title?.contains(search,true)==true)}
}

/** Validates the managed boundary in the same transaction as the official operation. */
internal class KanbanRepository(private val db:Arachn0deDatabase) {
    val assignments get()=db.personDao().observeAllAssignments()
    val relations=CascadeRelationsRepository(db)
    fun observe(context:String)=IterationRepository(db).observe(context)
    private suspend fun task(context:String,id:String):NodeEntity {
        val contexts=db.managementContextDao().all()
        require(contexts.any {it.id==context && it.enabled}) {"La gestión está desactivada o el contexto ya no existe."}
        val tree=db.backupDao().nodes()
        require(contextOwners(tree,db.conversionDao().roots(),contexts)[id]==context) {"La tarea ya no pertenece a este contexto. Actualiza la vista."}
        return requireNotNull(tree.firstOrNull {it.id==id && it.purpose=="ACTION"}) {"La tarea ya no está disponible."}
    }
    suspend fun phase(context:String,id:String,state:WorkState)=db.withTransaction {
        task(context,id)
        require(NodeRepository(db).setWorkState(id,state)) {"Esta tarea no pertenece a un Sprint. Vincúlala desde la planificación para asignarle una fase."}
    }
    suspend fun confirm(context:String,id:String)=db.withTransaction {
        val row=task(context,id);val nodes=NodeRepository(db)
        check(if(row.workState!=null) nodes.confirmWorkState(id) else nodes.setCompleted(id,!row.isCompleted)) {"No se pudo confirmar la tarea. Actualiza la vista."}
    }
}
