package com.r0ybt.arachn0de.data.repository

import androidx.room.withTransaction
import com.r0ybt.arachn0de.data.local.*
import com.r0ybt.arachn0de.domain.model.WorkState

internal fun validateCascadeRelations(stories:List<SprintStoryEntity>,objectives:List<ObjectiveWorkEntity>,self:List<LocalIdentityEntity>,tree:List<NodeEntity>,roots:List<ConversionRootEntity>,contexts:List<ManagementContextEntity>,roles:List<ManagementRoleEntity>,goals:List<ManagementObjectiveEntity>,persons:Set<String>) {
    val byId=tree.associateBy {it.id};val owners=contextOwners(tree,roots,contexts);val byRole=roles.associateBy {it.nodeId};val byGoal=goals.associateBy {it.id}
    require(stories.distinct().size==stories.size && stories.map {it.storyId}.distinct().size==stories.size) {"Una historia no puede pertenecer a dos Sprints operativos."}
    stories.forEach {r->require(byId[r.sprintId]?.let {it.sprintMode && it.purpose=="LAYER"}==true && byRole[r.storyId]?.role=="STORY" && owners[r.sprintId]!=null && owners[r.sprintId]==owners[r.storyId]) {"Historia y Sprint deben pertenecer al mismo contexto."}}
    require(objectives.distinct().size==objectives.size)
    objectives.forEach {r->require(byGoal[r.objectiveId]!=null && owners[r.nodeId]==byGoal[r.objectiveId]?.contextId && (byId[r.nodeId]?.purpose=="ACTION" || byRole[r.nodeId]?.role=="STORY")) {"Trabajo de objetivo inválido."}}
    require(self.size<=1 && self.all {it.slot=="SELF" && it.personId in persons}) {"Identidad Yo inválida."}
}
internal fun storyTaskIds(tree:List<NodeEntity>,story:String):Set<String> {
    val children=tree.groupBy {it.parentId};val seen=hashSetOf<String>();val pending=ArrayDeque<String>();pending.add(story)
    while(pending.isNotEmpty()) {val id=pending.removeFirst();require(seen.add(id)) {"Ciclo en la historia."};children[id].orEmpty().forEach {pending.add(it.id)}}
    return tree.filter {it.id in seen && it.purpose=="ACTION"}.mapTo(hashSetOf()) {it.id}
}
/** Historic explicit links remain references even when the task lives outside the story. */
internal fun storyWorkIds(tree:List<NodeEntity>,story:String,links:List<StoryTaskEntity>):Set<String> =
    storyTaskIds(tree,story)+links.filter {it.storyId==story}.flatMap {storyTaskIds(tree,it.taskId)}
internal data class WorkProgress(val done:Int,val total:Int) {
    val complete get()=total>0 && done==total
    val label get()=if(total==0) "Sin trabajo asociado" else "$done / $total"
}
/** Leaf ACTION identities are counted once, even through overlapping story/epic/objective paths. */
internal fun objectiveWorkProgress(goal:String,tree:List<NodeEntity>,links:List<ObjectiveWorkEntity>,legacy:BacklogRecords):WorkProgress {
    val epics=legacy.objectiveEpics.filter {it.objectiveId==goal}.mapTo(hashSetOf()) {it.epicId}
    val stories=legacy.epicStories.filter {it.epicId in epics}.mapTo(hashSetOf()) {it.storyId}
    val roots=links.filter {it.objectiveId==goal}.mapTo(hashSetOf()) {it.nodeId}+stories
    val work=hashSetOf<String>();val byId=tree.associateBy {it.id};val parents=tree.mapTo(hashSetOf()) {it.parentId}
    for(id in roots) {if(byId[id]?.purpose=="ACTION") work.add(id);work.addAll(storyTaskIds(tree,id))}
    legacy.storyTasks.filter {it.storyId in roots}.forEach {work.add(it.taskId);work.addAll(storyTaskIds(tree,it.taskId))}
    val leaves=work.mapNotNull(byId::get).filter {it.purpose=="ACTION" && it.id !in parents}
    return WorkProgress(leaves.count {it.isCompleted},leaves.size)
}
internal class CascadeRelationsRepository(private val db:Arachn0deDatabase) {
    val stories get()=db.cascadeRelationDao().observeStories()
    val objectives get()=db.cascadeRelationDao().observeObjectives()
    val self get()=db.cascadeRelationDao().observeSelf()
    suspend fun validate(tree:List<NodeEntity>? = null) {
        validateCascadeRelations(db.cascadeRelationDao().stories(),db.cascadeRelationDao().objectives(),db.cascadeRelationDao().identities(),tree ?: db.backupDao().nodes(),db.conversionDao().roots(),db.managementContextDao().all(),db.backlogDao().roles(),db.planningDao().objectives(),db.backupDao().persons().mapTo(hashSetOf()) {it.id})
    }
    suspend fun objective(goal:String,node:String,linked:Boolean)=db.withTransaction {
        require(db.planningDao().objective(goal)?.contextId?.let {id->db.managementContextDao().all().any {it.id==id && it.enabled}}==true) {"La gestión está desactivada."}
        if(linked) db.cascadeRelationDao().insert(ObjectiveWorkEntity(goal,node)) else db.cascadeRelationDao().removeObjective(goal,node)
        validate()
    }
    suspend fun addStory(sprint:String,story:String)=db.withTransaction {
        val plan=db.iterationDao().plans().firstOrNull {it.sprintId==sprint}
        require(plan!=null && plan.state!="CLOSED") {"Configura o reabre el Sprint antes de incorporar historias."}
        require(db.managementContextDao().all().any {it.id==plan.contextId && it.enabled})
        require(db.cascadeRelationDao().stories().none {it.storyId==story && it.sprintId!=sprint}) {"La historia pertenece a otro Sprint. Retírala con confirmación antes de cambiarla."}
        db.cascadeRelationDao().insert(SprintStoryEntity(sprint,story));validate()
        syncStories();IterationRepository(db).recordScope()
    }
    /** Do not change the canonical story parent; future descendant tasks join the same Sprint. */
    suspend fun syncStories() {
        val tree=db.backupDao().nodes();val current=db.sprintLinkDao().all().associateBy {it.taskId}
        for(ref in db.cascadeRelationDao().stories()) {
            if(db.iterationDao().plans().any {it.sprintId==ref.sprintId && it.state=="CLOSED"}) continue
            val tasks=storyWorkIds(tree,ref.storyId,db.backlogDao().storyTasks()).filterTo(hashSetOf()) {id->current[id]?.sprintId!=ref.sprintId && tree.firstOrNull {it.id==id}?.parentId!=ref.sprintId}
            if(tasks.isNotEmpty()) SprintLinkRepository(db).link(ref.sprintId,tasks,recordScope=false)
        }
    }
    suspend fun removeStory(sprint:String,story:String,expected:Map<String,String?>)=db.withTransaction {
        require(db.iterationDao().plans().none {it.sprintId==sprint && it.state=="CLOSED"}) {"Reabre el Sprint antes de retirar trabajo."}
        val tree=db.backupDao().nodes();val ids=storyWorkIds(tree,story,db.backlogDao().storyTasks())
        val linked=db.sprintLinkDao().all().filter {it.sprintId==sprint && it.taskId in ids}
        val actual=tree.filter {n->linked.any {it.taskId==n.id}}.associate {it.id to it.workState}
        require(actual==expected) {"Las fases cambiaron. Revisa la retirada de la historia antes de confirmarla."}
        db.cascadeRelationDao().removeStory(sprint,story)
        // A descendant explicitly used by another story remains in the Sprint.
        val remaining=db.cascadeRelationDao().stories().filter {it.sprintId==sprint}.flatMap {storyWorkIds(tree,it.storyId,db.backlogDao().storyTasks())}.toSet()
        for(ref in linked.filter {it.taskId !in remaining}) SprintLinkRepository(db).unlink(sprint,ref.taskId,WorkState.valueOf(requireNotNull(actual[ref.taskId])))
        validate()
    }
}
